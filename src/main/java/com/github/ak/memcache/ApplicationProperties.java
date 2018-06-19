/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Nonnegative;

@Validated
@Configuration
@ConfigurationProperties("app")
public class ApplicationProperties {

    /** TCP port where server needs to listen */
    @Nonnegative private Integer port;
    /** Maximum allowed size of cache */
    @Nonnegative private Integer cacheCapacity;

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getCacheCapacity() {
        return cacheCapacity;
    }

    public void setCacheCapacity(Integer cacheCapacity) {
        this.cacheCapacity = cacheCapacity;
    }
}
