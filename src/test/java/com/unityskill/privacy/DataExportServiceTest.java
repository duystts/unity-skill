package com.unityskill.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.contribution.ContributionRepository;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.ContributionEvent;
import com.unityskill.contribution.entity.SourceType;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.portfolio.AwayPeriodRepository;
import com.unityskill.portfolio.ContributionStreakRepository;
import com.unityskill.privacy.entity.DataExport;
import com.unityskill.privacy.entity.ExportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock DataExportRepository dataExportRepository;
    @Mock ContributionRepository contributionRepository;
    @Mock SkillEvidenceRepository skillEvidenceRepository;
    @Mock ContributionStreakRepository streakRepository;
    @Mock AwayPeriodRepository awayPeriodRepository;
    @Mock WebSocketEventPublisher wsPublisher;
    @Spy  ObjectMapper objectMapper;  // real Jackson for JSON serialization
    @InjectMocks DataExportService dataExportService;

    @Test
    void generateExport_compilesAllUserData_savesReadyAndNotifiesWebSocket() {
        // AC1/2/5: happy path — compiles data, saves READY record, sends WS notification
        UUID userId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        DataExport inProgress = DataExport.builder().id(exportId).userId(userId).build();
        when(dataExportRepository.save(any())).thenReturn(inProgress);

        ContributionEvent event = ContributionEvent.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(UUID.randomUUID())
                .sourceType(SourceType.PR).processedAt(Instant.now()).createdAt(Instant.now())
                .skillSignals("[\"Java\"]").build();
        when(contributionRepository.findAllByUserId(userId)).thenReturn(List.of(event));
        when(skillEvidenceRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(streakRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(awayPeriodRepository.findAllByUserId(userId)).thenReturn(List.of());

        dataExportService.generateExport(userId);

        // Verify final save sets status=READY and exportJson is populated
        ArgumentCaptor<DataExport> captor = ArgumentCaptor.forClass(DataExport.class);
        verify(dataExportRepository, times(2)).save(captor.capture());
        DataExport saved = captor.getAllValues().get(1);  // second save = READY update
        assertThat(saved.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(saved.getExportJson()).contains("contributionEvents");
        assertThat(saved.getExportJson()).contains("userId");
        assertThat(saved.getCompletedAt()).isNotNull();

        // AC1: WebSocket notification sent
        verify(wsPublisher).publishNotification(eq(userId), eq("DATA_EXPORT_READY"), any());
    }

    @Test
    void generateExport_compilationFails_deletesInProgressRecord() {
        // Error handling: if compilation throws, IN_PROGRESS record is deleted so user can retry
        UUID userId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        DataExport inProgress = DataExport.builder().id(exportId).userId(userId).build();
        when(dataExportRepository.save(any())).thenReturn(inProgress);
        when(contributionRepository.findAllByUserId(userId))
                .thenThrow(new RuntimeException("DB unavailable"));

        dataExportService.generateExport(userId);  // must NOT throw

        verify(dataExportRepository).delete(inProgress); // cleanup
        verifyNoInteractions(wsPublisher);               // no notification on failure
    }

    @Test
    void generateExport_noDataForUser_savesReadyWithEmptyCollections() {
        // AC3: user with no data still gets a valid empty export (not an error)
        UUID userId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        DataExport inProgress = DataExport.builder().id(exportId).userId(userId).build();
        when(dataExportRepository.save(any())).thenReturn(inProgress);
        when(contributionRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(skillEvidenceRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(streakRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(awayPeriodRepository.findAllByUserId(userId)).thenReturn(List.of());

        dataExportService.generateExport(userId);

        ArgumentCaptor<DataExport> captor = ArgumentCaptor.forClass(DataExport.class);
        verify(dataExportRepository, times(2)).save(captor.capture());
        DataExport saved = captor.getAllValues().get(1);
        assertThat(saved.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(saved.getExportJson()).contains("\"contributionEvents\":[]");
        assertThat(saved.getExportJson()).contains("\"skillEvidences\":[]");
    }
}
