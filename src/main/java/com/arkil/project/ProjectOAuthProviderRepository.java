package com.arkil.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectOAuthProviderRepository extends JpaRepository<ProjectOAuthProvider, UUID> {

    List<ProjectOAuthProvider> findByProjectId(UUID projectId);

    List<ProjectOAuthProvider> findByProjectIdAndEnabledTrue(UUID projectId);

    Optional<ProjectOAuthProvider> findByProjectIdAndProviderAndEnvironment(
            UUID projectId, String provider, Project.Environment environment);

    void deleteByProjectIdAndProviderAndEnvironment(
            UUID projectId, String provider, Project.Environment environment);

    boolean existsByProjectIdAndProviderAndEnvironment(
            UUID projectId, String provider, Project.Environment environment);

    void deleteByProjectId(UUID projectId);
}
