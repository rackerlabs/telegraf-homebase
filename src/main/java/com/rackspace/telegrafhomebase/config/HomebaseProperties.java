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

    int initialConsistencyCheckDelay = 5_000;

    int consistencyCheckDelay = 60_000;
}
