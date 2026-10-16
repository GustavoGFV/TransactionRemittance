package com.inter.remittance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InterApplicationTests {

	@Test
	void contextLoads() {
	}

}
