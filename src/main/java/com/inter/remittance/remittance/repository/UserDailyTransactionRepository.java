package com.inter.remittance.remittance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inter.remittance.remittance.model.UserDailyTransaction;

public interface UserDailyTransactionRepository extends JpaRepository<UserDailyTransaction, Long> {

	Optional<UserDailyTransaction> findByUserIdAndTransactionDate(UUID userId, LocalDate transactionDate);

}
