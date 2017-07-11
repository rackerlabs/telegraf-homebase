package com.rackspace.mmi.telegrafhomebase.services;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component @Slf4j
public class ConfigRepository {
    private final IgniteCache<String, StoredRegionalConfig> regionalConfigCache;
    private final IdCreator idCreator;

    @Autowired
    public ConfigRepository(Ignite ignite, IdCreator idCreator) {
        regionalConfigCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
        this.idCreator = idCreator;
    }

    public String createRegional(String region, String definition) {
        final StoredRegionalConfig config = new StoredRegionalConfig();
        config.setDefinition(definition);
        final String id = idCreator.create();
        config.setId(id);
        config.setRegion(region);

        log.debug("Creating externally provided configuration: {}", config);
        regionalConfigCache.put(config.getId(), config);

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
        regionalConfigCache.remove(id);
    }
}
