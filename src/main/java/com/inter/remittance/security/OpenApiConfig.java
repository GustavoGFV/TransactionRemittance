package com.inter.remittance.security;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Inter Remittance API", version = "1.0",
		description = "Cross-border remittances (BRL to USD) with daily limits and BCB PTAX quotations"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
		description = "Obtain a token at POST /auth/token and paste it here")
public class OpenApiConfig {

}
