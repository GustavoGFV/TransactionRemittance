package com.inter.remittance.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.inter.remittance.user.model.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	boolean existsByEmail(String email);

	boolean existsByDocument(String document);

	boolean existsByEmailAndPublicIdNot(String email, UUID publicId);

	Optional<User> findByPublicId(UUID publicId);

	boolean existsByPublicId(UUID publicId);

	void deleteByPublicId(UUID publicId);

}
