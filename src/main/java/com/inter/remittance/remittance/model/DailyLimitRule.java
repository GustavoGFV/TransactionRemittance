package com.inter.remittance.remittance.model;

import java.math.BigDecimal;

import com.inter.remittance.user.model.DocumentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Daily limit per document type: CPF (individual, PF) and CNPJ (company, PJ)
@Entity
@Table(name = "daily_limit_rules")
public class DailyLimitRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, unique = true, length = 4)
	private DocumentType documentType;

	@Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal maxAmount;

	protected DailyLimitRule() {
	}

	public DailyLimitRule(DocumentType documentType, BigDecimal maxAmount) {
		this.documentType = documentType;
		this.maxAmount = maxAmount;
	}

	public Long getId() {
		return id;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public BigDecimal getMaxAmount() {
		return maxAmount;
	}

}
