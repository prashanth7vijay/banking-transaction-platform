package com.platform.security;

import com.platform.auth.service.JwtService;
import com.platform.auth.service.TokenDenylistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deferred from Phase 6 to here, as planned. Covers a representative endpoint from
 * every module rather than the literal full endpoint surface - each assertion
 * checks the security *gate* (401/403), not what the business logic does
 * afterward, since a correct-role request may still legitimately 404 downstream
 * (e.g. approving a transaction ID that doesn't exist) without that being an RBAC
 * failure.
 * <p>
 * {@link TokenDenylistService} is mocked (not a real Redis) since this suite only
 * needs valid, freshly-issued tokens - it never exercises logout/denylist behavior,
 * which is covered separately.
 * <p>
 * Each MockMvc call gets a freshly-built request via a {@link Supplier}, since
 * {@link MockHttpServletRequestBuilder} mutates itself on `.header(...)` and is not
 * safe to reuse across multiple {@code perform()} calls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RbacMatrixIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("platform_test")
            .withUsername("platform")
            .withPassword("platform");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private TokenDenylistService tokenDenylistService;

    @Test
    void usersMeRequiresAnyAuthenticatedRoleButNotAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        int status = mockMvc.perform(authed(() -> get("/api/v1/users/me"), "CUSTOMER"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void accountsRequiresCustomerRole() throws Exception {
        assertRbac(() -> get("/api/v1/accounts"), "CUSTOMER", "EMPLOYEE");
    }

    @Test
    void pendingTransactionsRequiresEmployeeRole() throws Exception {
        assertRbac(() -> get("/api/v1/transactions/pending"), "EMPLOYEE", "CUSTOMER");
    }

    @Test
    void approveTransactionRequiresEmployeeRole() throws Exception {
        String path = "/api/v1/transactions/" + UUID.randomUUID() + "/approve";
        assertRbac(() -> post(path), "EMPLOYEE", "CUSTOMER");
    }

    @Test
    void adminUsersRequiresAdminRole() throws Exception {
        assertRbac(() -> get("/api/v1/admin/users"), "ADMIN", "EMPLOYEE");
    }

    @Test
    void auditLogsRequiresAdminRole() throws Exception {
        assertRbac(() -> get("/api/v1/audit/logs"), "ADMIN", "CUSTOMER");
    }

    /**
     * Asserts the standard three-way matrix for an endpoint gated to `requiredRole`:
     * no token -> 401, wrong role -> 403, correct role -> anything but 401/403.
     */
    private void assertRbac(Supplier<MockHttpServletRequestBuilder> requestSupplier, String requiredRole, String wrongRole) throws Exception {
        mockMvc.perform(requestSupplier.get())
                .andExpect(status().isUnauthorized());

        mockMvc.perform(authed(requestSupplier, wrongRole))
                .andExpect(status().isForbidden());

        int status = mockMvc.perform(authed(requestSupplier, requiredRole))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("correct-role request should pass the RBAC gate").isNotIn(401, 403);
    }

    private MockHttpServletRequestBuilder authed(Supplier<MockHttpServletRequestBuilder> requestSupplier, String role) {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@platform.local", Set.of(role)).token();
        return requestSupplier.get().header("Authorization", "Bearer " + token);
    }
}
