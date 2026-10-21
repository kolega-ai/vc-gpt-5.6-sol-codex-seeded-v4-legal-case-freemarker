package com.lexbridge.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
@EnableMethodSecurity
@EnableScheduling
public class DecisionApplication {
  public static void main(String[] args) {
    SpringApplication.run(DecisionApplication.class, args);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, TokenFilter token) throws Exception {
    return http.csrf(c -> c.disable())
        .addFilterBefore(
            token,
            org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/internal/auth/login",
                        "/internal/auth/forgot",
                        "/internal/auth/reset",
                        "/internal/auth/invite-status",
                        "/internal/registry/signin",
                        "/internal/reference/**",
                        "/case-files/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }
}
