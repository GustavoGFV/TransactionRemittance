package com.inter.remittance.user.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.inter.remittance.user.dto.CreateUserRequest;
import com.inter.remittance.user.dto.UpdateUserRequest;
import com.inter.remittance.user.exception.DuplicateUserException;
import com.inter.remittance.user.exception.UserNotFoundException;
import com.inter.remittance.user.model.User;
import com.inter.remittance.user.repository.UserRepository;
import com.inter.remittance.user.validation.Documents;

@Service
public class UserService {

	private final UserRepository repository;

	public UserService(UserRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public User create(CreateUserRequest request) {
		String email = normalizeEmail(request.email());
		String document = Documents.normalize(request.document());

		if (repository.existsByEmail(email)) {
			throw new DuplicateUserException("email");
		}
		if (repository.existsByDocument(document)) {
			throw new DuplicateUserException("document");
		}

		User user = new User(request.name().strip(), email, document,
				orZero(request.balanceBrl()), orZero(request.balanceUsd()));
		try {
			return repository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Two concurrent requests can both pass the checks above; the unique constraints are the real guard
			throw new DuplicateUserException("email or document");
		}
	}

	@Transactional(readOnly = true)
	public User findById(UUID id) {
		return repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public Page<User> findAll(Pageable pageable) {
		return repository.findAll(pageable);
	}

	@Transactional
	public User update(UUID id, UpdateUserRequest request) {
		User user = findById(id);
		String email = normalizeEmail(request.email());

		if (repository.existsByEmailAndIdNot(email, id)) {
			throw new DuplicateUserException("email");
		}

		user.changeProfile(request.name().strip(), email);
		try {
			return repository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw new DuplicateUserException("email");
		}
	}

	@Transactional
	public void delete(UUID id) {
		if (!repository.existsById(id)) {
			throw new UserNotFoundException(id);
		}
		repository.deleteById(id);
	}

	private static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
