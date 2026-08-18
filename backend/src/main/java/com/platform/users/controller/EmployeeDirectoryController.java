package com.platform.users.controller;

import com.platform.users.dto.UserResponse;
import com.platform.users.mapper.UserMapper;
import com.platform.users.service.EmployeeDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Same shape as {@link EmployeeCustomerController}, scoped to employees instead of customers. */
@RestController
@RequestMapping("/api/v1/employee/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeDirectoryController {

    private final EmployeeDirectoryService employeeDirectoryService;
    private final UserMapper userMapper;

    @GetMapping
    public List<UserResponse> search(@RequestParam(required = false) String search) {
        return employeeDirectoryService.searchEmployees(search).stream().map(userMapper::toResponse).toList();
    }
}
