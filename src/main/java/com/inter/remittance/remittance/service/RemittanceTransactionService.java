package com.inter.remittance.remittance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.inter.remittance.remittance.dto.BalanceResponse;
import com.inter.remittance.remittance.dto.BuyRemittanceRequest;
import com.inter.remittance.remittance.dto.RemittanceResponse;
import com.inter.remittance.remittance.dto.RemittanceType;
import com.inter.remittance.remittance.dto.ReverseRemittanceRequest;
import com.inter.remittance.remittance.exception.InvalidRemittanceException;
import com.inter.remittance.remittance.exception.RecipientNotFoundException;
import com.inter.remittance.remittance.mapper.RemittanceMapper;
import com.inter.remittance.remittance.model.DailyLimitRule;
import com.inter.remittance.remittance.model.UserDailyTransaction;
import com.inter.remittance.remittance.repository.DailyLimitRuleRepository;
import com.inter.remittance.remittance.repository.UserDailyTransactionRepository;
import com.inter.remittance.user.exception.UserNotFoundException;
import com.inter.remittance.user.model.User;
import com.inter.remittance.user.repository.UserRepository;
import com.inter.remittance.user.validation.Documents;

/**
 * Database part of a remittance. Every public method is one ACID transaction: any failure
 * (insufficient balance, daily limit, missing user) rolls back all balance and limit changes.
 * The quotation is fetched beforehand by {@link RemittanceService} so no row lock or connection
 * is held while waiting for the BCB API.
 */
@Service
public class RemittanceTransactionService {

	private static final int MONEY_SCALE = 2;

	private final UserRepository userRepository;
	private final DailyLimitRuleRepository ruleRepository;
	private final UserDailyTransactionRepository dailyTransactionRepository;
	private final RemittanceMapper mapper;
	private final Clock clock;

	public RemittanceTransactionService(UserRepository userRepository, DailyLimitRuleRepository ruleRepository,
			UserDailyTransactionRepository dailyTransactionRepository, RemittanceMapper mapper, Clock clock) {
		this.userRepository = userRepository;
		this.ruleRepository = ruleRepository;
		this.dailyTransactionRepository = dailyTransactionRepository;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public BalanceResponse balance(UUID userId) {
		return mapper.toBalance(userRepository.findByPublicId(userId).orElseThrow(() -> new UserNotFoundException(userId)));
	}

	@Transactional(readOnly = true)
	public void requireUser(UUID userId) {
		if (!userRepository.existsByPublicId(userId)) {
			throw new UserNotFoundException(userId);
		}
	}

	/**
	 * Debits the sender's BRL and credits USD to the beneficiary. 
	 * Every purchase, same-account or external, counts toward the sender's daily limit.
	 * Rows are locked in id order, which prevents deadlocks between concurrent transfers in opposite directions.
	 */
	@Transactional
	public RemittanceResponse buy(BuyRemittanceRequest request, BigDecimal quotation) {
		boolean sameAccount = request.isSameAccount();
		UUID recipientId = sameAccount ? request.userId() : resolveRecipient(request);

		Map<UUID, User> users = lock(Set.copyOf(List.of(request.userId(), recipientId)));
		User sender = find(users, request.userId());
		User beneficiary = find(users, recipientId);

		BigDecimal amountBrl = request.amountBrl();
		BigDecimal amountUsd = amountBrl.divide(quotation, MONEY_SCALE, RoundingMode.DOWN);
		if (amountUsd.signum() == 0) {
			throw new InvalidRemittanceException("Amount is too small to buy at least US$ 0.01");
		}

		sender.debitBrl(amountBrl);
		beneficiary.creditUsd(amountUsd);
		UserDailyTransaction daily = registerDailyUsage(sender, amountBrl);

		return new RemittanceResponse(RemittanceType.BUY, sender.getPublicId(), beneficiary.getPublicId(),
				amountBrl, amountUsd, quotation, sender.getBalanceBrl(), sender.getBalanceUsd(),
				daily.getReachedAmount(), daily.getRule().getMaxAmount());
	}

	/**
	 * Converts USD back to BRL inside the user's own account. 
	 * The BRL amount is rounded down, so rounding never benefits the customer.
	 * It does not count toward the daily limit.
	 */
	@Transactional
	public RemittanceResponse reverse(ReverseRemittanceRequest request, BigDecimal quotation) {
		User user = find(lock(Set.of(request.userId())), request.userId());

		BigDecimal amountUsd = request.amountUsd();
		BigDecimal amountBrl = amountUsd.multiply(quotation).setScale(MONEY_SCALE, RoundingMode.DOWN);
		if (amountBrl.signum() == 0) {
			throw new InvalidRemittanceException("Amount is too small to receive at least R$ 0.01");
		}

		user.debitUsd(amountUsd);
		user.creditBrl(amountBrl);

		return new RemittanceResponse(RemittanceType.REVERSE, user.getPublicId(), user.getPublicId(),
				amountBrl, amountUsd, quotation, user.getBalanceBrl(), user.getBalanceUsd(), null, null);
	}

	/**
	 * Resolves the user that owns the recipient document.
	 * A recipient equal to the sender is rejected, since that scenario is covered by isSameAccount=true
	 * (the daily limit applies to both).
	 */
	private UUID resolveRecipient(BuyRemittanceRequest request) {
		if (request.recipientDocument() == null || request.recipientDocument().isBlank()) {
			throw new InvalidRemittanceException("recipientDocument is required when isSameAccount is false");
		}
		User recipient = userRepository.findByDocument(Documents.normalize(request.recipientDocument()))
				.orElseThrow(RecipientNotFoundException::new);
		if (recipient.getPublicId().equals(request.userId())) {
			throw new InvalidRemittanceException("The recipient is the sender; use isSameAccount=true");
		}
		return recipient.getPublicId();
	}

	/**
	 * Adds the amount to today's counter of the sender (Brasília date), creating the row on first use.
	 * The sender row is already locked at this point, which serializes concurrent transfers and keeps the limit check consistent.
	 */
	private UserDailyTransaction registerDailyUsage(User sender, BigDecimal amountBrl) {
		LocalDate today = LocalDate.now(clock);
		UserDailyTransaction daily = dailyTransactionRepository
				.findByUserIdAndTransactionDate(sender.getPublicId(), today)
				.orElseGet(() -> new UserDailyTransaction(sender.getPublicId(), ruleFor(sender), today));
		daily.register(amountBrl);
		return dailyTransactionRepository.save(daily);
	}

	private DailyLimitRule ruleFor(User sender) {
		return ruleRepository.findByDocumentType(sender.getDocumentType())
				.orElseThrow(() -> new IllegalStateException("No daily limit rule for " + sender.getDocumentType()));
	}

	private Map<UUID, User> lock(Set<UUID> ids) {
		return userRepository.lockAllByPublicIdIn(ids).stream()
				.collect(Collectors.toMap(user -> user.getPublicId(), Function.identity()));
	}

	private static User find(Map<UUID, User> users, UUID id) {
		User user = users.get(id);
		if (user == null) {
			throw new UserNotFoundException(id);
		}
		return user;
	}

}
