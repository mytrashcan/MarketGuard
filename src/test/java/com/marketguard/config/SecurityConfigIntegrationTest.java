package com.marketguard.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "marketguard.security.enabled=true",
        "marketguard.security.username=operator",
        "marketguard.security.password=a-secure-password",
        "marketguard.security.allowed-origins[0]=https://trusted.example",
        "marketguard.security.requests-per-minute=120"
})
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectsDashboardApiAndNonHealthActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"marketguard\""));
        mockMvc.perform(get("/api/anomalies"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/anomalies").with(httpBasic("operator", "a-secure-password")))
                .andExpect(status().isOk());
    }

    @Test
    void exposesOnlyHealthProbesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnsafeRequestsWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/anomalies")
                        .with(httpBasic("operator", "a-secure-password")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/cases/1/status")
                        .with(httpBasic("operator", "a-secure-password"))
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/cases/1/status")
                        .with(httpBasic("operator", "a-secure-password"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUntrustedWebSocketOrigin() throws Exception {
        mockMvc.perform(get("/ws/info")
                        .header("Origin", "https://evil.example")
                        .with(httpBasic("operator", "a-secure-password")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/ws/info")
                        .header("Origin", "https://trusted.example")
                        .with(httpBasic("operator", "a-secure-password")))
                .andExpect(status().isOk());
    }
}
