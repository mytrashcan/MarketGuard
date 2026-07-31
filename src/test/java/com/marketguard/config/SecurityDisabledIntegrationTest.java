package com.marketguard.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "marketguard.security.enabled=false")
@AutoConfigureMockMvc
class SecurityDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsDashboardReadsWithoutLoginButRejectsWritesWhenTokenIsUnset() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/cases/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/cases/1/status")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void omitsHsts() throws Exception {
        mockMvc.perform(get("/").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
