package com.inter.remittance.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentConstraintValidator implements ConstraintValidator<ValidDocument, String> {
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.isBlank() || Documents.isValid(value);
	}
}
