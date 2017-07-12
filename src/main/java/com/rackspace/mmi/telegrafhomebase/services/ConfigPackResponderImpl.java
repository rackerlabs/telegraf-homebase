package com.rackspace.mmi.telegrafhomebase.services;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.config.IgniteProperties;
import com.rackspace.mmi.telegrafhomebase.model.AppliedKey;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import com.rackspace.mmi.telegrafhomebase.shared.DistributedQueueUtils;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import remote.Telegraf;

import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service
@Slf4j
public class ConfigPackResponderImpl implements Closeable, ConfigPackResponder {

    private final Ignite ignite;
    private final IgniteCache<AppliedKey, String> appliedCache;
    private boolean closed;

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite, IgniteProperties igniteProperties) {
        this.ignite = ignite;
        appliedCache = ignite.cache(CacheNames.APPLIED);
    }

    @Override
    public void startConfigStreaming(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver) {
        log.debug("Setting up config pack provider for telegraf={} in region={}",
                  tid, region);

        final IgniteQueue<StoredRegionalConfig> queue = ignite.queue(DistributedQueueUtils.derivePendingConfigQueueName(
                region),
                                                                     0, null);
        while (!closed) {
            log.debug("Waiting for next pending config");

            final StoredRegionalConfig config;
            try {
                config = queue.take();
            } catch (IgniteException e) {
                if (closed) {
                    log.trace("Expected exception during shutdown", e);
                } else {
                    log.warn("Unexpceted exception while taking from pending config queue", e);
                    responseObserver.onError(new IllegalStateException("Unexpceted exception while taking from pending config queue"));
                }
                return;
            }
            log.debug("Acquired next configuration in queue={}", config);

            final Telegraf.ConfigPack.Builder configPackBuilder = Telegraf.ConfigPack.newBuilder();
            Telegraf.Config sentConfig = Telegraf.Config.newBuilder()
                    .setId(config.getId())
                    .setTenantId(config.getTenantId())
                    .setDefinition(config.getDefinition())
                    .build();
            configPackBuilder.addNew(sentConfig);

            try {
                final Telegraf.ConfigPack configPack = configPackBuilder.build();
                log.debug("Responding to {} with configPack={}", tid, configPack);
                responseObserver.onNext(configPack);

                final AppliedKey key = new AppliedKey(config.getId(), region);
                log.debug("Noting initial application of {} to {}", key, tid);
                appliedCache.put(key, tid);
            } catch (StatusRuntimeException e) {
                if (e.getStatus().equals(Status.CANCELLED)) {
                    log.debug("Re-queueing due to cancelled telegraf: {}", config);
                    queue.offer(config);
                    log.debug("Farend {} cancelled stream", tid);
                }
                else {
                    log.warn("Observed exception providing config pack to telegraf={}", tid, e);
                }
                return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
