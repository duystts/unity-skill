package com.unityskill.webhook;

import com.unityskill.webhook.entity.GithubConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, UUID> {
    Optional<GithubConnection> findByProjectId(UUID projectId);
    Optional<GithubConnection> findByRepoFullName(String repoFullName);
}
