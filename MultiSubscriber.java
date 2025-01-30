package com.example;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MultiSubscriber {
    private final String redisHost;
    private final int redisPort;
    private final int connectionCount;
    private final int totalChannels;
    private static final String CHANNEL_PREFIX = "channel-";
    private static final AtomicLong messageCounter = new AtomicLong(0);

    public MultiSubscriber(String redisHost, int redisPort, int connectionCount, int totalChannels) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.connectionCount = connectionCount;
        this.totalChannels = totalChannels;
    }

    public void start() {
        List<RedisClient> clients = new ArrayList<>();
        List<StatefulRedisPubSubConnection<String, String>> connections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        Random random = new Random();

        try {
            // Create array of all channel names
            String[] allChannels = new String[totalChannels];
            for (int i = 0; i < totalChannels; i++) {
                allChannels[i] = CHANNEL_PREFIX + i;
            }

            // Create connections and subscribe to all channels
            for (int i = 0; i < connectionCount; i++) {
                RedisClient client = RedisClient.create(
                    String.format("redis://%s:%d", redisHost, redisPort)
                );
                clients.add(client);

                StatefulRedisPubSubConnection<String, String> connection = client.connectPubSub();
                connections.add(connection);

                connection.addListener(new RedisPubSubAdapter<String, String>() {
                    @Override
                    public void message(String channel, String message) {
                        messageCounter.incrementAndGet();
                    }
                });

                RedisPubSubCommands<String, String> sync = connection.sync();
                sync.subscribe(allChannels);
            }

            // Schedule message rate printing
            scheduler.scheduleAtFixedRate(() -> {
                long count = messageCounter.getAndSet(0);
                System.out.println("Messages received in last second: " + count);
            }, 1, 1, TimeUnit.SECONDS);

            // Schedule UNSUBSCRIBE from five random channels and then RESUBSCRIBE
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    for (StatefulRedisPubSubConnection<String, String> connection : connections) {
                        RedisPubSubCommands<String, String> sync = connection.sync();

                        // Pick 5 random channels to unsubscribe and resubscribe
                        List<String> randomChannels = new ArrayList<>();
                        for (int i = 0; i < 5; i++) {
                            randomChannels.add(allChannels[random.nextInt(totalChannels)]);
                        }

                        sync.unsubscribe(randomChannels.toArray(new String[0]));
                        TimeUnit.MILLISECONDS.sleep(200); // Delay between unsubscribe and resubscribe
                        sync.subscribe(randomChannels.toArray(new String[0]));
                    }
                } catch (Exception e) {
                    System.err.println("Error during unsubscribe/resubscribe: " + e.getMessage());
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

            // Input validation
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
