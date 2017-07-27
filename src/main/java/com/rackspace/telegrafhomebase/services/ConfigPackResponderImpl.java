package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.config.TelegrafProperties;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import com.rackspace.telegrafhomebase.shared.ConfigObserverBundle;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import remote.Telegraf;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service
@Slf4j
public class ConfigPackResponderImpl implements Closeable, ConfigPackResponder {

    private final Ignite ignite;
    private final IgniteCache<RunningKey, String> runningCache;
    private final ConfigRepository configRepository;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final TelegrafProperties telegrafProperties;
    private final Random rand;
    private boolean closed;
    private ThreadGroup threadGroup;
    private Map<String, ConfigObserverBundle> bundles
            = Collections.synchronizedMap(new HashMap<>());

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite,
                                   ConfigRepository configRepository,
                                   PendingConfigQueuer pendingConfigQueuer,
                                   TelegrafProperties telegrafProperties,
                                   Random rand) {
        this.ignite = ignite;
        runningCache = ignite.cache(CacheNames.RUNNING);
        this.configRepository = configRepository;
        this.pendingConfigQueuer = pendingConfigQueuer;
        this.telegrafProperties = telegrafProperties;
        this.rand = rand;
    }

    @PostConstruct
    public void startResponders() {

        threadGroup = new ThreadGroup("responders");

        for (String region : telegrafProperties.getRegions()) {
            final ConfigObserverBundle observerBundle = new ConfigObserverBundle(rand);
            bundles.put(region, observerBundle);

            final Thread thread = new Thread(threadGroup, String.format("responder-%s", region)){
                @Override
                public void run() {
                    log.debug("Starting");
                    try {
                        observe(region, observerBundle);
                    } catch (Exception e) {
                        log.warn("Unexpected exception", e);
                    }
                    log.debug("Stopping");
                }
            };

            thread.start();
        }
    }

    private void observe(String region, ConfigObserverBundle observerBundle) {
        pendingConfigQueuer.observe(region, new PendingConfigQueuer.Handler() {
            @Override
            public boolean handle(String configId) {
                final StoredRegionalConfig config = configRepository.get(configId);

                if (config != null) {
                    observerBundle.respondToOne(entry -> {
                        try {
                            return respondWithNextConfig(entry.getTid(), region, entry.getStream(), configId, config);
                        } catch (Exception e) {
                            log.warn("Unexpected exception", e);
                            return false;
                        }
                    });
                } else {
                    log.warn("Saw configId={} in pending queue, but no corresponding config object", configId);
                }

                return !closed;
            }

            @Override
            public void onError(Throwable throwable) {
                observerBundle.respondToAll(entry -> {
                    try {
                        entry.getStream().onError(throwable);
                    } catch (Exception e) {
                        log.warn("Unexpected exception provided an error back to telegraf={}", entry.getTid(), e);
                    }
                    // all will get removed from rotation and will need to re-initiate
                    return false;
                });
            }

            @Override
            public boolean waitForReady() throws InterruptedException {
                return observerBundle.waitForReady();
            }
        });
    }

    @Override
    public void startConfigStreaming(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver) {
        log.debug("Setting up config pack provider for telegraf={} in region={}",
                  tid, region);

        final ConfigObserverBundle bundle = bundles.get(region);
        if (bundle != null) {
            bundle.add(new ConfigObserverBundle.Entry(tid, responseObserver));
        }
        else {
            log.warn("telegraf={} reported a region={} that is not configured", tid, region);
            responseObserver.onError(new IllegalArgumentException("Unknown region"));
        }
    }

    /**
     * @return true to keep this telegraf in the response bundle
     */
    private boolean respondWithNextConfig(String tid,
                                          String region,
                                          StreamObserver<Telegraf.ConfigPack> responseObserver,
                                          String configId,
                                          StoredRegionalConfig config) {
        final Telegraf.ConfigPack.Builder configPackBuilder = Telegraf.ConfigPack.newBuilder();
        final Telegraf.Config.Builder builder = Telegraf.Config.newBuilder()
                .setId(config.getId())
                .setTenantId(config.getTenantId())
                .setDefinition(config.getDefinition());

        if (config.getTitle() != null) {
            builder.setTitle(config.getTitle());
        }

        configPackBuilder.addNew(builder.build());

        try {
            final Telegraf.ConfigPack configPack = configPackBuilder.build();
            log.debug("Responding to {} with configPack={}", tid, configPack);
            responseObserver.onNext(configPack);

            final RunningKey key = new RunningKey(config.getId(), region);
            log.debug("Noting initial application of {} to {}", key, tid);

            try (Transaction tx = ignite.transactions().txStart()) {
                runningCache.put(key, tid);
                tx.commit();
            }
            // keep this telegraf in rotation
            return true;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().equals(Status.CANCELLED)) {
                log.debug("Farend telegraf={} cancelled", tid);
            } else {
                log.warn("Observed exception providing config pack to telegraf={}", tid, e);
            }

            log.debug("Re-queueing due to exception: {}", config);
            pendingConfigQueuer.offer(region, configId, true);
            // and take this telegraf out of rotation
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
