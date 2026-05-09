package com.unityskill.project;

import com.unityskill.project.entity.WorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, UUID> {

    List<WorkflowStage> findAllByProjectIdOrderByPositionAsc(UUID projectId);

    Optional<WorkflowStage> findByIdAndProjectId(UUID stageId, UUID projectId);
}
