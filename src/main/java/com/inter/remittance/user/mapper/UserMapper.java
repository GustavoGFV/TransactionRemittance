package com.inter.remittance.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.inter.remittance.user.dto.UserResponse;
import com.inter.remittance.user.model.User;

@Mapper
public interface UserMapper {

	@Mapping(source = "publicId", target = "id")
	UserResponse toResponse(User user);

}
