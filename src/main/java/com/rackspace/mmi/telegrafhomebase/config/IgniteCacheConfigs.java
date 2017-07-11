package com.rackspace.mmi.telegrafhomebase.config;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.model.AppliedKey;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import org.apache.ignite.cache.eviction.EvictableEntry;
import org.apache.ignite.cache.eviction.EvictionPolicy;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.NearCacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Configuration
public class IgniteCacheConfigs {
    private final IgniteProperties properties;

    @Autowired
    public IgniteCacheConfigs(IgniteProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CacheConfiguration<String,StoredRegionalConfig> regionalConfigCacheConfig() {
        final CacheConfiguration<String,StoredRegionalConfig> config =
                new CacheConfiguration<>(CacheNames.REGIONAL_CONFIG);
        config.setTypes(String.class, StoredRegionalConfig.class);
        config.setNearConfiguration(new NearCacheConfiguration<>());

        return config;
    }

    @Bean
    public CacheConfiguration<AppliedKey,String> appliedConfigsCacheConfig() {
        final CacheConfiguration<AppliedKey,String> config = new CacheConfiguration<>(CacheNames.APPLIED);
        config.setTypes(AppliedKey.class, String.class);
        config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.SECONDS, properties.getAppliedConfigTTL())
        ));
        config.setEagerTtl(true);
        config.setNearConfiguration(new NearCacheConfiguration<>());
        return config;
    }
}
