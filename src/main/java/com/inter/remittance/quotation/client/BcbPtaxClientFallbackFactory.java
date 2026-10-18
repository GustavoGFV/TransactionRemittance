package com.inter.remittance.quotation.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;
import com.inter.remittance.quotation.exception.QuotationUnavailableException;

@Component
public class BcbPtaxClientFallbackFactory implements FallbackFactory<BcbPtaxClient> {

	private static final Logger log = LoggerFactory.getLogger(BcbPtaxClientFallbackFactory.class);

	@Override
	public BcbPtaxClient create(Throwable cause) {
		log.warn("BCB PTAX call failed, fallback triggered", cause);
		return new BcbPtaxClient() {
			@Override
			public BcbQuotationResponse findDollarQuotationByDay(Map<String, String> params) {
				throw new QuotationUnavailableException("BCB PTAX API is unavailable", cause);
			}
		};
	}

}
