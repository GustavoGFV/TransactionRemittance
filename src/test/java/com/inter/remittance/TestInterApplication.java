package com.inter.remittance;

import org.springframework.boot.SpringApplication;

public class TestInterApplication {

	public static void main(String[] args) {
		SpringApplication.from(InterApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
