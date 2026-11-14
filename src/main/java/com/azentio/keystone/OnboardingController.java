package com.azentio.keystone;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/onboarding")
class OnboardingController {
  private final OnboardingService service;
  OnboardingController(OnboardingService service){this.service=service;}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) Map<String,Long> create(@Valid @RequestBody ApplicationRequest r,Authentication a){return Map.of("id",service.create(r,a.getName()));}
  @PutMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void update(@PathVariable long id,@Valid @RequestBody ApplicationRequest r,Authentication a){service.update(id,r,a.getName());}
  @PostMapping("/{id}/actions") @ResponseStatus(HttpStatus.NO_CONTENT) void act(@PathVariable long id,@Valid @RequestBody ActionRequest r,Authentication a){service.act(id,r,a.getName(),roles(a));}
  @GetMapping("/{id}") ApplicationView get(@PathVariable long id,Authentication a){return service.get(id,a.getName(),roles(a));}
  @GetMapping Page<ApplicationView> list(@RequestParam(required=false) Status status,@RequestParam(required=false) RiskTier tier,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication a){return service.list(status,tier,page,Math.min(size,100),a.getName(),roles(a));}
  private Set<String> roles(Authentication a){return a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","")).collect(java.util.stream.Collectors.toSet());}
}

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(DomainException.class) ResponseEntity<ErrorResponse> domain(DomainException e){return ResponseEntity.status(e.status).body(new ErrorResponse(e.code,e.getMessage(),List.of()));}
  @ExceptionHandler(ValidationException.class) ResponseEntity<ErrorResponse> validation(ValidationException e){return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED","Validation failed",e.errors));}
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) ResponseEntity<ErrorResponse> denied(){return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN","Access denied",List.of()));}
}
