package com.unityskill.collaboration;

import com.unityskill.collaboration.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    List<Meeting> findAllByWorkspaceIdOrderByScheduledAtAsc(UUID workspaceId);

    Optional<Meeting> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
