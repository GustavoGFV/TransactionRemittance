package com.inter.remittance.quotation.client;

import java.util.Date;

import org.springframework.context.annotation.Bean;

import feign.FeignException;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;

public class BcbPtaxClientConfig {

	private static final long RETRY_PERIOD_MS = 200;
	private static final long RETRY_MAX_PERIOD_MS = 1_000;
	private static final int MAX_ATTEMPTS = 4; // 1 call + 3 retries

	@Bean
	Retryer bcbPtaxRetryer() {
		return new Retryer.Default(RETRY_PERIOD_MS, RETRY_MAX_PERIOD_MS, MAX_ATTEMPTS);
	}

	// Feign only retries RetryableException; 5xx responses are made retryable here.
	@Bean
	ErrorDecoder bcbPtaxErrorDecoder() {
		ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
		return (methodKey, response) -> {
			Exception exception = defaultDecoder.decode(methodKey, response);
			if (response.status() >= 500 && exception instanceof FeignException feignException) {
				return new RetryableException(response.status(), feignException.getMessage(),
						response.request().httpMethod(), (Date) null, response.request());
			}
			return exception;
		};
	}

}
