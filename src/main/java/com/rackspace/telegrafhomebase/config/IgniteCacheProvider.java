package com.rackspace.telegrafhomebase.config;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.ConnectedNode;
import com.rackspace.telegrafhomebase.model.DirectAssignments;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningAssignedInputKey;
import com.rackspace.telegrafhomebase.model.RunningRegionalInputKey;
import com.rackspace.telegrafhomebase.model.TaggedNodes;
import com.rackspace.telegrafhomebase.model.TaggedNodesKey;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * This provides type safe accessors to the caches configured in {@link IgniteCacheConfigs}.
 *
 * <p>
 *     This class is not a {@link org.springframework.context.annotation.Configuration} bean since
 *     it is only needed during regular bean startup.
 * </p>
 *
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Component
public class IgniteCacheProvider {

    private final Ignite ignite;

    @Autowired
    public IgniteCacheProvider(Ignite ignite) {
        this.ignite = ignite;
    }

    @Bean
    public IgniteCache<String/*stored config id*/, ManagedInput> managedInputsCache() {
        return ignite.cache(CacheNames.MANAGED_INPUTS);
    }

    @Bean
    public IgniteCache<RunningRegionalInputKey, String> runningRegionalInputsCache() {
        return ignite.cache(CacheNames.RUNNING_REGIONAL_INPUTS);
    }

    @Bean
    public IgniteCache<TaggedNodesKey, TaggedNodes> taggedNodesCache() {
        return ignite.cache(CacheNames.TAGGED_NODES);
    }

    @Bean
    public IgniteCache<String/*tid*/, ConnectedNode> connectedNodesCache() {
        return ignite.cache(CacheNames.CONNECTED_NODES);
    }

    @Bean
    public IgniteCache<String/*tid*/, DirectAssignments> directAssignmentsCache() {
        return ignite.cache(CacheNames.DIRECT_ASSIGNMENTS);
    }

    /**
     * Tracks a specific assignment of a managed input onto a telegraf instance. The presence of the key is
     * the important thing, but the ignite cluster node ID where the assignment created is recorded as the
     * value.
     */
    @Bean
    public IgniteCache<RunningAssignedInputKey, String/*cluster node id*/> runningAssignedInputsCache() {
        return ignite.cache(CacheNames.RUNNING_ASSIGNED_INPUTS);
    }
}
