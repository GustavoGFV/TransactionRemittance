package com.inter.remittance.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.inter.remittance.user.dto.CreateUserRequest;
import com.inter.remittance.user.dto.UpdateUserRequest;
import com.inter.remittance.user.exception.DuplicateUserException;
import com.inter.remittance.user.exception.UserNotFoundException;
import com.inter.remittance.user.model.User;
import com.inter.remittance.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	UserRepository repository;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	UserService service;

	private CreateUserRequest request() {
		return new CreateUserRequest(" Maria Silva ", "Maria@Mail.COM", "529.982.247-25", "S3nh@Forte2026", null,
				new BigDecimal("10.50"));
	}

	@Test
	void createNormalizesDataAndDefaultsBalanceToZero() {
		when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User user = service.create(request());

		assertThat(user.getName()).isEqualTo("Maria Silva");
		assertThat(user.getEmail()).isEqualTo("maria@mail.com");
		assertThat(user.getDocument()).isEqualTo("52998224725");
		assertThat(user.getBalanceBrl()).isEqualByComparingTo("0");
		assertThat(user.getBalanceUsd()).isEqualByComparingTo("10.50");
	}

	@Test
	void createStoresOnlyTheEncodedPassword() {
		when(passwordEncoder.encode("S3nh@Forte2026")).thenReturn("encoded");
		when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User user = service.create(request());

		assertThat(user).extracting("passwordHash").isEqualTo("encoded");
	}

	@Test
	void createRequestToStringNeverExposesThePassword() {
		assertThat(request().toString()).doesNotContain("S3nh@Forte2026");
	}

	@Test
	void updateChangesThePasswordOnlyWhenProvided() {
		UUID id = UUID.randomUUID();
		User existing = new User("A", "a@mail.com", "52998224725", "old-hash", BigDecimal.ZERO, BigDecimal.ZERO);
		when(repository.findByPublicId(id)).thenReturn(Optional.of(existing));
		when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(passwordEncoder.encode("N0v@Senha2026")).thenReturn("new-hash");

		service.update(id, new UpdateUserRequest("A", "a@mail.com", null));
		assertThat(existing).extracting("passwordHash").isEqualTo("old-hash");

		service.update(id, new UpdateUserRequest("A", "a@mail.com", "N0v@Senha2026"));
		assertThat(existing).extracting("passwordHash").isEqualTo("new-hash");
	}

	@Test
	void findAllDelegatesFilteringAndPagingToTheRepository() {
		Page<User> page = new PageImpl<>(java.util.List.of());
		when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

		assertThat(service.findAll(new com.inter.remittance.user.dto.UserFilter("maria", null, null), PageRequest.of(0, 10)))
				.isSameAs(page);
	}

	@Test
	void createRejectsDuplicatedEmail() {
		when(repository.existsByEmail("maria@mail.com")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request())).isInstanceOf(DuplicateUserException.class);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void createRejectsDuplicatedDocument() {
		when(repository.existsByDocument("52998224725")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request())).isInstanceOf(DuplicateUserException.class);
	}

	@Test
	void createMapsConcurrentUniqueViolationToDuplicate() {
		when(repository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_email"));

		assertThatThrownBy(() -> service.create(request())).isInstanceOf(DuplicateUserException.class);
	}

	@Test
	void findByIdThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(repository.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(id)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void updateRejectsEmailUsedByAnotherUser() {
		UUID id = UUID.randomUUID();
		when(repository.findByPublicId(id)).thenReturn(Optional.of(new User("A", "a@mail.com", "52998224725", "old-hash", BigDecimal.ZERO, BigDecimal.ZERO)));
		when(repository.existsByEmailAndPublicIdNot("b@mail.com", id)).thenReturn(true);

		assertThatThrownBy(() -> service.update(id, new UpdateUserRequest("A", "B@mail.com", null)))
				.isInstanceOf(DuplicateUserException.class);
	}

	@Test
	void deleteThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(repository.existsByPublicId(id)).thenReturn(false);

		assertThatThrownBy(() -> service.delete(id)).isInstanceOf(UserNotFoundException.class);
		verify(repository, never()).deleteByPublicId(any());
	}

}
