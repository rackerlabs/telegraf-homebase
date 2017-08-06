package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningRemoteInputKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import remote.Telegraf;

import java.util.List;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service @Slf4j
public class TelegrafWellBeingHandler {

    private final IgniteCache<RunningRemoteInputKey, String> runningCache;
    private final IgniteCache<String, ManagedInput> storedCache;
    private final IgniteTransactions igniteTransactions;

    @Autowired
    public TelegrafWellBeingHandler(Ignite ignite, IgniteCacheProvider cacheProvider) {
        runningCache = cacheProvider.runningInputsCache();
        storedCache = cacheProvider.managedInputsCache();
        igniteTransactions = ignite.transactions();
    }

    public Telegraf.CurrentStateResponse confirmState(Telegraf.Identifiers identifiers, List<String> activeConfigIdsList) {
        final String tid = identifiers.getTid();
        final String region = identifiers.getRegion();

        log.debug("Got keep alive from telegraf={}, activeConfigIds={}", tid, activeConfigIdsList);

        Telegraf.CurrentStateResponse.Builder resp = Telegraf.CurrentStateResponse.newBuilder();

        try (Transaction tx = igniteTransactions.txStart()) {

            activeConfigIdsList.forEach(configId -> {
                final RunningRemoteInputKey runningKey = new RunningRemoteInputKey(configId, region);

                if (storedCache.containsKey(configId)) {
                    // getting the key is enough to touch it and keep the entry alive
                    final String assignedTo = runningCache.get(runningKey);

                    // runningOn might be null
                    if (assignedTo == null) {
                        // we must have restarted, so need to capture running assignment
                        log.info("Telegraf={} reported running managed input={}, but we didn't know it",
                                 tid, configId);
                        //  it's nobody's (because we probably did a cold restart), so tell them to stop
                        resp.addRemovedId(configId);
                    } else if (!tid.equals(assignedTo)) {
                        log.warn("The managed input={} got reported by telegraf={}, but we thought it was assigned to {}",
                                 configId, tid, assignedTo);
                        //  it's not theirs, so tell them to stop
                        resp.addRemovedId(configId);
                    }
                }
                else {
                    log.info("Config {} was removed, so reporting back to telegraf as such", configId);
                    // it's been removed, let them know and let's clean up
                    resp.addRemovedId(configId);
                    runningCache.remove(runningKey);
                }
            });

            tx.commit();
        }

        return resp.build();
    }
}
