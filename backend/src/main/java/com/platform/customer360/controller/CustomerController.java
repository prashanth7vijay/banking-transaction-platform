package com.platform.customer360.controller;

import com.platform.customer360.dto.Customer360Response;
import com.platform.customer360.service.Customer360Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Mounted under the existing {@code /employee/customers} path (where
 * {@code EmployeeCustomerController}'s search already lives) even though this
 * is implemented in a different Java module - the URL structure follows the
 * resource, not the package layout. EMPLOYEE-only, same as the rest of the
 * operations surface (approvals, exceptions, command center).
 */
@RestController
@RequestMapping("/api/v1/employee/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class CustomerController {

    private final Customer360Service customer360Service;

    @GetMapping("/{id}/360")
    public Customer360Response summary360(@PathVariable UUID id) {
        return customer360Service.getSummary(id);
    }
}
