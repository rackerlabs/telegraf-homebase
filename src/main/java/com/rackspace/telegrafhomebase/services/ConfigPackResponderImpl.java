package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.config.IgniteProperties;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import com.rackspace.telegrafhomebase.shared.DistributedQueueUtils;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteInterruptedException;
import org.apache.ignite.IgniteQueue;
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
    private final IgniteCache<RunningKey, String> appliedCache;
    private boolean closed;

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite, IgniteProperties igniteProperties) {
        this.ignite = ignite;
        appliedCache = ignite.cache(CacheNames.RUNNING);
    }

    @Override
    public void startConfigStreaming(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver) {
        log.debug("Setting up config pack provider for telegraf={} in region={}",
                  tid, region);

        final IgniteQueue<StoredRegionalConfig> queue;
        try {
            queue = ignite.queue(DistributedQueueUtils.derivePendingConfigQueueName(
                    region),
                                 0, null);

        } catch (Exception e) {
            log.warn("Unexpected exception while locating queue", e);
            responseObserver.onError(new IllegalStateException("Unexpected exception while locating queue"));
            return;
        }

        while (!closed) {
            log.debug("Waiting for next pending config");

            final StoredRegionalConfig config;
            try {
                config = queue.take();
            } catch (IgniteInterruptedException e) {
                log.warn("Interrupted during queue take, should happen only during shutdown");
                return;
            } catch (IgniteException e) {
                log.warn("Unexpceted exception while taking from pending config queue", e);
                responseObserver.onError(new IllegalStateException(
                        "Unexpected exception while taking from pending config queue"));
                return;
            }
            log.debug("Acquired next configuration in queue={}", config);

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
                    appliedCache.put(key, tid);
                    tx.commit();
                }
            } catch (StatusRuntimeException e) {
                if (e.getStatus().equals(Status.CANCELLED)) {
                    log.debug("Re-queueing due to cancelled telegraf: {}", config);
                    queue.offer(config);
                    log.debug("Farend {} cancelled stream", tid);
                } else {
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
