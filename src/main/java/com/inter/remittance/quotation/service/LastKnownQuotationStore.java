package com.inter.remittance.quotation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.inter.remittance.quotation.dto.BuyQuotation;

/**
 * Keeps the most recent quotation successfully obtained from the BCB (stale-if-error).
 * When the BCB is unavailable the service falls back to it, as long as it is younger than the
 * configured maximum age, which matches the challenge rule of using the last quotation obtained.
 */
@Component
public class LastKnownQuotationStore {

	private record Entry(List<BuyQuotation> quotations, Instant storedAt) {
	}

	private final AtomicReference<Entry> latest = new AtomicReference<>();
	private final Duration maxAge;
	private final Clock clock;

	public LastKnownQuotationStore(@Value("${app.cache.quotations.stale-max-age}") Duration maxAge, Clock clock) {
		this.maxAge = maxAge;
		this.clock = clock;
	}

	public void remember(List<BuyQuotation> quotations) {
		latest.set(new Entry(quotations, clock.instant()));
	}

	public void clear() {
		latest.set(null);
	}

	public Optional<List<BuyQuotation>> recall() {
		return Optional.ofNullable(latest.get())
				.filter(entry -> Duration.between(entry.storedAt(), clock.instant()).compareTo(maxAge) <= 0)
				.map(Entry::quotations);
	}

}
