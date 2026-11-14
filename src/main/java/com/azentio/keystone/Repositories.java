package com.azentio.keystone;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface ApplicationRepository extends JpaRepository<OnboardingApplication, Long> {
  Optional<OnboardingApplication> findByIdAndOwner(Long id, String owner);
  Page<OnboardingApplication> findByStatusAndRiskTier(Status status, RiskTier tier, Pageable pageable);
  Page<OnboardingApplication> findByStatus(Status status, Pageable pageable);
  Page<OnboardingApplication> findByOwnerAndStatus(String owner, Status status, Pageable pageable);
}
