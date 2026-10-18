package com.inter.remittance.remittance.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Remittance(Long id, BigDecimal amountInDollars, BigDecimal buyQuotation, LocalDate date) {
}
