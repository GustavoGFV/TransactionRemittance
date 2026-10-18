package com.inter.remittance;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates documents with valid check digits, so integration tests can create as many users as they need.
 */
final class DocumentGenerator {

	private DocumentGenerator() {
	}

	static String cpf() {
		int[] digits = randomDigits(9);
		int first = checkDigit(digits, 9, 10);
		int second = checkDigit(append(digits, first), 10, 11);
		return format(digits, first, second);
	}

	static String cnpj() {
		int[] digits = randomDigits(12);
		int first = cnpjCheckDigit(digits, new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
		int second = cnpjCheckDigit(append(digits, first), new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
		return format(digits, first, second);
	}

	private static int checkDigit(int[] digits, int length, int startWeight) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += digits[i] * (startWeight - i);
		}
		int remainder = (sum * 10) % 11;
		return remainder == 10 ? 0 : remainder;
	}

	private static int cnpjCheckDigit(int[] digits, int[] weights) {
		int sum = 0;
		for (int i = 0; i < weights.length; i++) {
			sum += digits[i] * weights[i];
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}

	private static int[] randomDigits(int length) {
		int[] digits = new int[length];
		for (int i = 0; i < length; i++) {
			digits[i] = ThreadLocalRandom.current().nextInt(10);
		}
		return digits;
	}

	private static int[] append(int[] digits, int digit) {
		int[] result = java.util.Arrays.copyOf(digits, digits.length + 1);
		result[digits.length] = digit;
		return result;
	}

	private static String format(int[] digits, int first, int second) {
		StringBuilder builder = new StringBuilder();
		for (int digit : digits) {
			builder.append(digit);
		}
		return builder.append(first).append(second).toString();
	}

}
