package com.inter.remittance.user.dto;

// Whitelist of sortable properties, so clients cannot sort by arbitrary entity attributes
public enum UserSortField {
	name("name"),
	email("email"),
	createdAt("createdAt");

	private final String property;

	UserSortField(String property) {
		this.property = property;
	}

	public String property() {
		return property;
	}
}
