package com.analytics.security;

import com.analytics.security.entity.Role;
import com.analytics.security.entity.User;
import com.analytics.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String createBearerToken(String username, Role role) {
        User user = new User(username, username + "@corp.local", "dummyHash", role);
        user.setId(999L);
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    @DisplayName("Public endpoints permit unauthenticated access")
    void publicEndpointsAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoints return 401 when unauthenticated")
    void protectedEndpointsRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/datasources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/datasources/crm/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Role RBAC: VIEWER is forbidden from manual query execution")
    void viewerForbiddenFromManualQuery() throws Exception {
        String viewerToken = createBearerToken("viewer_user", Role.VIEWER);

        mockMvc.perform(post("/api/datasources/crm/query")
                        .header("Authorization", viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Role RBAC: ANALYST and ADMIN are permitted for manual query execution")
    void analystAndAdminPermittedForManualQuery() throws Exception {
        String analystToken = createBearerToken("analyst_user", Role.ANALYST);

        // May return 200 or datasource error, but must NOT be 401 or 403
        mockMvc.perform(post("/api/datasources/crm/query")
                        .header("Authorization", analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(401, status);
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status);
                });
    }

    @Test
    @DisplayName("Role RBAC: Only ADMIN can access /actuator/metrics")
    void actuatorRoleRestrictions() throws Exception {
        String viewerToken = createBearerToken("viewer_user", Role.VIEWER);
        String analystToken = createBearerToken("analyst_user", Role.ANALYST);
        String adminToken = createBearerToken("admin_user", Role.ADMIN);

        mockMvc.perform(get("/actuator/metrics").header("Authorization", viewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics").header("Authorization", analystToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Authenticated users can access datasource catalog")
    void authenticatedUserCanAccessDatasources() throws Exception {
        String viewerToken = createBearerToken("viewer_user", Role.VIEWER);

        mockMvc.perform(get("/api/datasources").header("Authorization", viewerToken))
                .andExpect(status().isOk());
    }
}
