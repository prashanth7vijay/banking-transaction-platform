package com.platform.users.service;

import com.platform.users.domain.RoleName;
import com.platform.users.domain.User;
import com.platform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lets an employee find another employee to assign/reassign an exception case
 * to (Feature 2: Exception &amp; Investigation Management). Same shape as
 * {@link CustomerDirectoryService}, scoped to EMPLOYEE-role users instead of
 * CUSTOMER - deliberately not ADMIN too, matching this codebase's existing
 * flat-role convention rather than assuming ADMIN should appear in an
 * operational assignment list.
 */
@Service
@RequiredArgsConstructor
public class EmployeeDirectoryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<User> searchEmployees(String search) {
        return userRepository.searchByRole(RoleName.EMPLOYEE, search);
    }
}
