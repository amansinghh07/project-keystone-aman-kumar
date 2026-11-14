package com.azentio.keystone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Stores only HMAC-derived identifiers. Redis members are application ids and scores are submission timestamps. */
interface IdentifierIndex {
  List<FactorView> evaluateAndIndex(long applicationId, ApplicationRequest request);
  void remove(long applicationId, ApplicationRequest request);
  Map<String, Set<Long>> linkedApplicationIds(long applicationId, ApplicationRequest request);
}

abstract class HmacIdentifierIndex {
  final String secret;
  HmacIdentifierIndex(String secret) { this.secret = secret; }
  List<String> keys(ApplicationRequest r) {
    String address = r.registeredAddress().line1()+"|"+r.registeredAddress().city()+"|"+r.registeredAddress().pincode();
    List<String> keys = new ArrayList<>(List.of(key("ENTITY_PAN",r.pan()), key("ADDRESS",normaliseAddress(address))));
    for (PartyRequest p:r.relatedParties()) { keys.add(key("PARTY_PAN",p.pan())); keys.add(key("MOBILE",p.mobile())); }
    return keys;
  }
  String key(String type,String value){return "keystone:identifier:"+type+":"+hmac(value);}
  static String normaliseAddress(String input){return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
  private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to key identifier",e);}}
  List<FactorView> factors(Map<String,Set<Long>> links) {
    List<FactorView> result=new ArrayList<>();
    int duplicate=links.getOrDefault("ENTITY_PAN",Set.of()).size(); if(duplicate>0) result.add(new FactorView("DUPLICATE_ENTITY_PAN",60,"Entity PAN matches "+duplicate+" other active application(s)"));
    int cluster=links.getOrDefault("ADDRESS",Set.of()).size(); if(cluster>=4) result.add(new FactorView("ADDRESS_CLUSTER",20,"Address matches "+cluster+" other active application(s)"));
    int signatory=links.getOrDefault("PARTY_PAN",Set.of()).size(); if(signatory>=3) result.add(new FactorView("SIGNATORY_SPREAD",30,"Related-party PAN appears on "+signatory+" other active application(s)"));
    int mobile=links.getOrDefault("MOBILE",Set.of()).size(); if(mobile>0) result.add(new FactorView("SHARED_MOBILE",25,"Mobile appears on "+mobile+" other active application(s)"));
    return result;
  }
  String type(String key){return key.split(":",4)[2];}
}

/** Default test/dev adapter. Its synchronized read-then-write behaviour mirrors the Redis Lua operation. */
@Component @Profile("!docker")
class InMemoryIdentifierIndex extends HmacIdentifierIndex implements IdentifierIndex {
  private final Map<String,Set<Long>> entries = new HashMap<>(); private final Map<Long,Set<String>> applicationKeys = new HashMap<>();
  InMemoryIdentifierIndex(@Value("${keystone.identifier-hmac-secret:development-only-change-me}") String secret){super(secret);}
  @Override public synchronized List<FactorView> evaluateAndIndex(long id,ApplicationRequest request) { Map<String,Set<Long>> links=linkedApplicationIds(id,request); remove(id,request); Set<String> keys=new HashSet<>(keys(request)); applicationKeys.put(id,keys); keys.forEach(k->entries.computeIfAbsent(k,x->new HashSet<>()).add(id)); return factors(links); }
  @Override public synchronized void remove(long id,ApplicationRequest ignored){Set<String> keys=applicationKeys.remove(id);if(keys!=null)keys.forEach(k->{Set<Long> ids=entries.get(k);if(ids!=null){ids.remove(id);if(ids.isEmpty())entries.remove(k);}});}
  @Override public synchronized Map<String,Set<Long>> linkedApplicationIds(long id,ApplicationRequest request) {Map<String,Set<Long>> result=new TreeMap<>();for(String key:keys(request)){Set<Long> ids=new TreeSet<>(entries.getOrDefault(key,Set.of()));ids.remove(id);result.computeIfAbsent(type(key),x->new TreeSet<>()).addAll(ids);}return result;}
}

/** Production adapter. The Lua script prunes the rolling window, reads existing ids and writes the submission atomically. */
@Component @Profile("docker")
class RedisIdentifierIndex extends HmacIdentifierIndex implements IdentifierIndex {
  private static final DefaultRedisScript<List> INDEX = new DefaultRedisScript<>("local r={} for i,k in ipairs(KEYS) do redis.call('ZREMRANGEBYSCORE',k,'-inf',ARGV[3]); local ids=redis.call('ZRANGE',k,0,-1); r[i]=table.concat(ids,','); redis.call('ZADD',k,ARGV[2],ARGV[1]); redis.call('EXPIRE',k,ARGV[4]); end return r", List.class);
  private final StringRedisTemplate redis; private final Duration window;
  RedisIdentifierIndex(StringRedisTemplate redis,@Value("${keystone.identifier-hmac-secret:development-only-change-me}") String secret,@Value("${keystone.identifier-window-days:90}") long days){super(secret);this.redis=redis;this.window=Duration.ofDays(days);}
  @Override public List<FactorView> evaluateAndIndex(long id,ApplicationRequest request){List<String> keys=keys(request);Instant now=Instant.now();List raw=redis.execute(INDEX,keys,Long.toString(id),Long.toString(now.toEpochMilli()),Long.toString(now.minus(window).toEpochMilli()),Long.toString(window.plusDays(1).toSeconds()));return factors(toLinks(id,keys,raw));}
  @Override public void remove(long id,ApplicationRequest request){for(String key:keys(request)) redis.opsForZSet().remove(key,Long.toString(id));}
  @Override public Map<String,Set<Long>> linkedApplicationIds(long id,ApplicationRequest request){Map<String,Set<Long>> out=new TreeMap<>();Instant cutoff=Instant.now().minus(window);for(String key:keys(request)){redis.opsForZSet().removeRangeByScore(key,0,cutoff.toEpochMilli());Set<String> members=redis.opsForZSet().range(key,0,-1);Set<Long> ids=new TreeSet<>();if(members!=null)for(String member:members)if(!member.equals(Long.toString(id)))ids.add(Long.parseLong(member));out.computeIfAbsent(type(key),x->new TreeSet<>()).addAll(ids);}return out;}
  private Map<String,Set<Long>> toLinks(long id,List<String> keys,List raw){Map<String,Set<Long>> out=new TreeMap<>();for(int i=0;i<keys.size();i++){Set<Long> ids=new TreeSet<>();String csv=(String)raw.get(i);if(!csv.isBlank())for(String member:csv.split(","))if(!member.equals(Long.toString(id)))ids.add(Long.parseLong(member));out.computeIfAbsent(type(keys.get(i)),x->new TreeSet<>()).addAll(ids);}return out;}
}
