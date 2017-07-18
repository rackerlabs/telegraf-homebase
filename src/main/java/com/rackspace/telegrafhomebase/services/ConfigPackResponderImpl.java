package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
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
    private final IgniteCache<RunningKey, String> runningCache;
    private final ConfigRepository configRepository;
    private boolean closed;

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite, ConfigRepository configRepository) {
        this.ignite = ignite;
        runningCache = ignite.cache(CacheNames.RUNNING);
        this.configRepository = configRepository;
    }

    @Override
    public void startConfigStreaming(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver) {
        log.debug("Setting up config pack provider for telegraf={} in region={}",
                  tid, region);

        final IgniteQueue<String> queue;
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

            final String configId;
            try {
                configId = queue.take();
            } catch (IgniteInterruptedException e) {
                log.warn("Interrupted during queue take, should happen only during shutdown");
                return;
            } catch (IgniteException e) {
                log.warn("Unexpceted exception while taking from pending config queue", e);
                responseObserver.onError(new IllegalStateException(
                        "Unexpected exception while taking from pending config queue"));
                return;
            }
            log.debug("Acquired next configuration in queue={}", configId);

            final StoredRegionalConfig config = configRepository.get(configId);

            if (config != null) {
                respondWithNextConfig(tid, region, responseObserver, queue, configId, config);
            }
            else {
                log.warn("Saw configId={} in pending queue, but no corresponding config object", configId);
            }
        }
    }

    private void respondWithNextConfig(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver, IgniteQueue<String> queue, String configId, StoredRegionalConfig config) {
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
                queue.offer(configId);
                log.debug("Farend {} cancelled stream", tid);
            } else {
                log.warn("Observed exception providing config pack to telegraf={}", tid, e);
            }
            try {
                close();
            } catch (IOException e1) { }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
