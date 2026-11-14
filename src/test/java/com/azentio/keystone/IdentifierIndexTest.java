package com.azentio.keystone;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IdentifierIndexTest {
  @Test void secondEntityWithSamePanGetsDuplicateFactorAndRemovalStopsIt() {
    IdentifierIndex index=new InMemoryIdentifierIndex("test-secret"); index.evaluateAndIndex(1,request("ABCDE1234F","9876543210"));
    assertTrue(index.evaluateAndIndex(2,request("ABCDE1234F","9876543211")).stream().anyMatch(f->f.code().equals("DUPLICATE_ENTITY_PAN")));
    index.remove(1); index.remove(2);
    assertFalse(index.evaluateAndIndex(3,request("ABCDE1234F","9876543212")).stream().anyMatch(f->f.code().equals("DUPLICATE_ENTITY_PAN")));
  }
  @Test void resubmissionDoesNotCountTheSameApplicationTwice() {
    IdentifierIndex index=new InMemoryIdentifierIndex("test-secret"); index.evaluateAndIndex(1,request("ABCDE1234F","9876543210")); index.evaluateAndIndex(1,request("ABCDE1234F","9876543210"));
    assertTrue(index.evaluateAndIndex(2,request("ABCDE1234F","9876543211")).stream().anyMatch(f->f.reason().contains("1 other")));
  }
  @Test void addressNormalisationIsStable(){assertEquals("12 main st pune 411001",InMemoryIdentifierIndex.normaliseAddress("12, Main St. PUNE | 411001"));}
  private ApplicationRequest request(String pan,String mobile){return new ApplicationRequest(EntityType.SOLE_PROPRIETORSHIP,"Acme",pan,null,new AddressRequest("1 Main St","Pune","411001"),"OTHER",BigDecimal.valueOf(100),BigDecimal.TEN,List.of(new PartyRequest("Aman",pan,mobile,PartyRole.PROPRIETOR,BigDecimal.valueOf(100),true,false,"IN")));}
}
