package com.inter.remittance.remittance.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	// Daily limits and quotation dates follow Brasília time, regardless of the server timezone
	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("America/Sao_Paulo"));
	}

}
