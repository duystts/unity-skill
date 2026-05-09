package com.unityskill.portfolio;

import com.unityskill.portfolio.dto.AwayPeriodRequest;
import com.unityskill.portfolio.dto.AwayPeriodResponse;
import com.unityskill.portfolio.entity.AwayPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AwayPeriodService {

    private final AwayPeriodRepository awayPeriodRepository;

    /** AC1: Create an away period for the authenticated user. */
    public AwayPeriodResponse createAwayPeriod(UUID userId, AwayPeriodRequest request) {
        AwayPeriod awayPeriod = AwayPeriod.builder()
                .userId(userId)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
        AwayPeriod saved = awayPeriodRepository.save(awayPeriod);
        return toResponse(saved);
    }

    /** AC4: Return all away periods declared by the authenticated user. */
    public List<AwayPeriodResponse> getAwayPeriods(UUID userId) {
        return awayPeriodRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AwayPeriodResponse toResponse(AwayPeriod p) {
        return new AwayPeriodResponse(
                p.getId().toString(),
                p.getUserId().toString(),
                p.getStartDate().toString(),
                p.getEndDate().toString(),
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
        );
    }
}
