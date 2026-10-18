package com.inter.remittance.user.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.inter.remittance.user.exception.InsufficientBalanceException;
import com.inter.remittance.user.validation.Documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Identifier exposed by the API; the sequential id never leaves the database
	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 254, unique = true)
	private String email;

	@Column(nullable = false, length = 14, unique = true)
	private String document;

	// BCrypt hash only; the plain password is never stored or exposed
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "balance_brl", nullable = false, precision = 19, scale = 2)
	private BigDecimal balanceBrl;

	@Column(name = "balance_usd", nullable = false, precision = 19, scale = 2)
	private BigDecimal balanceUsd;

	// Optimistic locking: concurrent balance updates fail fast instead of overwriting each other
	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String name, String email, String document, String passwordHash, BigDecimal balanceBrl,
			BigDecimal balanceUsd) {
		this.publicId = UUID.randomUUID();
		this.name = name;
		this.email = email;
		this.document = document;
		this.passwordHash = passwordHash;
		this.balanceBrl = balanceBrl;
		this.balanceUsd = balanceUsd;
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

	public void changeProfile(String name, String email) {
		this.name = name;
		this.email = email;
	}

	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void debitBrl(BigDecimal amount) {
		if (balanceBrl.compareTo(amount) < 0) {
			throw new InsufficientBalanceException("BRL", balanceBrl, amount);
		}
		balanceBrl = balanceBrl.subtract(amount);
	}

	public void creditBrl(BigDecimal amount) {
		balanceBrl = balanceBrl.add(amount);
	}

	public void debitUsd(BigDecimal amount) {
		if (balanceUsd.compareTo(amount) < 0) {
			throw new InsufficientBalanceException("USD", balanceUsd, amount);
		}
		balanceUsd = balanceUsd.subtract(amount);
	}

	public void creditUsd(BigDecimal amount) {
		balanceUsd = balanceUsd.add(amount);
	}

	public DocumentType getDocumentType() {
		return Documents.typeOf(document);
	}

	public Long getId() {
		return id;
	}

	public UUID getPublicId() {
		return publicId;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getDocument() {
		return document;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public BigDecimal getBalanceBrl() {
		return balanceBrl;
	}

	public BigDecimal getBalanceUsd() {
		return balanceUsd;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
