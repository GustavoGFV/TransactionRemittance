package com.inter.remittance.remittance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inter.remittance.remittance.dto.BuyRemittanceRequest;
import com.inter.remittance.remittance.dto.RemittanceResponse;
import com.inter.remittance.remittance.dto.ReverseRemittanceRequest;
import com.inter.remittance.remittance.exception.DailyLimitExceededException;
import com.inter.remittance.remittance.exception.InvalidRemittanceException;
import com.inter.remittance.remittance.exception.RecipientNotFoundException;
import com.inter.remittance.remittance.mapper.RemittanceMapper;
import com.inter.remittance.remittance.model.DailyLimitRule;
import com.inter.remittance.remittance.model.UserDailyTransaction;
import com.inter.remittance.remittance.repository.DailyLimitRuleRepository;
import com.inter.remittance.remittance.repository.UserDailyTransactionRepository;
import com.inter.remittance.user.exception.InsufficientBalanceException;
import com.inter.remittance.user.exception.UserNotFoundException;
import com.inter.remittance.user.model.DocumentType;
import com.inter.remittance.user.model.User;
import com.inter.remittance.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RemittanceTransactionServiceTest {

	private static final String CPF = "52998224725";
	private static final String CNPJ = "11222333000181";
	private static final BigDecimal RATE = new BigDecimal("5.00");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

	@Mock
	UserRepository userRepository;
	@Mock
	DailyLimitRuleRepository ruleRepository;
	@Mock
	UserDailyTransactionRepository dailyRepository;

	RemittanceTransactionService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
		service = new RemittanceTransactionService(userRepository, ruleRepository, dailyRepository,
				mock(RemittanceMapper.class), clock);
	}

	@Test
	void buyOnSameAccountConvertsAndAlsoCountsTowardDailyLimit() {
		User sender = user(CPF, "1000.00", "0.00");
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(sender));
		when(dailyRepository.findByUserIdAndTransactionDate(sender.getPublicId(), TODAY)).thenReturn(Optional.empty());
		when(ruleRepository.findByDocumentType(DocumentType.CPF)).thenReturn(Optional.of(rule(DocumentType.CPF, "10000.00")));
		when(dailyRepository.save(any(UserDailyTransaction.class))).thenAnswer(i -> i.getArgument(0));

		RemittanceResponse response = service.buy(buy(sender, "500.00", true, null), RATE);

		assertThat(response.amountUsd()).isEqualByComparingTo("100.00");
		assertThat(sender.getBalanceBrl()).isEqualByComparingTo("500.00");
		assertThat(sender.getBalanceUsd()).isEqualByComparingTo("100.00");
		assertThat(response.dailyReachedAmount()).isEqualByComparingTo("500.00");
		assertThat(response.dailyLimit()).isEqualByComparingTo("10000.00");
	}

	@Test
	void externalBuyCreditsRecipientAndAccumulatesDailyUsage() {
		User sender = user(CPF, "1000.00", "0.00");
		User recipient = user(CNPJ, "0.00", "10.00");
		when(userRepository.findByDocument(CNPJ)).thenReturn(Optional.of(recipient));
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(sender, recipient));
		when(dailyRepository.findByUserIdAndTransactionDate(sender.getPublicId(), TODAY)).thenReturn(Optional.empty());
		when(ruleRepository.findByDocumentType(DocumentType.CPF)).thenReturn(Optional.of(rule(DocumentType.CPF, "10000.00")));
		when(dailyRepository.save(any(UserDailyTransaction.class))).thenAnswer(i -> i.getArgument(0));

		RemittanceResponse response = service.buy(buy(sender, "500.00", false, "11.222.333/0001-81"), RATE);

		assertThat(sender.getBalanceBrl()).isEqualByComparingTo("500.00");
		assertThat(sender.getBalanceUsd()).isEqualByComparingTo("0.00");
		assertThat(recipient.getBalanceUsd()).isEqualByComparingTo("110.00");
		assertThat(response.beneficiaryId()).isEqualTo(recipient.getPublicId());
		assertThat(response.dailyReachedAmount()).isEqualByComparingTo("500.00");
		assertThat(response.dailyLimit()).isEqualByComparingTo("10000.00");
	}

	@Test
	void externalBuyAboveDailyLimitIsRejected() {
		User sender = user(CPF, "20000.00", "0.00");
		User recipient = user(CNPJ, "0.00", "0.00");
		UserDailyTransaction used = new UserDailyTransaction(sender.getPublicId(), rule(DocumentType.CPF, "10000.00"), TODAY);
		used.register(new BigDecimal("9500.00"));
		when(userRepository.findByDocument(CNPJ)).thenReturn(Optional.of(recipient));
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(sender, recipient));
		when(dailyRepository.findByUserIdAndTransactionDate(sender.getPublicId(), TODAY)).thenReturn(Optional.of(used));

		assertThatThrownBy(() -> service.buy(buy(sender, "500.01", false, CNPJ), RATE))
				.isInstanceOf(DailyLimitExceededException.class);
		// The in-memory debit is discarded by the transaction rollback (covered in RemittanceFlowIT)
		assertThat(used.getReachedAmount()).isEqualByComparingTo("9500.00");
		verify(dailyRepository, never()).save(any());
	}

	@Test
	void buyWithInsufficientBrlBalanceIsRejected() {
		User sender = user(CPF, "10.00", "0.00");
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(sender));

		assertThatThrownBy(() -> service.buy(buy(sender, "500.00", true, null), RATE))
				.isInstanceOf(InsufficientBalanceException.class);
		assertThat(sender.getBalanceUsd()).isEqualByComparingTo("0.00");
	}

	@Test
	void externalBuyRequiresRecipientDocument() {
		User sender = user(CPF, "1000.00", "0.00");

		assertThatThrownBy(() -> service.buy(buy(sender, "10.00", false, null), RATE))
				.isInstanceOf(InvalidRemittanceException.class);
	}

	@Test
	void externalBuyToUnknownRecipientIsRejected() {
		User sender = user(CPF, "1000.00", "0.00");
		when(userRepository.findByDocument(CNPJ)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.buy(buy(sender, "10.00", false, CNPJ), RATE))
				.isInstanceOf(RecipientNotFoundException.class);
	}

	@Test
	void externalBuyToSelfIsRejected() {
		User sender = user(CPF, "1000.00", "0.00");
		when(userRepository.findByDocument(CPF)).thenReturn(Optional.of(sender));

		assertThatThrownBy(() -> service.buy(buy(sender, "10.00", false, CPF), RATE))
				.isInstanceOf(InvalidRemittanceException.class);
	}

	@Test
	void buyOfUnknownSenderIsRejected() {
		UUID id = UUID.randomUUID();
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of());

		assertThatThrownBy(() -> service.buy(new BuyRemittanceRequest(id, new BigDecimal("10.00"), true, null), RATE))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void buyRoundsUsdDown() {
		User sender = user(CPF, "100.00", "0.00");
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(sender));
		when(dailyRepository.findByUserIdAndTransactionDate(sender.getPublicId(), TODAY)).thenReturn(Optional.empty());
		when(ruleRepository.findByDocumentType(DocumentType.CPF)).thenReturn(Optional.of(rule(DocumentType.CPF, "10000.00")));
		when(dailyRepository.save(any(UserDailyTransaction.class))).thenAnswer(i -> i.getArgument(0));

		RemittanceResponse response = service.buy(buy(sender, "10.00", true, null), new BigDecimal("5.2073"));

		assertThat(response.amountUsd()).isEqualByComparingTo("1.92");
	}

	@Test
	void reverseConvertsUsdToBrl() {
		User user = user(CPF, "0.00", "100.00");
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(user));

		RemittanceResponse response = service.reverse(new ReverseRemittanceRequest(user.getPublicId(), new BigDecimal("40.00")),
				new BigDecimal("5.2073"));

		assertThat(response.amountBrl()).isEqualByComparingTo("208.29");
		assertThat(user.getBalanceUsd()).isEqualByComparingTo("60.00");
		assertThat(user.getBalanceBrl()).isEqualByComparingTo("208.29");
	}

	@Test
	void reverseWithInsufficientUsdBalanceIsRejected() {
		User user = user(CPF, "0.00", "10.00");
		when(userRepository.lockAllByPublicIdIn(any())).thenReturn(List.of(user));

		assertThatThrownBy(() -> service.reverse(new ReverseRemittanceRequest(user.getPublicId(), new BigDecimal("40.00")), RATE))
				.isInstanceOf(InsufficientBalanceException.class);
	}

	private static BuyRemittanceRequest buy(User sender, String amount, boolean sameAccount, String recipient) {
		return new BuyRemittanceRequest(sender.getPublicId(), new BigDecimal(amount), sameAccount, recipient);
	}

	private static User user(String document, String brl, String usd) {
		return new User("Test", document + "@mail.com", document, "hash", new BigDecimal(brl), new BigDecimal(usd));
	}

	private static DailyLimitRule rule(DocumentType type, String max) {
		return new DailyLimitRule(type, new BigDecimal(max));
	}

}
