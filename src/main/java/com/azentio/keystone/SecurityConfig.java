package com.azentio.keystone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @EnableWebSecurity
class SecurityConfig {
  @Bean UserDetailsService users() {
    return new InMemoryUserDetailsManager(
      User.withUsername("rm1").password("{noop}password").roles("RM").build(),
      User.withUsername("rm2").password("{noop}password").roles("RM").build(),
      User.withUsername("ops1").password("{noop}password").roles("OPS").build(),
      User.withUsername("compliance1").password("{noop}password").roles("COMPLIANCE").build());
  }
  @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(a -> a.requestMatchers("/api/**").authenticated().anyRequest().permitAll()).httpBasic(b -> {}).build();
  }
}
