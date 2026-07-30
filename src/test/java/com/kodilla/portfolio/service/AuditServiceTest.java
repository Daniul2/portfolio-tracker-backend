package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.AuditLog;
import com.kodilla.portfolio.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @InjectMocks
    private AuditService service;

    @Test
    @DisplayName("stores the entry as given")
    void storesEntry() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record("USER_CREATED", "User", 1L, "username=anna");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("USER_CREATED");
        assertThat(saved.getEntityType()).isEqualTo("User");
        assertThat(saved.getEntityId()).isEqualTo(1L);
        assertThat(saved.getDetails()).isEqualTo("username=anna");
    }

    @Test
    @DisplayName("truncates details that would exceed the column length")
    void truncatesLongDetails() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        String tooLong = "x".repeat(2000);

        service.record("BIG", "Thing", 1L, tooLong);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        // Must fit the 800-char column, and show it was cut.
        assertThat(captor.getValue().getDetails()).hasSize(800).endsWith("...");
    }

    @Test
    @DisplayName("details exactly at the limit are left alone")
    void keepsDetailsAtExactLimit() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        String exact = "y".repeat(800);

        service.record("EXACT", "Thing", 1L, exact);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isEqualTo(exact);
    }

    @Test
    @DisplayName("null details are allowed")
    void allowsNullDetails() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record("NO_DETAILS", "Thing", null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    @DisplayName("reads the recent entries and filters by entity type")
    void readsEntries() {
        when(auditLogRepository.findTop100ByOrderByCreatedAtDesc())
                .thenReturn(List.of(new AuditLog("A", "User", 1L, "d")));
        when(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("Alert"))
                .thenReturn(List.of(new AuditLog("B", "Alert", 2L, "d")));

        assertThat(service.findRecent()).hasSize(1);
        assertThat(service.findByEntityType("Alert")).hasSize(1);
    }
}
