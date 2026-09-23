package com.platform.users.repository;

import com.platform.users.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Batch name resolution for display purposes (e.g. Customer 360's exception
     * assignee names). Deliberately projects only id + firstName instead of
     * returning {@code User} - callers here never need roles, email, or any
     * other field, and {@code User.roles} is an EAGER @ManyToMany that would
     * otherwise pull in a secondary per-user query for no reason. One IN-clause
     * query for however many ids are passed, instead of one {@code findById}
     * per id.
     */
    @Query("SELECT u.id AS id, u.firstName AS firstName FROM User u WHERE u.id IN :ids")
    List<UserIdName> findFirstNamesByIdIn(@Param("ids") Collection<UUID> ids);

    interface UserIdName {
        UUID getId();
        String getFirstName();
    }

    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR :search = ''
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.createdAt DESC
            """)
    List<User> search(@Param("search") String search);

    @Query("""
            SELECT DISTINCT u FROM User u JOIN u.roles r
            WHERE r.name = :roleName
              AND (:search IS NULL OR :search = ''
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.createdAt DESC
            """)
    List<User> searchByRole(@Param("roleName") String roleName, @Param("search") String search);
    
    @Query(value = """
            SELECT DISTINCT u FROM User u JOIN u.roles r
            WHERE r.name = :roleName
              AND (:search IS NULL OR :search = ''
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r
            WHERE r.name = :roleName
              AND (:search IS NULL OR :search = ''
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> searchByRolePaged(@Param("roleName") String roleName, @Param("search") String search, Pageable pageable);

    @Query("SELECT r.name AS roleName, COUNT(u) AS userCount FROM User u JOIN u.roles r GROUP BY r.name")
    List<RoleCount> countUsersByRole();

    interface RoleCount {
        String getRoleName();
        Long getUserCount();
    }
}