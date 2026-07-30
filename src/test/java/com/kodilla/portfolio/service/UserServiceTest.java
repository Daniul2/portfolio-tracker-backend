package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.User;
import com.kodilla.portfolio.dto.UserDtos.UserRequest;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private UserService service;

    private final UserRequest request = new UserRequest("anna", "anna@example.com", "Anna K");

    @Test
    @DisplayName("creates a user and audits it")
    void createsUser() {
        when(userRepository.existsByUsername("anna")).thenReturn(false);
        when(userRepository.existsByEmail("anna@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            TestFixtures.setId(user, 5L);
            return user;
        });

        UserResponse response = service.create(request);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.username()).isEqualTo("anna");
        assertThat(response.portfolioCount()).isZero();
        verify(auditService).record(eq("USER_CREATED"), eq("User"), eq(5L), anyString());
    }

    @Test
    @DisplayName("rejects a duplicate username")
    void rejectsDuplicateUsername() {
        when(userRepository.existsByUsername("anna")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("anna");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a duplicate email")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByUsername("anna")).thenReturn(false);
        when(userRepository.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("anna@example.com");
    }

    @Test
    @DisplayName("updates an existing user")
    void updatesUser() {
        User existing = TestFixtures.user(5L, "anna");
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepository.countByUserId(5L)).thenReturn(2L);

        UserResponse response = service.update(5L,
                new UserRequest("anna", "new@example.com", "Anna Kowalska"));

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.displayName()).isEqualTo("Anna Kowalska");
        assertThat(response.portfolioCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("keeping your own username on update is not a duplicate")
    void allowsKeepingOwnUsername() {
        User existing = TestFixtures.user(5L, "anna");
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(5L, new UserRequest("anna", "anna@example.com", "Anna"));

        // Uniqueness must not be checked for values that did not change.
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("rejects taking a username that belongs to somebody else")
    void rejectsTakingAnotherUsername() {
        User existing = TestFixtures.user(5L, "anna");
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L,
                new UserRequest("taken", "anna@example.com", "Anna")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("updating a missing user is a 404")
    void updateMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deletes a user and audits it")
    void deletesUser() {
        User existing = TestFixtures.user(5L, "anna");
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));

        service.delete(5L);

        verify(userRepository).delete(existing);
        verify(auditService).record(eq("USER_DELETED"), eq("User"), eq(5L), anyString());
    }

    @Test
    @DisplayName("deleting a missing user is a 404")
    void deleteMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lists users with their portfolio counts")
    void listsUsers() {
        when(userRepository.findAll())
                .thenReturn(List.of(TestFixtures.user(1L, "a"), TestFixtures.user(2L, "b")));
        when(portfolioRepository.countByUserId(1L)).thenReturn(3L);
        when(portfolioRepository.countByUserId(2L)).thenReturn(0L);

        List<UserResponse> users = service.findAll();

        assertThat(users).hasSize(2);
        assertThat(users.get(0).portfolioCount()).isEqualTo(3);
        assertThat(users.get(1).portfolioCount()).isZero();
    }

    @Test
    @DisplayName("finds a user by id")
    void findsById() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(TestFixtures.user(5L, "anna")));
        when(portfolioRepository.countByUserId(5L)).thenReturn(1L);

        assertThat(service.findById(5L).username()).isEqualTo("anna");
    }

    @Test
    @DisplayName("a missing id is a 404")
    void findByIdMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("finds a user by username")
    void findsByUsername() {
        when(userRepository.findByUsername("anna"))
                .thenReturn(Optional.of(TestFixtures.user(5L, "anna")));
        when(portfolioRepository.countByUserId(5L)).thenReturn(0L);

        assertThat(service.findByUsername("anna").id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("an unknown username is a 404")
    void findByUsernameMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByUsername("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
