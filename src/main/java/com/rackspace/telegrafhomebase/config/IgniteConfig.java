package com.rackspace.telegrafhomebase.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.spring.SpringCacheManager;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.EventType;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.zk.TcpDiscoveryZookeeperIpFinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Geoff Bourne
 * @since Jun 2017
 */
@Configuration @Slf4j
public class IgniteConfig {

    private final IgniteProperties properties;
    private final TelegrafProperties telegrafProperties;
    private final CacheConfiguration[] cacheConfigs;

    @Autowired
    public IgniteConfig(IgniteProperties properties,
                        TelegrafProperties telegrafProperties,
                        CacheConfiguration[] cacheConfigs) {
        this.properties = properties;
        this.telegrafProperties = telegrafProperties;
        this.cacheConfigs = cacheConfigs;
    }

    @Bean
    public DiscoverySpi zkDiscoverySpi() {
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();

        final TcpDiscoveryZookeeperIpFinder ipFinder = new TcpDiscoveryZookeeperIpFinder();
        ipFinder.setZkConnectionString(properties.getZkConnection());

        spi.setIpFinder(ipFinder);

        return spi;
    }

    @Bean
    public IgniteConfiguration igniteConfiguration() {
        final IgniteConfiguration configuration = new IgniteConfiguration();

        configuration.setCacheConfiguration(cacheConfigs);

        if (properties.getZkConnection() != null) {
            configuration.setDiscoverySpi(zkDiscoverySpi());
        }

        configuration.setMetricsLogFrequency(properties.getMetricsLogFrequency());

        configuration.setIncludeEventTypes(
                EventType.EVT_CACHE_OBJECT_PUT,
                EventType.EVT_CACHE_OBJECT_REMOVED,
                EventType.EVT_CACHE_OBJECT_EXPIRED
        );

        return configuration;
    }

    @Bean
    public Ignite ignite() {
        final Ignite ignite = Ignition.start(igniteConfiguration());

        return ignite;
    }

    @Bean
    public SpringCacheManager igniteSpringCacheManager() {
        return new SpringCacheManager();
    }
}
