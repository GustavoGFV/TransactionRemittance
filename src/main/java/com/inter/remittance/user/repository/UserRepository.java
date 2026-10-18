package com.inter.remittance.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.inter.remittance.user.model.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByDocument(String document);

	boolean existsByEmailAndPublicIdNot(String email, UUID publicId);

	Optional<User> findByPublicId(UUID publicId);

	boolean existsByPublicId(UUID publicId);

	void deleteByPublicId(UUID publicId);

	Optional<User> findByDocument(String document);

	// Ordered by id so concurrent operations always acquire row locks in the same order (no deadlocks)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.publicId in :publicIds order by u.id")
	List<User> lockAllByPublicIdIn(@Param("publicIds") Collection<UUID> publicIds);

}
