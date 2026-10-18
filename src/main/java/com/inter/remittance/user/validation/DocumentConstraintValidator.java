package com.inter.remittance.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentConstraintValidator implements ConstraintValidator<ValidDocument, String> {

	// Null/blank is left to @NotBlank so each rule reports its own message
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.isBlank() || Documents.isValid(value);
	}

}
