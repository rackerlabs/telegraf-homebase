package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningAssignedInputKey;
import com.rackspace.telegrafhomebase.model.RunningRegionalInputKey;
import lombok.extern.slf4j.Slf4j;
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

    private final IgniteCache<RunningRegionalInputKey, String> runningRegionalCache;
    private final IgniteCache<String, ManagedInput> managedInputCache;
    private final IgniteTransactions igniteTransactions;
    private final IgniteCache<RunningAssignedInputKey, String> runningAssignedInputsCache;

    @Autowired
    public TelegrafWellBeingHandler(IgniteTransactions igniteTransactions,
                                    IgniteCacheProvider cacheProvider) {
        this.igniteTransactions = igniteTransactions;
        runningRegionalCache = cacheProvider.runningRegionalInputsCache();
        runningAssignedInputsCache = cacheProvider.runningAssignedInputsCache();
        managedInputCache = cacheProvider.managedInputsCache();
    }

    public Telegraf.CurrentStateResponse confirmState(Telegraf.Identifiers identifiers, List<String> activeConfigIdsList) {
        final String tid = identifiers.getTid();
        final String region = identifiers.getRegion();

        log.debug("Got keep alive from telegraf={}, activeConfigIds={}", tid, activeConfigIdsList);

        Telegraf.CurrentStateResponse.Builder resp = Telegraf.CurrentStateResponse.newBuilder();

        try (Transaction tx = igniteTransactions.txStart()) {

            activeConfigIdsList.forEach(configId -> {
                final RunningRegionalInputKey runningKey = new RunningRegionalInputKey(configId, region);

                if (managedInputCache.containsKey(configId)) {
                    // getting the key is enough to touch it and keep the entry alive
                    final String runningOn = runningRegionalCache.get(runningKey);

                    // runningOn might be null
                    if (runningOn == null) {
                        // but might be an assigned input
                        final RunningAssignedInputKey assignedKey = new RunningAssignedInputKey(configId, tid);
                        final String createdOnClusterNode = runningAssignedInputsCache.get(assignedKey);
                        if (createdOnClusterNode == null) {
                            // we must have restarted, so need to capture running assignment
                            log.info("Telegraf={} reported running managed input={}, but we didn't know it",
                                     tid, configId);
                            //  it's nobody's (because we probably did a cold restart), so tell them to stop
                            resp.addRemovedId(configId);
                        }

                    } else if (!tid.equals(runningOn)) {
                        log.warn("The managed input={} got reported by telegraf={}, but we thought it was assigned to {}",
                                 configId, tid, runningOn);
                        //  it's not theirs, so tell them to stop
                        resp.addRemovedId(configId);
                    }
                }
                else {
                    log.info("Config {} was removed, so reporting back to telegraf as such", configId);
                    // it's been removed, let them know and let's clean up
                    resp.addRemovedId(configId);

                    final RunningAssignedInputKey assignedKey = new RunningAssignedInputKey(configId, tid);
                    runningAssignedInputsCache.remove(assignedKey);
                    runningRegionalCache.remove(runningKey);
                }
            });

            tx.commit();
        }

        return resp.build();
    }
}
