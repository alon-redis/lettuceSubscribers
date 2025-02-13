package com.example;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MultiSubscriber {
    private final String redisHost;
    private final int redisPort;
    private final int connectionCount;
    private final int totalChannels;
    private static final String CHANNEL_PREFIX = "channel-";
    private static final String LATENCY_CHANNEL = "channel-0";
    private static final AtomicLong messageCounter = new AtomicLong(0);
    
    // Sophisticated metrics collection using exponential decay
    private static final double ALPHA = 0.1; // Exponential moving average factor
    private final AtomicReference<Double> avgLatency = new AtomicReference<>(0.0);
    private final AtomicReference<Double> maxLatency = new AtomicReference<>(0.0);
    private final ConcurrentSkipListSet<Double> latencyPercentiles = new ConcurrentSkipListSet<>();
    
    private final ConcurrentMap<StatefulRedisPubSubConnection<String, String>, Set<String>> connectionSubscriptions;
    private volatile boolean isRunning = true;
    
    public MultiSubscriber(String redisHost, int redisPort, int connectionCount, int totalChannels) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.connectionCount = connectionCount;
        this.totalChannels = totalChannels;
        this.connectionSubscriptions = new ConcurrentHashMap<>();
    }

    private class LatencyPublisher implements Runnable {
        private final RedisClient client;
        private final RedisCommands<String, String> syncCommands;

        public LatencyPublisher(String host, int port) {
            this.client = RedisClient.create(String.format("redis://%s:%d", host, port));
            this.syncCommands = client.connect().sync();
        }

        @Override
        public void run() {
            try {
                while (isRunning) {
                    String timestamp = String.valueOf(System.nanoTime());
                    syncCommands.publish(LATENCY_CHANNEL, timestamp);
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                client.shutdown();
            }
        }
    }

    private void updateLatencyMetrics(double latency) {
        // Update moving average with exponential decay
        avgLatency.updateAndGet(current -> current * (1 - ALPHA) + latency * ALPHA);
        
        // Update maximum latency with atomic operation
        maxLatency.updateAndGet(current -> Math.max(current, latency));
        
        // Store for percentile calculation (limited size)
        latencyPercentiles.add(latency);
        if (latencyPercentiles.size() > 1000) {
            latencyPercentiles.pollFirst(); // Remove oldest
        }
    }

    private void printLatencyMetrics() {
        double avg = avgLatency.get();
        double max = maxLatency.get();
        
        // Calculate 95th percentile
        List<Double> sortedLatencies = new ArrayList<>(latencyPercentiles);
        double p95 = 0.0;
        if (!sortedLatencies.isEmpty()) {
            Collections.sort(sortedLatencies);
            int index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
            p95 = sortedLatencies.get(Math.max(0, index));
        }
        
        System.out.printf("Latency Metrics (ms) - Avg: %.3f, Max: %.3f, 95th: %.3f%n",
            avg / 1_000_000.0, max / 1_000_000.0, p95 / 1_000_000.0);
    }

    public void start() {
        List<RedisClient> clients = new ArrayList<>();
        List<StatefulRedisPubSubConnection<String, String>> connections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3); // Added thread for publisher
        Random random = new Random();

        try {
            // Initialize latency publisher
            LatencyPublisher publisher = new LatencyPublisher(redisHost, redisPort);
            scheduler.execute(publisher);

            // Initialize channels
            String[] allChannels = new String[totalChannels];
            for (int i = 0; i < totalChannels; i++) {
                allChannels[i] = CHANNEL_PREFIX + i;
            }

            // Initialize connections with enhanced message handling
            for (int i = 0; i < connectionCount; i++) {
                RedisClient client = RedisClient.create(String.format("redis://%s:%d", redisHost, redisPort));
                client.setOptions(io.lettuce.core.ClientOptions.builder()
                    .protocolVersion(io.lettuce.core.protocol.ProtocolVersion.RESP2)
                    .build());
                clients.add(client);
                
                StatefulRedisPubSubConnection<String, String> connection = client.connectPubSub();
                connections.add(connection);
                
                connectionSubscriptions.put(connection, ConcurrentHashMap.newKeySet());
                
                connection.addListener(new RedisPubSubAdapter<String, String>() {
                    @Override
                    public void message(String channel, String message) {
                        messageCounter.incrementAndGet();
                        
                        if (channel.equals(LATENCY_CHANNEL)) {
                            try {
                                long sentTimestamp = Long.parseLong(message);
                                long receivedTimestamp = System.nanoTime();
                                double latency = receivedTimestamp - sentTimestamp;
                                updateLatencyMetrics(latency);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid timestamp format in message: " + message);
                            }
                        }
                    }
                });

                RedisPubSubCommands<String, String> sync = connection.sync();
                sync.subscribe(allChannels);
                connectionSubscriptions.get(connection).addAll(Arrays.asList(allChannels));
            }

            // Enhanced metrics reporting
            scheduler.scheduleAtFixedRate(() -> {
                long count = messageCounter.getAndSet(0);
                System.out.println("Messages received in last second: " + count);
                printLatencyMetrics();
            }, 1, 1, TimeUnit.SECONDS);

            // Subscription rotation logic
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    for (StatefulRedisPubSubConnection<String, String> connection : connections) {
                        Set<String> currentSubscriptions = connectionSubscriptions.get(connection);
                        List<String> subscribedChannels = new ArrayList<>(currentSubscriptions);
                        List<String> channelsToModify = new ArrayList<>();
                        
                        int numChannelsToModify = Math.min(5, subscribedChannels.size());
                        for (int i = 0; i < numChannelsToModify; i++) {
                            int randomIndex = random.nextInt(subscribedChannels.size());
                            String channel = subscribedChannels.remove(randomIndex);
                            if (!channel.equals(LATENCY_CHANNEL)) { // Preserve latency channel subscription
                                channelsToModify.add(channel);
                            }
                        }

                        if (!channelsToModify.isEmpty()) {
                            RedisPubSubCommands<String, String> sync = connection.sync();
                            sync.unsubscribe(channelsToModify.toArray(new String[0]));
                            currentSubscriptions.removeAll(channelsToModify);
                            
                            TimeUnit.MILLISECONDS.sleep(200);
                            
                            sync.subscribe(channelsToModify.toArray(new String[0]));
                            currentSubscriptions.addAll(channelsToModify);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error during subscription state change: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 1, 1, TimeUnit.SECONDS);

            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isRunning = false;
            scheduler.shutdown();
            connections.forEach(StatefulRedisPubSubConnection::close);
            clients.forEach(RedisClient::shutdown);
        }
    }

    // Main method remains unchanged
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java MultiSubscriber <redisHost> <redisPort> <connectionCount> <totalChannels>");
            System.out.println("Example: java MultiSubscriber localhost 6379 10 30000");
            System.exit(1);
        }

        try {
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            int connections = Integer.parseInt(args[2]);
            int channels = Integer.parseInt(args[3]);

            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            if (connections <= 0) {
                throw new IllegalArgumentException("Connection count must be positive");
            }
            if (channels <= 0) {
                throw new IllegalArgumentException("Total channels must be positive");
            }

            MultiSubscriber subscriber = new MultiSubscriber(host, port, connections, channels);
            subscriber.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format in arguments");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
