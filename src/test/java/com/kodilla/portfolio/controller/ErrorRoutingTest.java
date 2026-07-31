package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requests that reach no handler at all. */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("the root path describes the API instead of failing")
    void rootDescribesTheApi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("portfolio-tracker-backend"))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.endpoints.users").value("/v1/users"));
    }

    @Test
    @DisplayName("an unmapped path is 404, not 500")
    void unmappedPathIs404() throws Exception {
        mockMvc.perform(get("/nonsense"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("nonsense")));
    }

    @Test
    @DisplayName("the browser's favicon request is 404, not 500")
    void faviconIs404() throws Exception {
        // Every browser asks for this unprompted; it must not look like a crash.
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("an unmapped path below /v1 is also 404")
    void unmappedApiPathIs404() throws Exception {
        mockMvc.perform(get("/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("the wrong HTTP method on a real path is 405 and names what is allowed")
    void wrongMethodIs405() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(delete("/v1/users"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value(containsString("DELETE")))
                .andExpect(jsonPath("$.message").value(containsString("GET")));
    }

    @Test
    @DisplayName("a non-JSON request body is 415, not 500")
    void wrongContentTypeIs415() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("username=demo"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(containsString("application/json")));
    }

    @Test
    @DisplayName("every one of these still returns the standard error body")
    void keepsTheStandardErrorShape() throws Exception {
        mockMvc.perform(get("/nonsense"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
