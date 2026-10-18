package com.inter.remittance.user.mapper;

import org.mapstruct.Mapper;

import com.inter.remittance.user.dto.UserResponse;
import com.inter.remittance.user.model.User;

@Mapper
public interface UserMapper {

	UserResponse toResponse(User user);

}
