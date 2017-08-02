package com.rackspace.telegrafhomebase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Configuration
@EnableSwagger2
public class DocsConfig {

    @Bean
    public Docket configApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                                 .version("1")
                                 .build())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.rackspace.telegrafhomebase.web"))
                .build()
                ;
    }
}
