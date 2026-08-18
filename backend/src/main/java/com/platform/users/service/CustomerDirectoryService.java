package com.platform.users.service;

import com.platform.users.domain.RoleName;
import com.platform.users.domain.User;
import com.platform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lets an employee find a customer to act on (e.g. open an account for them).
 * Deliberately scoped to CUSTOMER-role users only - an employee opening an
 * account has no legitimate reason to search for other employees or admins.
 */
@Service
@RequiredArgsConstructor
public class CustomerDirectoryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<User> searchCustomers(String search) {
        return userRepository.searchByRole(RoleName.CUSTOMER, search);
    }
}
