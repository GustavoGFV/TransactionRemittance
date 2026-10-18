package com.inter.remittance.remittance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inter.remittance.remittance.model.DailyLimitRule;
import com.inter.remittance.user.model.DocumentType;

public interface DailyLimitRuleRepository extends JpaRepository<DailyLimitRule, Long> {

	Optional<DailyLimitRule> findByDocumentType(DocumentType documentType);

}
