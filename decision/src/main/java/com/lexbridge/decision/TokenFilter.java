package com.lexbridge.decision;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class TokenFilter extends OncePerRequestFilter {
  private final AuthService auth;

  TokenFilter(AuthService a) {
    auth = a;
  }

  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
      throws ServletException, IOException {
    String path = r.getRequestURI();
    if (!path.startsWith("/internal/auth/login")
        && !path.startsWith("/internal/auth/forgot")
        && !path.startsWith("/internal/auth/reset")
        && !path.startsWith("/internal/auth/invite-status")
        && !path.startsWith("/internal/reference")
        && !path.startsWith("/internal/registry")
        && !path.startsWith("/case-files")) {
      try {
        Actor a = auth.require(r.getHeader("X-Session-Token"));
        auth.installSecurityContext(a);
        r.setAttribute("actor", a);
      } catch (SecurityException e) {
        s.sendError(401, e.getMessage());
        return;
      }
    }
    c.doFilter(r, s);
  }
}
