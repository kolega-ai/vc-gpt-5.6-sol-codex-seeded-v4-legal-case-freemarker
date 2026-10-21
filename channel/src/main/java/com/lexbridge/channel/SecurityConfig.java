package com.lexbridge.channel;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
class SecurityConfig {
  @Bean
  SecurityFilterChain chain(HttpSecurity h, SessionIdentityFilter f) throws Exception {
    return h.addFilterBefore(f, AnonymousAuthenticationFilter.class)
        .cors(org.springframework.security.config.Customizer.withDefaults())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login",
                        "/forgot-password",
                        "/invite-status",
                        "/reset-password",
                        "/registry-signin",
                        "/assets/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(x -> x.disable())
        .logout(x -> x.disable())
        .sessionManagement(s -> s.sessionFixation(fix -> fix.changeSessionId()).maximumSessions(3))
        .headers(
            x ->
                x.cacheControl(c -> c.disable())
                    .contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; style-src 'self'; script-src 'self'; img-src"
                                + " 'self' data:; frame-ancestors 'none'; form-action 'self'")))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowCredentials(true);
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}

@Component
class SessionIdentityFilter extends OncePerRequestFilter {
  private final DecisionClient api;

  SessionIdentityFilter(DecisionClient a) {
    api = a;
  }

  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
      throws ServletException, IOException {
    HttpSession session = r.getSession(false);
    if (session != null && session.getAttribute("token") != null) {
      try {
        Map<String, Object> user = api.get("/internal/auth/me", session);
        String role = user.get("role").toString();
        Object view = session.getAttribute("viewRole");
        if (view != null) role = view.toString();
        r.setAttribute("currentUser", user);
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
      } catch (Exception e) {
        session.invalidate();
      }
    }
    c.doFilter(r, s);
  }
}
