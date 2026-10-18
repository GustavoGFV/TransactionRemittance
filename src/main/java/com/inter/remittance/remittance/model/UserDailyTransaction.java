package com.inter.remittance.remittance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.inter.remittance.remittance.exception.DailyLimitExceededException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// Tracks how much of the daily limit a user has already used (one row per user per day)
@Entity
@Table(name = "user_daily_transactions")
public class UserDailyTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "daily_limit_rule_id", nullable = false, updatable = false)
	private DailyLimitRule rule;

	@Column(name = "reached_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal reachedAmount = BigDecimal.ZERO;

	@Column(name = "transaction_date", nullable = false, updatable = false)
	private LocalDate transactionDate;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserDailyTransaction() {
	}

	public UserDailyTransaction(UUID userId, DailyLimitRule rule, LocalDate transactionDate) {
		this.userId = userId;
		this.rule = rule;
		this.transactionDate = transactionDate;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void register(BigDecimal amount) {
		BigDecimal limit = rule.getMaxAmount();
		BigDecimal total = reachedAmount.add(amount);
		if (total.compareTo(limit) > 0) {
			throw new DailyLimitExceededException(limit, limit.subtract(reachedAmount));
		}
		reachedAmount = total;
	}

	public Long getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public DailyLimitRule getRule() {
		return rule;
	}

	public BigDecimal getReachedAmount() {
		return reachedAmount;
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

}
