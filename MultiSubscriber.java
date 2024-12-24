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
    private static final String REDIS_HOST = "redis-10006.alon-data2-5160.env0.qa.redislabs.com";
    private static final int REDIS_PORT = 10007;
    private static final int CONNECTION_COUNT = 10;
    private static final int TOTAL_CHANNELS = 20000; // 20000 channels per connection
    private static final String CHANNEL_PREFIX = "channel-";
    private static final AtomicLong messageCounter = new AtomicLong(0);

    public static void main(String[] args) {
        List<RedisClient> clients = new ArrayList<>();
        List<StatefulRedisPubSubConnection<String, String>> connections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        Random random = new Random();

        try {
            // Create array of all channel names
            String[] allChannels = new String[TOTAL_CHANNELS];
            for (int i = 0; i < TOTAL_CHANNELS; i++) {
                allChannels[i] = CHANNEL_PREFIX + i;
            }

            // Create connections and subscribe to all channels
            for (int i = 0; i < CONNECTION_COUNT; i++) {
                RedisClient client = RedisClient.create(
                    String.format("redis://%s:%d", REDIS_HOST, REDIS_PORT)
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
                System.out.println(count);
            }, 1, 1, TimeUnit.SECONDS);

            // Schedule UNSUBSCRIBE from five random channels and then RESUBSCRIBE
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    for (StatefulRedisPubSubConnection<String, String> connection : connections) {
                        RedisPubSubCommands<String, String> sync = connection.sync();

                        // Pick 5 random channels to unsubscribe and resubscribe
                        List<String> randomChannels = new ArrayList<>();
                        for (int i = 0; i < 5; i++) {
                            randomChannels.add(allChannels[random.nextInt(TOTAL_CHANNELS)]);
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
}
