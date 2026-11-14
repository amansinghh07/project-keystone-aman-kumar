package com.azentio.keystone;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

record AddressRequest(@NotBlank String line1, @NotBlank String city, @Pattern(regexp="\\d{6}") String pincode) {}
record PartyRequest(@NotBlank String name, @NotBlank String pan, @NotBlank String mobile, @NotNull PartyRole role,
                    @NotNull @DecimalMin("0.0") BigDecimal ownershipPercent, boolean signatory, boolean pep, @NotBlank String nationality) {}
record ApplicationRequest(@NotNull EntityType entityType, @NotBlank String legalName, @NotBlank String pan, String gstin,
                          @NotNull @Valid AddressRequest registeredAddress, @NotBlank String industryCode,
                          @NotNull @DecimalMin("0.0") BigDecimal expectedMonthlyTurnover,
                          @NotNull @DecimalMin("0.0") BigDecimal expectedMonthlyCashDeposit,
                          @NotEmpty @Valid List<PartyRequest> relatedParties) {}
record ActionRequest(@NotNull WorkflowAction action, String note) {}
record FactorView(String code, int points, String reason) {}
record EventView(String actor, WorkflowAction action, Status fromStatus, Status toStatus, String note, String details, Instant at) {}
record PartyView(String name, String pan, String mobile, PartyRole role, BigDecimal ownershipPercent, boolean signatory, boolean pep, String nationality) {}
record ApplicationView(Long id, EntityType entityType, String legalName, String pan, String gstin, AddressRequest registeredAddress,
                       String industryCode, BigDecimal expectedMonthlyTurnover, BigDecimal expectedMonthlyCashDeposit,
                       Status status, Integer score, RiskTier tier, List<FactorView> factors, List<PartyView> relatedParties,
                       List<EventView> timeline, List<WorkflowAction> allowedActions, Instant submittedAt) {}
record ApplicationLinkView(Long applicationId, Status status, RiskTier tier, Set<String> matchedOn, Instant submittedAt) {}
record ReindexResult(int indexedApplications, int windowDays) {}
record ErrorResponse(String code, String message, List<FieldErrorView> fieldErrors) {}
record FieldErrorView(String field, String code) {}
