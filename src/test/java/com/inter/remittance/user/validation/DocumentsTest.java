package com.inter.remittance.user.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.inter.remittance.user.model.DocumentType;

class DocumentsTest {

	@ParameterizedTest
	@ValueSource(strings = { "529.982.247-25", "52998224725", "111.444.777-35" })
	void acceptsValidCpf(String cpf) {
		assertThat(Documents.isValid(cpf)).isTrue();
		assertThat(Documents.typeOf(cpf)).isEqualTo(DocumentType.CPF);
	}

	@ParameterizedTest
	@ValueSource(strings = { "11.222.333/0001-81", "11222333000181", "12ABC34501DE35" })
	void acceptsValidCnpjIncludingAlphanumeric(String cnpj) {
		assertThat(Documents.isValid(cnpj)).isTrue();
		assertThat(Documents.typeOf(cnpj)).isEqualTo(DocumentType.CNPJ);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "123", "529.982.247-24", "111.111.111-11", "000.000.000-00",
			"11.222.333/0001-82", "11111111111111", "ABCDEFGHIJK", "1234567890123456" })
	void rejectsInvalidDocuments(String document) {
		assertThat(Documents.isValid(document)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "12ABC34501DEAB", "12ABC34501DE3A", "12ABC34501DEA5", "1234567890A" })
	void rejectsLettersInCheckDigitPositions(String document) {
		assertThat(Documents.isValid(document)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "12ABC34501DE36", "12ABC34501DE45", "12ABD34501DE35", "13ABC34501DE35" })
	void rejectsAlphanumericCnpjWithWrongCheckDigitOrTamperedBody(String cnpj) {
		assertThat(Documents.isValid(cnpj)).isFalse();
	}

	@Test
	void alphanumericCnpjIsCaseInsensitiveAndAcceptsPunctuation() {
		assertThat(Documents.isValid("12.abc.345/01de-35")).isTrue();
	}

	@Test
	void normalizeRemovesPunctuationAndUpperCases() {
		assertThat(Documents.normalize("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
	}

}
