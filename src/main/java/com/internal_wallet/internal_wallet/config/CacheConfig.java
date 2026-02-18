package com.internal_wallet.internal_wallet.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * In-memory cache backed by ConcurrentHashMap.
     * Asset types are seeded once and never mutated at runtime, so this cache
     * needs no eviction policy. Declaring the cache name here ensures it is
     * ready before the first @Cacheable call on AssetTypeRepository.findByName().
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("assetTypes");
    }
}
