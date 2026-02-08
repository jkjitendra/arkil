package com.arkil.credential.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, UUID> {

    Optional<SocialIdentity> findByProviderAndProviderSubjectId(String provider, String providerSubjectId);

    List<SocialIdentity> findByUserId(UUID userId);

    boolean existsByProviderAndProviderSubjectId(String provider, String providerSubjectId);
}
