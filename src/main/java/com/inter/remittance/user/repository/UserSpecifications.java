package com.inter.remittance.user.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.inter.remittance.user.dto.UserFilter;
import com.inter.remittance.user.model.DocumentType;
import com.inter.remittance.user.model.User;

public final class UserSpecifications {

	private static final int CPF_LENGTH = 11;

	private UserSpecifications() {
	}

	public static Specification<User> from(UserFilter filter) {
		return Specification.allOf(
				nameContains(filter.name()),
				emailContains(filter.email()),
				ofType(filter.documentType()));
	}

	private static Specification<User> nameContains(String name) {
		return contains("name", name);
	}

	private static Specification<User> emailContains(String email) {
		return contains("email", email);
	}

	private static Specification<User> contains(String attribute, String value) {
		if (!StringUtils.hasText(value)) {
			return Specification.unrestricted();
		}
		String pattern = "%" + escape(value.strip().toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.like(cb.lower(root.get(attribute)), pattern, '\\');
	}

	private static Specification<User> ofType(DocumentType type) {
		if (type == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> type == DocumentType.CPF
				? cb.equal(cb.length(root.get("document")), CPF_LENGTH)
				: cb.notEqual(cb.length(root.get("document")), CPF_LENGTH);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
