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

	@InjectMocks
	UserService service;

	private CreateUserRequest request() {
		return new CreateUserRequest(" Maria Silva ", "Maria@Mail.COM", "529.982.247-25", null, new BigDecimal("10.50"));
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
		when(repository.findByPublicId(id)).thenReturn(Optional.of(new User("A", "a@mail.com", "52998224725", BigDecimal.ZERO, BigDecimal.ZERO)));
		when(repository.existsByEmailAndPublicIdNot("b@mail.com", id)).thenReturn(true);

		assertThatThrownBy(() -> service.update(id, new UpdateUserRequest("A", "B@mail.com")))
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
