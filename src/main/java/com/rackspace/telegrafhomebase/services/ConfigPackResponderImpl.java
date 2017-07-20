package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
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

import java.io.Closeable;
import java.io.IOException;

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
    private boolean closed;

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite,
                                   ConfigRepository configRepository,
                                   PendingConfigQueuer pendingConfigQueuer) {
        this.ignite = ignite;
        runningCache = ignite.cache(CacheNames.RUNNING);
        this.configRepository = configRepository;
        this.pendingConfigQueuer = pendingConfigQueuer;
    }

    @Override
    public void startConfigStreaming(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver) {
        log.debug("Setting up config pack provider for telegraf={} in region={}",
                  tid, region);

        pendingConfigQueuer.observe(region, new PendingConfigQueuer.Handler() {
            @Override
            public boolean handle(String configId) {
                final StoredRegionalConfig config = configRepository.get(configId);

                if (config != null) {
                    respondWithNextConfig(tid, region, responseObserver, configId, config);
                } else {
                    log.warn("Saw configId={} in pending queue, but no corresponding config object", configId);
                }

                return !closed;
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }
        });
    }

    private void respondWithNextConfig(String tid,
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
        } catch (StatusRuntimeException e) {
            if (e.getStatus().equals(Status.CANCELLED)) {
                log.debug("Re-queueing due to cancelled telegraf: {}", config);
                pendingConfigQueuer.offer(region, configId);
                log.debug("Farend {} cancelled stream", tid);
            } else {
                log.warn("Observed exception providing config pack to telegraf={}", tid, e);
            }
            try {
                close();
            } catch (IOException e1) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
