package com.azentio.keystone;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiskAndValidationTest {
  private final ApplicationValidator validator = new ApplicationValidator();
  private final RiskEngine risk = new RiskEngine(List.of("JEWELLERY","BULLION","MONEY_SERVICES","CRYPTO_ASSETS","REAL_ESTATE_BROKER"), List.of("USED_VEHICLES","TRAVEL_AGENCY","CONSTRUCTION"),10);
  @Test void validSoleProprietorshipPassesV1ToV5() { assertTrue(validator.validate(valid()).isEmpty()); }
  @Test void cashGreaterThanTurnoverFailsV1() { ApplicationRequest r=withAmounts(BigDecimal.valueOf(100),BigDecimal.valueOf(101)); assertTrue(validator.validate(r).stream().anyMatch(e->e.code().equals("CASH_EXCEEDS_TURNOVER"))); }
  @Test void partnerCompositionFailsV2() { ApplicationRequest r=new ApplicationRequest(EntityType.PARTNERSHIP,"Acme","ABCDE1234F",null,new AddressRequest("1 Main St","Pune","411001"),"CONSTRUCTION",BigDecimal.valueOf(100),BigDecimal.TEN,List.of(party("PROPRIETOR","ABCDE1234F",true))); assertTrue(validator.validate(r).stream().anyMatch(e->e.code().equals("INVALID_COMPOSITION"))); }
  @Test void gstinMismatchFailsV4() { ApplicationRequest r=new ApplicationRequest(valid().entityType(),valid().legalName(),valid().pan(),"27XXXXX1234F1Z5",valid().registeredAddress(),valid().industryCode(),valid().expectedMonthlyTurnover(),valid().expectedMonthlyCashDeposit(),valid().relatedParties()); assertTrue(validator.validate(r).stream().anyMatch(e->e.code().equals("GSTIN_PAN_MISMATCH"))); }
  @Test void ownershipAboveHundredFailsV5() { ApplicationRequest r=new ApplicationRequest(EntityType.PARTNERSHIP,"Acme","ABCDE1234F",null,new AddressRequest("1 Main St","Pune","411001"),"CONSTRUCTION",BigDecimal.valueOf(100),BigDecimal.TEN,List.of(party("PARTNER","ABCDE1234F",true),new PartyRequest("B","BCDEF1234F","9876543211",PartyRole.PARTNER,BigDecimal.valueOf(60),false,false,"IN"))); assertTrue(validator.validate(r).stream().anyMatch(e->e.code().equals("OWNERSHIP_EXCEEDS_100"))); }
  @Test void pepOverridesLowScoreToHigh() { ApplicationRequest r=new ApplicationRequest(EntityType.SOLE_PROPRIETORSHIP,"Acme","ABCDE1234F",null,new AddressRequest("1 Main St","Pune","411001"),"OTHER",BigDecimal.valueOf(100),BigDecimal.TEN,List.of(new PartyRequest("A","ABCDE1234F","9876543210",PartyRole.PROPRIETOR,BigDecimal.valueOf(100),true,true,"IN"))); assertEquals(RiskTier.HIGH,risk.assess(r).tier()); }
  @Test void scoreBoundariesAreCorrect() { assertEquals(RiskTier.LOW,risk.assess(withIndustry("OTHER")).tier()); assertEquals(RiskTier.MEDIUM,risk.assess(withIndustryAndCash("CONSTRUCTION",30)).tier()); }
  private ApplicationRequest valid(){return new ApplicationRequest(EntityType.SOLE_PROPRIETORSHIP,"Acme","ABCDE1234F",null,new AddressRequest("1 Main St","Pune","411001"),"OTHER",BigDecimal.valueOf(100),BigDecimal.TEN,List.of(party("PROPRIETOR","ABCDE1234F",true)));}
  private ApplicationRequest withIndustry(String industry){ApplicationRequest r=valid();return new ApplicationRequest(r.entityType(),r.legalName(),r.pan(),r.gstin(),r.registeredAddress(),industry,r.expectedMonthlyTurnover(),r.expectedMonthlyCashDeposit(),r.relatedParties());}
  private ApplicationRequest withIndustryAndCash(String industry,int cash){ApplicationRequest r=valid();return new ApplicationRequest(r.entityType(),r.legalName(),r.pan(),r.gstin(),r.registeredAddress(),industry,r.expectedMonthlyTurnover(),BigDecimal.valueOf(cash),r.relatedParties());}
  private ApplicationRequest withAmounts(BigDecimal turnover,BigDecimal cash){ApplicationRequest r=valid();return new ApplicationRequest(r.entityType(),r.legalName(),r.pan(),r.gstin(),r.registeredAddress(),r.industryCode(),turnover,cash,r.relatedParties());}
  private PartyRequest party(String role,String pan,boolean signatory){return new PartyRequest("Aman",pan,"9876543210",PartyRole.valueOf(role),BigDecimal.valueOf(100),signatory,false,"IN");}
}
