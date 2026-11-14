package com.azentio.keystone;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

enum EntityType { SOLE_PROPRIETORSHIP, PARTNERSHIP, LLP, PRIVATE_LIMITED }
enum PartyRole { PROPRIETOR, PARTNER, DESIGNATED_PARTNER, DIRECTOR }
enum Status { DRAFT, PENDING_OPS_REVIEW, PENDING_EDD, PENDING_INFO, APPROVED, REJECTED, WITHDRAWN }
enum RiskTier { LOW, MEDIUM, HIGH }
enum WorkflowAction { SUBMIT, REQUEST_INFO, RESUBMIT, ESCALATE_TO_EDD, APPROVE, REJECT, WITHDRAW }

@Entity @Table(name = "onboarding_application", indexes = @Index(name="idx_application_status_tier_submitted", columnList="status,risk_tier,submitted_at"))
class OnboardingApplication {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
  @Enumerated(EnumType.STRING) EntityType entityType;
  String legalName;
  String pan;
  String gstin;
  String addressLine1;
  String city;
  String pincode;
  String industryCode;
  BigDecimal expectedMonthlyTurnover;
  BigDecimal expectedMonthlyCashDeposit;
  String owner;
  @Enumerated(EnumType.STRING) Status status = Status.DRAFT;
  @Enumerated(EnumType.STRING) RiskTier riskTier;
  Integer riskScore;
  Instant submittedAt;
  @OneToMany(mappedBy="application", cascade=CascadeType.ALL, orphanRemoval=true) List<RelatedParty> parties = new ArrayList<>();
  @OneToMany(mappedBy="application", cascade=CascadeType.ALL, orphanRemoval=true) List<RiskFactor> factors = new ArrayList<>();
  @OneToMany(mappedBy="application", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("at ASC") List<OnboardingEvent> events = new ArrayList<>();
}

@Entity @Table(name="related_party")
class RelatedParty {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @ManyToOne(fetch=FetchType.LAZY) OnboardingApplication application;
  String name; String pan; String mobile;
  @Enumerated(EnumType.STRING) PartyRole role;
  BigDecimal ownershipPercent;
  boolean signatory; boolean pep; String nationality;
}

@Entity @Table(name="risk_factor")
class RiskFactor {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @ManyToOne(fetch=FetchType.LAZY) OnboardingApplication application;
  String code; Integer points; String reason;
}

@Entity @Table(name="onboarding_event")
class OnboardingEvent {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @ManyToOne(fetch=FetchType.LAZY) OnboardingApplication application;
  String actor;
  @Enumerated(EnumType.STRING) WorkflowAction action;
  @Enumerated(EnumType.STRING) Status fromStatus;
  @Enumerated(EnumType.STRING) Status toStatus;
  @Column(length=2000) String note;
  @Column(length=2000) String details;
  Instant at;
}
