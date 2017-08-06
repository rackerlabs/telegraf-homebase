package com.rackspace.telegrafhomebase.config;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningRemoteInputKey;
import com.rackspace.telegrafhomebase.model.TaggedNodes;
import com.rackspace.telegrafhomebase.model.TaggedNodesKey;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.store.cassandra.CassandraCacheStore;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import javax.cache.configuration.Factory;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Configuration
@ImportResource({
        /* needs to be classpath* for unit test classloader scoping */
        "classpath*:query-*.xml"
})
public class IgniteCacheConfigs {
    private final IgniteProperties properties;

    @Autowired
    public IgniteCacheConfigs(IgniteProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CacheConfiguration<String,ManagedInput> managedInputsCacheConfig(
            QueryEntities managedInputsQueryEntities,
            @Autowired(required = false)
            Factory<CassandraCacheStore<String, ManagedInput>> cacheStoreFactory
    ) {
        final CacheConfiguration<String,ManagedInput> config =
                new CacheConfiguration<>(CacheNames.MANAGED_INPUTS);
        config.setTypes(String.class, ManagedInput.class);
        config.setBackups(properties.getManagedInputsCacheBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        if (cacheStoreFactory != null) {
            config.setWriteThrough(true);
            config.setReadThrough(true);
            config.setWriteBehindEnabled(true);

            config.setCacheStoreFactory(cacheStoreFactory);
        }

        config.setQueryEntities(managedInputsQueryEntities.getQueryEntities());

        return config;
    }

    @Bean
    public CacheConfiguration<RunningRemoteInputKey,String/*tid*/> runningInputsCacheConfig(
            QueryEntities runningQueryEntities
    ) {
        final CacheConfiguration<RunningRemoteInputKey,String> config
                = new CacheConfiguration<>(CacheNames.RUNNING_REMOTE_INPUTS);
        config.setTypes(RunningRemoteInputKey.class, String.class);
        config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.SECONDS, properties.getRunningConfigTtl())
        ));
        config.setEagerTtl(true);
        config.setBackups(properties.getRunningConfigCacheBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        config.setQueryEntities(runningQueryEntities.getQueryEntities());

        return config;
    }

    @Bean
    public CacheConfiguration<TaggedNodesKey, TaggedNodes> taggedNodesCacheConfig() {
        final CacheConfiguration<TaggedNodesKey, TaggedNodes> config = new CacheConfiguration<>(
                CacheNames.TAGGED_NODES);

        config.setTypes(TaggedNodesKey.class, TaggedNodes.class);
        config.setBackups(properties.getRunningConfigCacheBackups());

        return config;
    }
}
