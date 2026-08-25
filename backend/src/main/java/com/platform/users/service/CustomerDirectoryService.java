package com.platform.users.service;

import com.platform.users.domain.RoleName;
import com.platform.users.domain.User;
import com.platform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerDirectoryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<User> searchCustomers(String search) {
        return userRepository.searchByRole(RoleName.CUSTOMER, search);
    }

    @Transactional(readOnly = true)
    public Page<User> searchCustomersPaged(String search, Pageable pageable) {
        return userRepository.searchByRolePaged(RoleName.CUSTOMER, search, pageable);
    }
}