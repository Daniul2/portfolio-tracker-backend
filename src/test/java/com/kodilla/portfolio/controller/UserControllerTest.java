package com.kodilla.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodilla.portfolio.dto.UserDtos.UserRequest;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private UserService userService;

    private final UserResponse user = new UserResponse(
            1L, "demo", "demo@example.com", "Demo User", LocalDateTime.now(), 2);

    @Test
    @DisplayName("GET /v1/users returns the list")
    void listsUsers() throws Exception {
        when(userService.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("demo"))
                .andExpect(jsonPath("$[0].portfolioCount").value(2));
    }

    @Test
    @DisplayName("GET /v1/users/{id} returns one user")
    void getsUser() throws Exception {
        when(userService.findById(1L)).thenReturn(user);

        mockMvc.perform(get("/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("demo@example.com"));
    }

    @Test
    @DisplayName("GET a missing user maps to 404 with the standard error body")
    void missingUserIs404() throws Exception {
        when(userService.findById(99L)).thenThrow(new ResourceNotFoundException("User", 99L));

        mockMvc.perform(get("/v1/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User with id 99 was not found"));
    }

    @Test
    @DisplayName("GET /v1/users/by-username/{username}")
    void getsUserByUsername() throws Exception {
        when(userService.findByUsername("demo")).thenReturn(user);

        mockMvc.perform(get("/v1/users/by-username/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    @DisplayName("POST /v1/users returns 201 with a Location header")
    void createsUser() throws Exception {
        UserRequest request = new UserRequest("demo", "demo@example.com", "Demo User");
        when(userService.create(any(UserRequest.class))).thenReturn(user);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/users/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST with an invalid body returns 400 listing the offending fields")
    void rejectsInvalidBody() throws Exception {
        String body = """
                {"username":"x","email":"not-an-email"}
                """;

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("POST with a missing username returns 400")
    void rejectsMissingUsername() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    @DisplayName("POST a duplicate username maps to 409")
    void duplicateUserIs409() throws Exception {
        when(userService.create(any(UserRequest.class)))
                .thenThrow(new DuplicateResourceException("Username 'demo' is already taken"));

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("demo", "demo@example.com", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PUT /v1/users/{id} updates")
    void updatesUser() throws Exception {
        when(userService.update(eq(1L), any(UserRequest.class))).thenReturn(user);

        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("demo", "demo@example.com", "Demo User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE /v1/users/{id} returns 204 with no body")
    void deletesUser() throws Exception {
        mockMvc.perform(delete("/v1/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).delete(1L);
    }
}
