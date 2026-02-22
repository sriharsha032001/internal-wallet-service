package com.internal_wallet.internal_wallet.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    // simple in-memory cache, no eviction needed — asset types never change after seeding
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("assetTypes");
    }
}
