package com.unityskill.project;

import com.unityskill.project.entity.TicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketActivityRepository extends JpaRepository<TicketActivity, UUID> {

    List<TicketActivity> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<TicketActivity> findAllByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}
