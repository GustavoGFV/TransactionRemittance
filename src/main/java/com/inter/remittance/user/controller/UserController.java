package com.inter.remittance.user.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.inter.remittance.user.dto.CreateUserRequest;
import com.inter.remittance.user.dto.PageResponse;
import com.inter.remittance.user.dto.UpdateUserRequest;
import com.inter.remittance.user.dto.UserResponse;
import com.inter.remittance.user.mapper.UserMapper;
import com.inter.remittance.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
@Tag(name = "User", description = "User management (CPF/CNPJ) with BRL and USD balances")
public class UserController {

	private final UserService userService;
	private final UserMapper mapper;

	public UserController(UserService userService, UserMapper mapper) {
		this.userService = userService;
		this.mapper = mapper;
	}

	@PostMapping
	@Operation(summary = "Create a user", description = "CPF/CNPJ and email must be valid and unique")
	public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		UserResponse created = mapper.toResponse(userService.create(request));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a user by id")
	public UserResponse findById(@PathVariable UUID id) {
		return mapper.toResponse(userService.findById(id));
	}

	@GetMapping
	@Operation(summary = "List users (paginated)")
	public PageResponse<UserResponse> findAll(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
		return PageResponse.from(userService.findAll(pageable), mapper::toResponse);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update name and email", description = "The document is immutable and balances change only through transactions")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
		return mapper.toResponse(userService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a user")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		userService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
