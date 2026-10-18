package com.inter.remittance.quotation.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine cache for BCB quotations. Async mode is required because the cached method returns a
 * {@code CompletableFuture}: the value is stored when the future completes and failures are never cached.
 */
@Configuration
@EnableCaching
public class QuotationCacheConfig {

	public static final String QUOTATIONS_CACHE = "quotations";

	@Bean
	CacheManager cacheManager(@Value("${app.cache.quotations.ttl}") Duration ttl,
			@Value("${app.cache.quotations.max-size}") long maxSize) {
		CaffeineCacheManager manager = new CaffeineCacheManager(QUOTATIONS_CACHE);
		manager.setAsyncCacheMode(true);
		manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).recordStats());
		return manager;
	}

}
