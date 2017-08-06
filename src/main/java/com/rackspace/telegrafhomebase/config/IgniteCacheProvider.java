package com.rackspace.telegrafhomebase.config;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningRemoteInputKey;
import com.rackspace.telegrafhomebase.model.TaggedNodes;
import com.rackspace.telegrafhomebase.model.TaggedNodesKey;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
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
    public IgniteCache<RunningRemoteInputKey, String> runningInputsCache() {
        return ignite.cache(CacheNames.RUNNING_REMOTE_INPUTS);
    }

    @Bean
    public IgniteCache<TaggedNodesKey, TaggedNodes> taggedNodesCache() {
        return ignite.cache(CacheNames.TAGGED_NODES);
    }
}
