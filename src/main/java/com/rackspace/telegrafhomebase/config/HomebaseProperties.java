package com.rackspace.telegrafhomebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@ConfigurationProperties("homebase")
@Data
public class HomebaseProperties {

    /**
     * The amount of time to wait in milliseconds after startup to re-queue pending configurations that are not
     * currently running. This is primarily helpful during a cold-start of the system.
     */
    int initialConsistencyCheckDelay = 5_000;

    int consistencyCheckDelay = 60_000;
}
