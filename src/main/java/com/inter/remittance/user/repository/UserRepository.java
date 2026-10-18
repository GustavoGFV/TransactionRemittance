package com.inter.remittance.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inter.remittance.user.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmail(String email);

	boolean existsByDocument(String document);

	boolean existsByEmailAndIdNot(String email, UUID id);

}
