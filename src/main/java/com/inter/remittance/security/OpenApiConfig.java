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
		description = "JWT sent as Authorization: Bearer <token>. Obtain an ADMIN token at POST /auth/token (API client credentials) or a USER token at POST /auth/login (e-mail and password), then paste it here")
public class OpenApiConfig {

}
