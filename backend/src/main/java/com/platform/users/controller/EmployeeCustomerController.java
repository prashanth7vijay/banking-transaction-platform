package com.platform.users.controller;

import com.platform.shared.dto.PageResponse;
import com.platform.users.dto.CreateCustomerRequest;
import com.platform.users.dto.UserResponse;
import com.platform.users.mapper.UserMapper;
import com.platform.users.service.CustomerDirectoryService;
import com.platform.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employee/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeCustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerDirectoryService customerDirectoryService;
    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping(params = "!page")
    public List<UserResponse> search(@RequestParam(required = false) String search) {
        return customerDirectoryService.searchCustomers(search)
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @GetMapping(params = "page")
    public PageResponse<UserResponse> searchPaged(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        return PageResponse.from(
                customerDirectoryService.searchCustomersPaged(
                        search,
                        PageRequest.of(safePage, safeSize)
                ).map(userMapper::toResponse)
        );
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        var customer = userService.createCustomer(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userMapper.toResponse(customer));
    }
}