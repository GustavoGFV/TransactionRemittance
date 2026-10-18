package com.inter.remittance.quotation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.inter.remittance.quotation.client.BcbPtaxClient;
import com.inter.remittance.quotation.client.dto.BcbQuotationItem;
import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;
import com.inter.remittance.quotation.config.QuotationCacheConfig;
import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.mapper.QuotationMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/*
 * Cache behaviour of QuotationService with the real Spring cache (Caffeine) and a mocked BCB client.
 * No Docker needed; the TTL is shortened to 400 ms so expiry can be observed.
 *
 *  1. Stampede protection: 50 concurrent requests for the same date produce a single BCB call.
 *  2. Hit: a repeated request is served from the cache.
 *  3. TTL: after the entry expires the BCB is queried again.
 *  4. Metrics: hits and misses are recorded by Caffeine (they feed the Micrometer cache.* metrics).
 */
@SpringBootTest(classes = QuotationCacheTest.TestConfig.class, properties = { "app.cache.quotations.ttl=400ms", "app.cache.quotations.max-size=10",
		"app.cache.quotations.stale-max-age=24h" })
class QuotationCacheTest {

	private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
	private static final BcbQuotationResponse RESPONSE = new BcbQuotationResponse(
			List.of(new BcbQuotationItem(new BigDecimal("5.0123"), new BigDecimal("5.0200"), "2026-10-16 13:05:00.0")));

	@Configuration
	@Import(QuotationCacheConfig.class)
	static class TestConfig {

		@Bean
		BcbPtaxClient bcbPtaxClient() {
			return mock(BcbPtaxClient.class);
		}

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		LastKnownQuotationStore lastKnownQuotationStore() {
			return new LastKnownQuotationStore(java.time.Duration.ofHours(24), Clock.systemUTC());
		}

		@Bean(destroyMethod = "shutdown")
		ExecutorService quotationExecutor() {
			return Executors.newVirtualThreadPerTaskExecutor();
		}

		@Bean
		QuotationService quotationService(BcbPtaxClient client, ExecutorService quotationExecutor,
				LastKnownQuotationStore store, MeterRegistry meters) {
			return new QuotationService(client, Mappers.getMapper(QuotationMapper.class), quotationExecutor, store,
					meters);
		}

	}

	@Autowired
	private QuotationService service;
	@Autowired
	private BcbPtaxClient client;
	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void setUp() {
		cacheManager.getCache(QuotationCacheConfig.QUOTATIONS_CACHE).clear();
		reset(client);
	}

	@Test
	void concurrentRequestsForTheSameDateCallTheBcbOnlyOnce() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		when(client.findDollarQuotationByDay(anyMap())).thenAnswer(invocation -> {
			release.await();
			return RESPONSE;
		});

		List<CompletableFuture<List<BuyQuotation>>> calls = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			calls.add(service.findBuyQuotations(DATE));
		}
		release.countDown();
		CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get();

		verify(client, times(1)).findDollarQuotationByDay(anyMap());
		assertThat(calls).allSatisfy(call -> assertThat(call.join()).hasSize(1));
	}

	@Test
	void repeatedRequestIsServedFromTheCache() throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(RESPONSE);

		service.findBuyQuotations(DATE).get();
		service.findBuyQuotations(DATE).get();

		verify(client, times(1)).findDollarQuotationByDay(anyMap());
		CacheStats stats = nativeCacheStats();
		assertThat(stats.hitCount()).isGreaterThanOrEqualTo(1);
		assertThat(stats.missCount()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void entryExpiresAfterTheTtl() throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(RESPONSE);

		service.findBuyQuotations(DATE).get();
		Thread.sleep(700);
		service.findBuyQuotations(DATE).get();

		verify(client, times(2)).findDollarQuotationByDay(anyMap());
	}

	private CacheStats nativeCacheStats() {
		var cache = (org.springframework.cache.caffeine.CaffeineCache) cacheManager
				.getCache(QuotationCacheConfig.QUOTATIONS_CACHE);
		return cache.getNativeCache().stats();
	}

}
