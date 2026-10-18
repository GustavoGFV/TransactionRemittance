package com.inter.remittance.quotation.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;

@FeignClient(
		name = "bcb-ptax",
		url = "${bcb.ptax.url}",
		configuration = BcbPtaxClientConfig.class,
		fallbackFactory = BcbPtaxClientFallbackFactory.class)
public interface BcbPtaxClient {

	@GetMapping("/CotacaoDolarDia(dataCotacao=@dataCotacao)")
	BcbQuotationResponse findDollarQuotationByDay(@RequestParam Map<String, String> params);

}
