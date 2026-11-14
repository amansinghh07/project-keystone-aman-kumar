package com.azentio.keystone;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
class OnboardingService {
  private final ApplicationRepository repo; private final ApplicationValidator validator; private final RiskEngine risk; private final Clock clock;
  OnboardingService(ApplicationRepository repo, ApplicationValidator validator, RiskEngine risk) { this.repo=repo; this.validator=validator; this.risk=risk; this.clock=Clock.systemUTC(); }
  @Transactional Long create(ApplicationRequest request, String actor) {
    OnboardingApplication a=new OnboardingApplication(); a.owner=actor; replace(a,request); repo.save(a); return a.id;
  }
  @Transactional void update(long id, ApplicationRequest request, String actor) {
    OnboardingApplication a=owned(id,actor); if(a.status!=Status.DRAFT && a.status!=Status.PENDING_INFO) throw new DomainException(409,"UPDATE_NOT_ALLOWED","Updates are only allowed in DRAFT or PENDING_INFO"); replace(a,request);
  }
  @Transactional void act(long id, ActionRequest request, String actor, Set<String> roles) {
    OnboardingApplication a=find(id); requireOwnerIfRm(a,actor,roles); Status from=a.status; Status to=transition(from,request.action(),roles);
    validateNote(from,request);
    if(request.action()==WorkflowAction.SUBMIT || request.action()==WorkflowAction.RESUBMIT) {
      ApplicationRequest current=toRequest(a); List<FieldErrorView> errors=validator.validate(current); if(!errors.isEmpty()) throw new ValidationException(errors);
      RiskEngine.Assessment assessment=risk.assess(current); a.riskScore=assessment.score(); a.riskTier=assessment.tier(); a.factors.clear(); assessment.factors().forEach(v->{RiskFactor f=new RiskFactor();f.application=a;f.code=v.code();f.points=v.points();f.reason=v.reason();a.factors.add(f);});
      to=assessment.tier()==RiskTier.HIGH?Status.PENDING_EDD:Status.PENDING_OPS_REVIEW; a.submittedAt=Instant.now(clock);
    }
    a.status=to; event(a,actor,request.action(),from,to,request.note());
  }
  @Transactional ApplicationView get(long id,String actor,Set<String> roles){ OnboardingApplication a=find(id); requireOwnerIfRm(a,actor,roles); return view(a,roles); }
  Page<ApplicationView> list(Status status,RiskTier tier,int page,int size,String actor,Set<String> roles) {
    Pageable p=PageRequest.of(page,size,Sort.by("submittedAt").ascending()); Page<OnboardingApplication> result;
    if(roles.contains("RM")) result=status==null?repo.findByOwner(actor,p):repo.findByOwnerAndStatus(actor,status,p); else if(status!=null && tier!=null) result=repo.findByStatusAndRiskTier(status,tier,p); else if(status!=null) result=repo.findByStatus(status,p); else result=repo.findAll(p);
    return result.map(a->view(a,roles));
  }
  private OnboardingApplication owned(long id,String actor){return repo.findByIdAndOwner(id,actor).orElseThrow(()->new DomainException(403,"NOT_OWNER","RM may only access applications they created"));}
  private OnboardingApplication find(long id){return repo.findById(id).orElseThrow(()->new DomainException(404,"NOT_FOUND","Application not found"));}
  private void requireOwnerIfRm(OnboardingApplication a,String actor,Set<String> roles){if(roles.contains("RM")&&!a.owner.equals(actor))throw new AccessDeniedException("RM may only access own applications");}
  private Status transition(Status s,WorkflowAction a,Set<String> r) {
    boolean rm=r.contains("RM"), ops=r.contains("OPS"), compliance=r.contains("COMPLIANCE");
    return switch(a) {
      case SUBMIT -> allowed(rm&&s==Status.DRAFT,Status.PENDING_OPS_REVIEW);
      case RESUBMIT -> allowed(rm&&s==Status.PENDING_INFO,Status.PENDING_OPS_REVIEW);
      case REQUEST_INFO -> allowed((ops&&s==Status.PENDING_OPS_REVIEW)||(compliance&&(s==Status.PENDING_OPS_REVIEW||s==Status.PENDING_EDD)),Status.PENDING_INFO);
      case ESCALATE_TO_EDD -> allowed(ops&&s==Status.PENDING_OPS_REVIEW,Status.PENDING_EDD);
      case APPROVE -> allowed((ops&&s==Status.PENDING_OPS_REVIEW)||(compliance&&(s==Status.PENDING_OPS_REVIEW||s==Status.PENDING_EDD)),Status.APPROVED);
      case REJECT -> allowed((ops&&s==Status.PENDING_OPS_REVIEW)||(compliance&&(s==Status.PENDING_OPS_REVIEW||s==Status.PENDING_EDD)),Status.REJECTED);
      case WITHDRAW -> allowed(rm&&(s==Status.DRAFT||s==Status.PENDING_INFO||s==Status.PENDING_OPS_REVIEW),Status.WITHDRAWN);
    };
  }
  private Status allowed(boolean condition,Status result){if(!condition)throw new DomainException(409,"ILLEGAL_TRANSITION","Action is not allowed for this role and status");return result;}
  private void validateNote(Status from,ActionRequest r){ int n=r.note()==null?0:r.note().trim().length(); if((r.action()==WorkflowAction.REQUEST_INFO||r.action()==WorkflowAction.ESCALATE_TO_EDD||r.action()==WorkflowAction.REJECT)&&n<20)throw new DomainException(400,"NOTE_TOO_SHORT","This action needs a note of at least 20 characters");if(r.action()==WorkflowAction.APPROVE&&from==Status.PENDING_EDD&&n<30)throw new DomainException(400,"NOTE_TOO_SHORT","Approving EDD needs a note of at least 30 characters"); }
  private void replace(OnboardingApplication a,ApplicationRequest r){a.entityType=r.entityType();a.legalName=r.legalName();a.pan=r.pan();a.gstin=r.gstin();a.addressLine1=r.registeredAddress().line1();a.city=r.registeredAddress().city();a.pincode=r.registeredAddress().pincode();a.industryCode=r.industryCode();a.expectedMonthlyTurnover=r.expectedMonthlyTurnover();a.expectedMonthlyCashDeposit=r.expectedMonthlyCashDeposit();a.parties.clear();r.relatedParties().forEach(x->{RelatedParty p=new RelatedParty();p.application=a;p.name=x.name();p.pan=x.pan();p.mobile=x.mobile();p.role=x.role();p.ownershipPercent=x.ownershipPercent();p.signatory=x.signatory();p.pep=x.pep();p.nationality=x.nationality();a.parties.add(p);});}
  private void event(OnboardingApplication a,String actor,WorkflowAction action,Status from,Status to,String note){OnboardingEvent e=new OnboardingEvent();e.application=a;e.actor=actor;e.action=action;e.fromStatus=from;e.toStatus=to;e.note=note;e.details=(action==WorkflowAction.SUBMIT||action==WorkflowAction.RESUBMIT)?"score="+a.riskScore+",tier="+a.riskTier+",factors="+a.factors.stream().map(f->f.code).toList():"";e.at=Instant.now(clock);a.events.add(e);}
  private ApplicationRequest toRequest(OnboardingApplication a){return new ApplicationRequest(a.entityType,a.legalName,a.pan,a.gstin,new AddressRequest(a.addressLine1,a.city,a.pincode),a.industryCode,a.expectedMonthlyTurnover,a.expectedMonthlyCashDeposit,a.parties.stream().map(p->new PartyRequest(p.name,p.pan,p.mobile,p.role,p.ownershipPercent,p.signatory,p.pep,p.nationality)).toList());}
  private ApplicationView view(OnboardingApplication a,Set<String> roles){return new ApplicationView(a.id,a.entityType,a.legalName,maskPan(a.pan),maskGstin(a.gstin),new AddressRequest(a.addressLine1,a.city,a.pincode),a.industryCode,a.expectedMonthlyTurnover,a.expectedMonthlyCashDeposit,a.status,a.riskScore,a.riskTier,a.factors.stream().map(f->new FactorView(f.code,f.points,f.reason)).toList(),a.parties.stream().map(p->new PartyView(p.name,maskPan(p.pan),maskMobile(p.mobile),p.role,p.ownershipPercent,p.signatory,p.pep,p.nationality)).toList(),a.events.stream().map(e->new EventView(e.actor,e.action,e.fromStatus,e.toStatus,e.note,e.details,e.at)).toList(),allowedActions(a.status,roles),a.submittedAt);}
  private List<WorkflowAction> allowedActions(Status s,Set<String> roles){return Arrays.stream(WorkflowAction.values()).filter(a->{try{transition(s,a,roles);return true;}catch(RuntimeException e){return false;}}).toList();}
  static String maskPan(String v){return v==null?null:"*****"+v.substring(Math.max(0,v.length()-5));} static String maskGstin(String v){return v==null?null:"**"+v.substring(Math.max(0,v.length()-4));} static String maskMobile(String v){return v==null?null:"******"+v.substring(Math.max(0,v.length()-4));}
}
class DomainException extends RuntimeException { final int status; final String code; DomainException(int s,String c,String m){super(m);status=s;code=c;} }
class ValidationException extends RuntimeException { final List<FieldErrorView> errors; ValidationException(List<FieldErrorView> e){errors=e;} }
