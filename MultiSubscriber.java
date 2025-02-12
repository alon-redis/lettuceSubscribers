package com.example;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MultiSubscriber {
    private final String redisHost;
    private final int redisPort;
    private final int connectionCount;
    private final int totalChannels;
    private static final String CHANNEL_PREFIX = "channel-";
    private static final AtomicLong messageCounter = new AtomicLong(0);
    
    // Thread-safe collection to maintain connection-specific channel subscriptions
    private final ConcurrentMap<StatefulRedisPubSubConnection<String, String>, Set<String>> connectionSubscriptions;
    
    public MultiSubscriber(String redisHost, int redisPort, int connectionCount, int totalChannels) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.connectionCount = connectionCount;
        this.totalChannels = totalChannels;
        this.connectionSubscriptions = new ConcurrentHashMap<>();
    }

    public void start() {
        List<RedisClient> clients = new ArrayList<>();
        List<StatefulRedisPubSubConnection<String, String>> connections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        Random random = new Random();

        try {
            // Initialize channel array
            String[] allChannels = new String[totalChannels];
            for (int i = 0; i < totalChannels; i++) {
                allChannels[i] = CHANNEL_PREFIX + i;
            }

            // Initialize connections with thread-safe subscription tracking
            for (int i = 0; i < connectionCount; i++) {
                RedisClient client = RedisClient.create(
                    String.format("redis://%s:%d", redisHost, redisPort)
                );
                clients.add(client);
                
                StatefulRedisPubSubConnection<String, String> connection = client.connectPubSub();
                connections.add(connection);
                
                // Initialize thread-safe set for this connection's subscriptions
                connectionSubscriptions.put(connection, ConcurrentHashMap.newKeySet());
                
                connection.addListener(new RedisPubSubAdapter<String, String>() {
                    @Override
                    public void message(String channel, String message) {
                        messageCounter.incrementAndGet();
                    }
                });

                // Initial subscription
                RedisPubSubCommands<String, String> sync = connection.sync();
                sync.subscribe(allChannels);
                
                // Record initial subscriptions
                connectionSubscriptions.get(connection).addAll(Arrays.asList(allChannels));
            }

            // Message rate monitoring
            scheduler.scheduleAtFixedRate(() -> {
                long count = messageCounter.getAndSet(0);
                System.out.println("Messages received in last second: " + count);
            }, 1, 1, TimeUnit.SECONDS);

            // Enhanced unsubscribe/resubscribe scheduler with connection affinity
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    for (StatefulRedisPubSubConnection<String, String> connection : connections) {
                        // Get current subscriptions for this connection
                        Set<String> currentSubscriptions = connectionSubscriptions.get(connection);
                        
                        // Select random channels from current subscriptions
                        List<String> subscribedChannels = new ArrayList<>(currentSubscriptions);
                        List<String> channelsToModify = new ArrayList<>();
                        
                        // Ensure we don't try to modify more channels than available
                        int numChannelsToModify = Math.min(5, subscribedChannels.size());
                        for (int i = 0; i < numChannelsToModify; i++) {
                            int randomIndex = random.nextInt(subscribedChannels.size());
                            channelsToModify.add(subscribedChannels.remove(randomIndex));
                        }

                        if (!channelsToModify.isEmpty()) {
                            RedisPubSubCommands<String, String> sync = connection.sync();
                            
                            // Atomic unsubscribe operation
                            sync.unsubscribe(channelsToModify.toArray(new String[0]));
                            // Update subscription tracking
                            currentSubscriptions.removeAll(channelsToModify);
                            
                            // Controlled delay to prevent potential race conditions
                            TimeUnit.MILLISECONDS.sleep(200);
                            
                            // Atomic resubscribe operation on same connection
                            sync.subscribe(channelsToModify.toArray(new String[0]));
                            // Update subscription tracking
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
