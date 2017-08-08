package com.rackspace.telegrafhomebase.config;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.ConnectedNode;
import com.rackspace.telegrafhomebase.model.DirectAssignments;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningAssignedInputKey;
import com.rackspace.telegrafhomebase.model.RunningRegionalInputKey;
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
 * This configures the application's Ignite cache instances into the Spring context.
 *
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
    public CacheConfiguration<RunningRegionalInputKey,String/*tid*/> runningRegionalInputsCacheConfig() {
        final CacheConfiguration<RunningRegionalInputKey,String> config
                = new CacheConfiguration<>(CacheNames.RUNNING_REGIONAL_INPUTS);
        config.setTypes(RunningRegionalInputKey.class, String.class);
        config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.SECONDS, properties.getRunningConfigTtl())
        ));
        config.setEagerTtl(true);
        config.setBackups(properties.getRunningConfigCacheBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        config.setIndexedTypes(RunningRegionalInputKey.class, String.class);

        return config;
    }

    @Bean
    public CacheConfiguration<TaggedNodesKey, TaggedNodes> taggedNodesCacheConfig(
            QueryEntities taggedNodesQueryEntities
    ) {
        final CacheConfiguration<TaggedNodesKey, TaggedNodes> config = new CacheConfiguration<>(
                CacheNames.TAGGED_NODES);

        config.setTypes(TaggedNodesKey.class, TaggedNodes.class);
        config.setBackups(properties.getRunningConfigCacheBackups());
        config.setQueryEntities(taggedNodesQueryEntities.getQueryEntities());

        return config;
    }

    @Bean
    public CacheConfiguration<RunningAssignedInputKey, String/*cluster node id*/> runningAssignedInputsCacheConfig() {
        final CacheConfiguration<RunningAssignedInputKey, String> config = new CacheConfiguration<>(
                CacheNames.RUNNING_ASSIGNED_INPUTS
        );
        config.setTypes(RunningAssignedInputKey.class, String.class);
        config.setBackups(properties.getRunningConfigCacheBackups());

        config.setIndexedTypes(RunningAssignedInputKey.class, String.class);

        return config;
    }

    @Bean
    public CacheConfiguration<String/*tid*/, DirectAssignments> directAssignmentsCacheConfig() {
        final CacheConfiguration<String, DirectAssignments> config = new CacheConfiguration<>(
                CacheNames.DIRECT_ASSIGNMENTS
        );
        config.setTypes(String.class, DirectAssignments.class);
        config.setBackups(properties.getRunningConfigCacheBackups());

        return config;
    }

    @Bean
    public CacheConfiguration<String/*tid*/, ConnectedNode> connectedNodesCacheConfig() {
        final CacheConfiguration<String/*tid*/, ConnectedNode> config = new CacheConfiguration<>(
                CacheNames.CONNECTED_NODES
        );
        config.setTypes(String.class, ConnectedNode.class);
        config.setBackups(properties.getRunningConfigCacheBackups());

        return config;
    }
}
