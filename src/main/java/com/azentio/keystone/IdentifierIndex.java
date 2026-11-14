package com.azentio.keystone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Boundary for Redis in production; the synchronized implementation makes unit tests deterministic. */
interface IdentifierIndex {
  List<FactorView> evaluateAndIndex(long applicationId, ApplicationRequest request);
  void remove(long applicationId);
}

@Component
class InMemoryIdentifierIndex implements IdentifierIndex {
  private final String secret;
  private final Map<String,Set<Long>> entries = new HashMap<>();
  private final Map<Long,Set<String>> applicationKeys = new HashMap<>();
  InMemoryIdentifierIndex(@Value("${keystone.identifier-hmac-secret:development-only-change-me}") String secret){this.secret=secret;}
  @Override public synchronized List<FactorView> evaluateAndIndex(long id,ApplicationRequest request) {
    remove(id); List<FactorView> result=new ArrayList<>();
    int duplicate=count(key("ENTITY_PAN",request.pan())); if(duplicate>0) result.add(new FactorView("DUPLICATE_ENTITY_PAN",60,"Entity PAN matches "+duplicate+" other active application(s)"));
    String address=request.registeredAddress().line1()+"|"+request.registeredAddress().city()+"|"+request.registeredAddress().pincode(); int cluster=count(key("ADDRESS",normaliseAddress(address))); if(cluster>=4) result.add(new FactorView("ADDRESS_CLUSTER",20,"Address matches "+cluster+" other active application(s)"));
    for(PartyRequest party:request.relatedParties()) {
      int signatory=count(key("PARTY_PAN",party.pan())); if(signatory>=3 && result.stream().noneMatch(f->f.code().equals("SIGNATORY_SPREAD"))) result.add(new FactorView("SIGNATORY_SPREAD",30,"Related-party PAN appears on "+signatory+" other active application(s)"));
      int mobile=count(key("MOBILE",party.mobile())); if(mobile>0 && result.stream().noneMatch(f->f.code().equals("SHARED_MOBILE"))) result.add(new FactorView("SHARED_MOBILE",25,"Mobile appears on "+mobile+" other active application(s)"));
    }
    Set<String> keys=new HashSet<>(); keys.add(key("ENTITY_PAN",request.pan())); keys.add(key("ADDRESS",normaliseAddress(address))); for(PartyRequest p:request.relatedParties()){keys.add(key("PARTY_PAN",p.pan()));keys.add(key("MOBILE",p.mobile()));} applicationKeys.put(id,keys); keys.forEach(k->entries.computeIfAbsent(k,x->new HashSet<>()).add(id)); return result;
  }
  @Override public synchronized void remove(long id){Set<String> keys=applicationKeys.remove(id);if(keys!=null)keys.forEach(k->{Set<Long> ids=entries.get(k);if(ids!=null){ids.remove(id);if(ids.isEmpty())entries.remove(k);}});}
  private int count(String key){return entries.getOrDefault(key,Set.of()).size();}
  private String key(String type,String value){return type+":"+hmac(value);}
  static String normaliseAddress(String input){return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
  private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to key identifier",e);}}
}
