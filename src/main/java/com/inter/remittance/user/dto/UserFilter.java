package com.inter.remittance.user.dto;

import com.inter.remittance.user.model.DocumentType;

public record UserFilter(String name, String email, DocumentType documentType) {
}
