package com.inter.remittance.remittance.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.inter.remittance.remittance.dto.BalanceResponse;
import com.inter.remittance.user.model.User;

@Mapper
public interface RemittanceMapper {

	@Mapping(source = "publicId", target = "userId")
	BalanceResponse toBalance(User user);

}
