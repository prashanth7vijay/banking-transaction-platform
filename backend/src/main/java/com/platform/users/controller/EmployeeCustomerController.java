package com.platform.users.controller;

import com.platform.users.dto.UserResponse;
import com.platform.users.mapper.UserMapper;
import com.platform.users.service.CustomerDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employee/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeCustomerController {

    private final CustomerDirectoryService customerDirectoryService;
    private final UserMapper userMapper;

    @GetMapping
    public List<UserResponse> search(@RequestParam(required = false) String search) {
        return customerDirectoryService.searchCustomers(search).stream().map(userMapper::toResponse).toList();
    }
}
