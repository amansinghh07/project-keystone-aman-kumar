package com.azentio.keystone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
class ApplicationValidator {
  private static final String PAN = "[A-Z]{5}[0-9]{4}[A-Z]";
  List<FieldErrorView> validate(ApplicationRequest r) {
    List<FieldErrorView> errors = new ArrayList<>();
    if (!r.pan().matches(PAN)) errors.add(new FieldErrorView("pan", "INVALID_PAN"));
    if (r.relatedParties().stream().anyMatch(p -> !p.pan().matches(PAN))) errors.add(new FieldErrorView("relatedParties.pan", "INVALID_PAN"));
    if (r.relatedParties().stream().anyMatch(p -> !p.mobile().matches("[6-9][0-9]{9}"))) errors.add(new FieldErrorView("relatedParties.mobile", "INVALID_MOBILE"));
    if (r.expectedMonthlyCashDeposit().compareTo(r.expectedMonthlyTurnover()) > 0) errors.add(new FieldErrorView("expectedMonthlyCashDeposit", "CASH_EXCEEDS_TURNOVER"));
    long expectedRole = r.relatedParties().stream().filter(p -> switch(r.entityType()) {
      case SOLE_PROPRIETORSHIP -> p.role()==PartyRole.PROPRIETOR;
      case PARTNERSHIP -> p.role()==PartyRole.PARTNER;
      case LLP -> p.role()==PartyRole.DESIGNATED_PARTNER;
      case PRIVATE_LIMITED -> p.role()==PartyRole.DIRECTOR;
    }).count();
    int minimum = r.entityType()==EntityType.SOLE_PROPRIETORSHIP ? 1 : 2;
    if (expectedRole != minimum && r.entityType()==EntityType.SOLE_PROPRIETORSHIP || expectedRole < minimum && r.entityType()!=EntityType.SOLE_PROPRIETORSHIP)
      errors.add(new FieldErrorView("relatedParties.role", "INVALID_COMPOSITION"));
    if (r.relatedParties().stream().noneMatch(PartyRequest::signatory)) errors.add(new FieldErrorView("relatedParties.signatory", "SIGNATORY_REQUIRED"));
    char required = switch(r.entityType()) { case PARTNERSHIP, LLP -> 'F'; case PRIVATE_LIMITED -> 'C'; default -> 0; };
    if (required != 0 && (r.pan().length() < 4 || r.pan().charAt(3) != required)) errors.add(new FieldErrorView("pan", "ENTITY_PAN_TYPE_MISMATCH"));
    if (r.entityType()==EntityType.SOLE_PROPRIETORSHIP && r.relatedParties().stream().noneMatch(p -> p.role()==PartyRole.PROPRIETOR && p.pan().equals(r.pan()))) errors.add(new FieldErrorView("pan", "PROPRIETOR_PAN_MISMATCH"));
    if (r.gstin()!=null && !r.gstin().isBlank() && (r.gstin().length()!=15 || !r.gstin().substring(2,12).equals(r.pan()))) errors.add(new FieldErrorView("gstin", "GSTIN_PAN_MISMATCH"));
    if (r.relatedParties().stream().map(PartyRequest::ownershipPercent).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.valueOf(100))>0) errors.add(new FieldErrorView("relatedParties.ownershipPercent", "OWNERSHIP_EXCEEDS_100"));
    return errors;
  }
}

@Component
class RiskEngine {
  private final Set<String> high; private final Set<String> medium; private final int beneficialOwnerThreshold;
  RiskEngine(@Value("${keystone.risk.high-industries}") List<String> high, @Value("${keystone.risk.medium-industries}") List<String> medium,
             @Value("${keystone.risk.beneficial-owner-threshold}") int beneficialOwnerThreshold) {
    this.high=Set.copyOf(high); this.medium=Set.copyOf(medium); this.beneficialOwnerThreshold=beneficialOwnerThreshold;
  }
  Assessment assess(ApplicationRequest r) {
    List<FactorView> f = new ArrayList<>(); String industry=r.industryCode().toUpperCase(Locale.ROOT);
    if(high.contains(industry)) add(f,"HIGH_RISK_INDUSTRY",40,"Industry is in the configured high-risk list");
    if(medium.contains(industry)) add(f,"MEDIUM_RISK_INDUSTRY",20,"Industry is in the configured medium-risk list");
    BigDecimal ratio=r.expectedMonthlyTurnover().signum()==0?BigDecimal.ZERO:r.expectedMonthlyCashDeposit().divide(r.expectedMonthlyTurnover(),4,java.math.RoundingMode.HALF_UP);
    if(ratio.compareTo(BigDecimal.valueOf(.5))>0) add(f,"CASH_INTENSIVE",25,"Cash-to-turnover ratio exceeds 0.50"); else if(ratio.compareTo(BigDecimal.valueOf(.2))>0) add(f,"CASH_INTENSIVE",10,"Cash-to-turnover ratio exceeds 0.20");
    boolean pep=r.relatedParties().stream().anyMatch(PartyRequest::pep); if(pep) add(f,"PEP_RELATED_PARTY",50,"At least one related party is a PEP");
    if(r.relatedParties().stream().anyMatch(p->!"IN".equalsIgnoreCase(p.nationality()))) add(f,"FOREIGN_RELATED_PARTY",15,"At least one related party is not Indian");
    if(r.entityType()!=EntityType.SOLE_PROPRIETORSHIP && r.relatedParties().stream().noneMatch(p->p.ownershipPercent().compareTo(BigDecimal.valueOf(beneficialOwnerThreshold))>=0)) add(f,"OPAQUE_OWNERSHIP",20,"No party meets the beneficial-owner threshold");
    if(r.expectedMonthlyTurnover().compareTo(BigDecimal.valueOf(50_000_000))>0) add(f,"HIGH_TURNOVER",10,"Expected monthly turnover exceeds 50,000,000");
    int score=f.stream().mapToInt(FactorView::points).sum(); RiskTier tier=pep||score>=60?RiskTier.HIGH:score>=30?RiskTier.MEDIUM:RiskTier.LOW;
    return new Assessment(score,tier,f);
  }
  private void add(List<FactorView> f,String c,int p,String r){f.add(new FactorView(c,p,r));}
  record Assessment(int score,RiskTier tier,List<FactorView> factors) {}
}
