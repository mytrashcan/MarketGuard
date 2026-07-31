package com.marketguard.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "marketguard.security.enabled=false",
        "marketguard.security.operator-token=test-operator-token"
})
@AutoConfigureMockMvc
class SecurityWriteAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsAnonymousReadsButRequiresOperatorTokenForWrites() throws Exception {
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/cases/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cases/1/notes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"validation\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnInvalidOperatorToken() throws Exception {
        mockMvc.perform(patch("/api/cases/1/status")
                        .with(csrf())
                        .header(OperatorTokenFilter.HEADER_NAME, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void acceptsTheConfiguredOperatorToken() throws Exception {
        mockMvc.perform(patch("/api/cases/1/status")
                        .with(csrf())
                        .header(OperatorTokenFilter.HEADER_NAME, "test-operator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/cases/1/notes")
                        .with(csrf())
                        .header(OperatorTokenFilter.HEADER_NAME, "test-operator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"validation\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsUnknownStatusToBadRequestAfterAuthentication() throws Exception {
        mockMvc.perform(patch("/api/cases/1/status")
                        .with(csrf())
                        .header(OperatorTokenFilter.HEADER_NAME, "test-operator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
