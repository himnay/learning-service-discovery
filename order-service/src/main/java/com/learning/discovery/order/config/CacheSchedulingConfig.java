package com.learning.discovery.order.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Kept off the @SpringBootApplication class so @WebMvcTest slices (which don't load
// the cache auto-configuration) don't fail looking for a CacheManager.
@Configuration
@EnableCaching
@EnableScheduling
class CacheSchedulingConfig {
}
