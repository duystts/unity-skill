package com.unityskill.collaboration;

import com.unityskill.collaboration.entity.MeetingTranscript;
import com.unityskill.collaboration.entity.TranscriptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<MeetingTranscript, UUID> {

    List<MeetingTranscript> findAllByMeetingId(UUID meetingId);

    // Story 5.5: used by MeetingService.getMeeting() to resolve transcriptStatus for response
    Optional<MeetingTranscript> findTopByMeetingIdOrderByCreatedAtDesc(UUID meetingId);

    // Story 5.5 will use this to update transcript status during async AI processing
    @Modifying
    @Transactional
    @Query("UPDATE MeetingTranscript t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") TranscriptStatus status);
}
