package com.unityskill.auth;

import com.unityskill.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGithubId(String githubId);
    Optional<User> findByGoogleId(String googleId);
    boolean existsByEmail(String email);
}
