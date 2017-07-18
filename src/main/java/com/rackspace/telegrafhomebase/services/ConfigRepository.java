package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component @Slf4j
public class ConfigRepository {
    private final IgniteCache<String, StoredRegionalConfig> regionalConfigCache;
    private final IgniteCache<RunningKey, String> runningCache;
    private final IdCreator idCreator;
    private final IgniteTransactions transactions;

    @Autowired
    public ConfigRepository(Ignite ignite, IdCreator idCreator) {
        regionalConfigCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
        runningCache = ignite.cache(CacheNames.RUNNING);
        transactions = ignite.transactions();
        this.idCreator = idCreator;
    }

    @PostConstruct
    public void loadCache() {
        regionalConfigCache.localLoadCacheAsync(null);
    }

    public String createRegional(String tenantId, String region, String definition, String title) {
        final StoredRegionalConfig config = new StoredRegionalConfig();
        config.setDefinition(definition);
        final String id = idCreator.create();
        config.setId(id);
        config.setTitle(title);
        config.setTenantId(tenantId);
        config.setRegion(region);

        log.debug("Creating externally provided configuration: {}", config);

        try (Transaction tx = transactions.txStart()) {
            regionalConfigCache.put(config.getId(), config);
            tx.commit();
        }

        return id;
    }

    public List<StoredRegionalConfig> getAll() {
        final ArrayList<StoredRegionalConfig> results = new ArrayList<>();

        for (Cache.Entry<String, StoredRegionalConfig> entry : regionalConfigCache) {
            results.add(entry.getValue());
        }

        return results;
    }

    public void delete(String id) {
        log.info("Deleting {}", id);

        try (Transaction tx = transactions.txStart()) {
            regionalConfigCache.remove(id);
            tx.commit();
        }
    }

    public StoredRegionalConfig getWithDetails(String region, String id) {
        final StoredRegionalConfig storedRegionalConfig = regionalConfigCache.get(id);

        storedRegionalConfig.setRunningOn(runningCache.get(new RunningKey(id, region)));

        return storedRegionalConfig;
    }

    public StoredRegionalConfig get(String id) {
        return regionalConfigCache.get(id);
    }

    public List<StoredRegionalConfig> getAllForTenant(String tenantId) {
        final SqlQuery<String, StoredRegionalConfig> query = new SqlQuery<>(
                StoredRegionalConfig.class,
                "tenantid = ?"
        );

        try (Transaction tx = transactions.txStart()) {
            final QueryCursor<Cache.Entry<String, StoredRegionalConfig>> queryCursor =
                    regionalConfigCache.query(query.setArgs(tenantId));
            return queryCursor.getAll().stream()
                    .map(entry -> {
                        final StoredRegionalConfig value = entry.getValue();

                        value.setRunningOn(runningCache.get(new RunningKey(value.getId(), value.getRegion())));

                        return value;
                    })
                    .collect(Collectors.toList());
        }
    }
}
