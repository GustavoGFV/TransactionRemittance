package com.inter.remittance.remittance.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import java.util.List;
import com.inter.remittance.remittance.model.Remittance;
import com.inter.remittance.remittance.service.RemittanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController 
@RequestMapping("/remittance")
@Tag(name = "Remittance", description = "Operations related to remittance")
public class RemittanceController {

    private final RemittanceService remittanceService;

    public RemittanceController(RemittanceService remittanceService) {
        this.remittanceService = remittanceService;
    }

    
    @GetMapping
    @Operation(summary = "Get all remittances",
            description = "Retrieve a list of all remittances. Placeholder: returns an empty list until the remittance flow is implemented")
    @ApiResponse(responseCode = "200", description = "List of remittances (currently empty)")
    public ResponseEntity<List<Remittance>> getAllRemittances() {
        return ResponseEntity.ok(remittanceService.getAllRemittances());
    }   
    
}
