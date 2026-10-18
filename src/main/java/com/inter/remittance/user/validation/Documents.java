package com.inter.remittance.user.validation;

import com.inter.remittance.user.model.DocumentType;

/**
 * CPF/CNPJ rules. CNPJ supports the alphanumeric format (digits and A-Z in the first 12
 * positions, numeric check digits), in effect since July 2026.
 */
public final class Documents {

	private static final int CPF_LENGTH = 11;
	private static final int CNPJ_LENGTH = 14;
	private static final int[] CNPJ_WEIGHTS = { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

	private Documents() {
	}

	/** Removes punctuation and upper-cases, so "123.456.789-09" and "12345678909" are the same document. */
	public static String normalize(String raw) {
		return raw == null ? null : raw.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
	}

	public static boolean isValid(String raw) {
		String document = normalize(raw);
		if (document == null) {
			return false;
		}
		return switch (document.length()) {
			case CPF_LENGTH -> isValidCpf(document);
			case CNPJ_LENGTH -> isValidCnpj(document);
			default -> false;
		};
	}

	public static DocumentType typeOf(String raw) {
		String document = normalize(raw);
		if (document == null) {
			return null;
		}
		return document.length() == CPF_LENGTH ? DocumentType.CPF : DocumentType.CNPJ;
	}

	private static boolean isValidCpf(String cpf) {
		if (!cpf.chars().allMatch(Character::isDigit) || allSame(cpf)) {
			return false;
		}
		int first = cpfCheckDigit(cpf, 9);
		int second = cpfCheckDigit(cpf, 10);
		return first == digit(cpf.charAt(9)) && second == digit(cpf.charAt(10));
	}

	private static int cpfCheckDigit(String cpf, int length) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += digit(cpf.charAt(i)) * (length + 1 - i);
		}
		int remainder = (sum * 10) % 11;
		return remainder == 10 ? 0 : remainder;
	}

	private static boolean isValidCnpj(String cnpj) {
		if (allSame(cnpj) || !Character.isDigit(cnpj.charAt(12)) || !Character.isDigit(cnpj.charAt(13))) {
			return false;
		}
		int first = cnpjCheckDigit(cnpj, 12);
		int second = cnpjCheckDigit(cnpj, 13);
		return first == digit(cnpj.charAt(12)) && second == digit(cnpj.charAt(13));
	}

	private static int cnpjCheckDigit(String cnpj, int length) {
		int sum = 0;
		int offset = CNPJ_WEIGHTS.length - length;
		for (int i = 0; i < length; i++) {
			sum += charValue(cnpj.charAt(i)) * CNPJ_WEIGHTS[offset + i];
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}

	// Alphanumeric CNPJ: value is the ASCII code minus 48 ('0'..'9' -> 0..9, 'A'..'Z' -> 17..42)
	private static int charValue(char c) {
		return c - '0';
	}

	private static int digit(char c) {
		return c - '0';
	}

	private static boolean allSame(String value) {
		return value.chars().distinct().count() == 1;
	}

}
