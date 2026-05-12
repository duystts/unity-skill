package com.unityskill.project;

import com.unityskill.project.entity.Project;
import com.unityskill.project.entity.ProjectVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByWorkspaceId(UUID workspaceId);

    List<Project> findAllByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId);

    List<Project> findAllByWorkspaceIdAndArchivedAtIsNotNull(UUID workspaceId);

    Optional<Project> findByIdAndVisibility(UUID id, ProjectVisibility visibility);

    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
