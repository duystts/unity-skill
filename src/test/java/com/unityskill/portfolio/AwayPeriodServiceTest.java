package com.unityskill.portfolio;

import com.unityskill.portfolio.dto.AwayPeriodRequest;
import com.unityskill.portfolio.dto.AwayPeriodResponse;
import com.unityskill.portfolio.entity.AwayPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwayPeriodServiceTest {

    @Mock AwayPeriodRepository awayPeriodRepository;
    @InjectMocks AwayPeriodService awayPeriodService;

    @Test
    void createAwayPeriod_validRequest_savesAndReturns201Data() {
        // AC1: creates record, returns response DTO
        UUID userId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 4, 10);
        LocalDate end   = LocalDate.of(2026, 4, 17);

        AwayPeriod saved = AwayPeriod.builder()
                .id(UUID.randomUUID()).userId(userId)
                .startDate(start).endDate(end)
                .createdAt(Instant.now()).build();

        when(awayPeriodRepository.save(any())).thenReturn(saved);

        AwayPeriodResponse result = awayPeriodService.createAwayPeriod(
                userId, new AwayPeriodRequest(start, end));

        ArgumentCaptor<AwayPeriod> captor = ArgumentCaptor.forClass(AwayPeriod.class);
        verify(awayPeriodRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getStartDate()).isEqualTo(start);
        assertThat(captor.getValue().getEndDate()).isEqualTo(end);
        assertThat(result.startDate()).isEqualTo(start.toString());
        assertThat(result.endDate()).isEqualTo(end.toString());
    }

    @Test
    void getAwayPeriods_multipleRecords_returnsAll() {
        // AC4: returns all declared away periods
        UUID userId = UUID.randomUUID();
        AwayPeriod p1 = AwayPeriod.builder().id(UUID.randomUUID()).userId(userId)
                .startDate(LocalDate.of(2026, 3, 1)).endDate(LocalDate.of(2026, 3, 7))
                .createdAt(Instant.now()).build();
        AwayPeriod p2 = AwayPeriod.builder().id(UUID.randomUUID()).userId(userId)
                .startDate(LocalDate.of(2026, 4, 10)).endDate(LocalDate.of(2026, 4, 17))
                .createdAt(Instant.now()).build();

        when(awayPeriodRepository.findAllByUserId(userId)).thenReturn(List.of(p1, p2));

        List<AwayPeriodResponse> results = awayPeriodService.getAwayPeriods(userId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).startDate()).isEqualTo("2026-03-01");
        assertThat(results.get(1).startDate()).isEqualTo("2026-04-10");
    }

    @Test
    void getAwayPeriods_noRecords_returnsEmptyList() {
        // AC4: empty list when user has no declared periods
        UUID userId = UUID.randomUUID();
        when(awayPeriodRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<AwayPeriodResponse> results = awayPeriodService.getAwayPeriods(userId);

        assertThat(results).isEmpty();
    }
}
