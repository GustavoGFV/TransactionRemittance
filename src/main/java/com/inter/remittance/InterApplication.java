package com.inter.remittance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class InterApplication {

	public static void main(String[] args) {
		SpringApplication.run(InterApplication.class, args);
	}

}
