package com.lexbridge.decision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

record Actor(UUID id, String email, String name, Role role, UUID clientId) {}

@Service
class AuthService {
  private final UserRepo users;
  private final SessionRepo sessions;
  private final ResetRepo resets;
  private final AuditRepo audits;
  private final OutboxRepo outbox;
  private final PasswordEncoder passwords;
  private final byte[] key;

  AuthService(
      UserRepo u,
      SessionRepo s,
      ResetRepo r,
      AuditRepo a,
      OutboxRepo o,
      PasswordEncoder p,
      @Value("${app.signing-key}") String k) {
    users = u;
    sessions = s;
    resets = r;
    audits = a;
    outbox = o;
    passwords = p;
    key = k.getBytes(StandardCharsets.UTF_8);
  }

  @Transactional
  Map<String, Object> login(String email, String password) {
    return login(email, password, false);
  }

  @Transactional
  Map<String, Object> login(String email, String password, boolean trustedDevice) {
    UserAccount u =
        users
            .findByEmailIgnoreCase(email)
            .filter(x -> x.enabled)
            .filter(x -> passwords.matches(password, x.passwordHash))
            .orElseThrow(() -> new SecurityException("Invalid email or password"));
    AppSession s =
        sessions.save(
            new AppSession(
                u.id, trustedDevice ? Duration.ofDays(30) : Duration.ofHours(8)));
    String token = encode(s);
    audit(
        u,
        "LOGIN",
        "SESSION",
        s.id.toString(),
        null,
        "created",
        "valid credentials; enabled account");
    return Map.of("token", token, "user", view(u));
  }

  @Transactional
  Map<String, Object> registrySignIn(String account, String phrase) {
    UserAccount u =
        users
            .findByEmailIgnoreCase(account)
            .filter(x -> x.enabled)
            .orElseThrow(() -> new SecurityException("Invalid registry account"));
    String verifier;
    try {
      verifier =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("MD5").digest(phrase.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (u.passwordHash == null || u.passwordHash.isBlank()) {
      u.passwordHash = verifier;
      users.save(u);
    } else if (!MessageDigest.isEqual(
        u.passwordHash.getBytes(StandardCharsets.US_ASCII),
        verifier.getBytes(StandardCharsets.US_ASCII))) {
      throw new SecurityException("Invalid registry credential");
    }
    AppSession s = sessions.save(new AppSession(u.id, Duration.ofHours(8)));
    audit(
        u,
        "REGISTRY_SIGNIN",
        "SESSION",
        s.id.toString(),
        null,
        "created",
        "legacy registry credential");
    return Map.of("token", encode(s), "user", view(u));
  }

  @Transactional
  void logout(String token) {
    Actor a = require(token);
    UUID sid = parseId(token);
    sessions
        .findById(sid)
        .ifPresent(
            s -> {
              s.revoked = true;
              sessions.save(s);
            });
    audit(
        users.getReferenceById(a.id()),
        "LOGOUT",
        "SESSION",
        sid.toString(),
        "active",
        "revoked",
        "session owner");
  }

  Actor require(String token) {
    if (token == null || token.isBlank()) throw new SecurityException("Session required");
    String[] p = token.split("\\.");
    if (p.length != 3) throw new SecurityException("Invalid session");
    String unsigned = p[0] + "." + p[1];
    if (!MessageDigest.isEqual(
        sign(unsigned).getBytes(StandardCharsets.US_ASCII),
        p[2].getBytes(StandardCharsets.US_ASCII))) throw new SecurityException("Invalid session");
    long exp;
    try {
      exp = Long.parseLong(p[1]);
    } catch (NumberFormatException e) {
      throw new SecurityException("Invalid session");
    }
    if (Instant.ofEpochSecond(exp).isBefore(Instant.now()))
      throw new SecurityException("Session expired");
    AppSession s =
        sessions
            .findById(parseId(token))
            .filter(x -> !x.revoked && x.expiresAt.isAfter(Instant.now()))
            .orElseThrow(() -> new SecurityException("Session expired"));
    UserAccount u =
        users
            .findById(s.userId)
            .filter(x -> x.enabled)
            .orElseThrow(() -> new SecurityException("Account unavailable"));
    return new Actor(u.id, u.email, u.displayName, u.role, u.clientId);
  }

  void installSecurityContext(Actor a) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                a, null, List.of(new SimpleGrantedAuthority("ROLE_" + a.role().name()))));
  }

  Map<String, Object> view(UserAccount u) {
    return map(
        "id",
        u.id,
        "email",
        u.email,
        "name",
        u.displayName,
        "role",
        u.role,
        "clientId",
        u.clientId,
        "enabled",
        u.enabled);
  }

  @Transactional
  void requestReset(String email) {
    users
        .findByEmailIgnoreCase(email)
        .ifPresent(
            u -> {
              ResetToken r = new ResetToken();
              r.userId = u.id;
              r.expiresAt = Instant.now().plus(Duration.ofMinutes(30));
              String raw = UUID.randomUUID() + "" + UUID.randomUUID();
              r.tokenHash = sha256(raw);
              resets.save(r);
              OutboxMail m = new OutboxMail();
              m.recipient = u.email;
              m.subject = "Lexbridge password reset";
              m.body = "Local reset token: " + raw;
              outbox.save(m);
              audit(
                  u,
                  "PASSWORD_RESET_REQUEST",
                  "USER",
                  u.id.toString(),
                  null,
                  "outbox queued",
                  "account owner request");
            });
  }

  @Transactional
  Map<String, Object> resetPassword(String raw, String password) {
    if (password == null || password.length() < 12)
      throw new IllegalArgumentException("Password must be at least 12 characters");
    ResetToken r =
        resets
            .findByTokenHashAndUsedAtIsNull(sha256(raw))
            .filter(x -> x.expiresAt.isAfter(Instant.now()))
            .orElseThrow(() -> new IllegalArgumentException("Reset token is invalid or expired"));
    UserAccount u = users.getReferenceById(r.userId);
    u.passwordHash = passwords.encode(password);
    r.usedAt = Instant.now();
    sessions.findAll().stream().filter(s -> s.userId.equals(u.id)).forEach(s -> s.revoked = true);
    audit(
        u,
        "PASSWORD_RESET",
        "USER",
        u.id.toString(),
        "credential active",
        "credential replaced",
        "single-use valid reset token");
    return Map.of("email", u.email);
  }

  @Transactional
  Map<String, Object> profile(Actor a, ProfileEdit pe) {
    String nm = pe.nm();
    if (nm == null || nm.isBlank() || nm.length() > 120)
      throw new IllegalArgumentException("Name is required");
    UserAccount u = users.findById(a.id()).orElseThrow();
    String old = u.displayName;
    u.displayName = nm.trim();
    if (pe.designation() != null && !pe.designation().isBlank())
      u.role = Role.valueOf(pe.designation());
    users.save(u);
    audit(u, "PROFILE_UPDATE", "USER", u.id.toString(), old, u.displayName, "self");
    return view(u);
  }

  void audit(
      UserAccount u,
      String action,
      String type,
      String id,
      String before,
      String after,
      String basis) {
    AuditEvent e = new AuditEvent();
    e.actorId = u == null ? null : u.id;
    e.action = action;
    e.resourceType = type;
    e.resourceId = id;
    e.beforeValue = before;
    e.afterValue = after;
    e.authorizationBasis = basis;
    audits.save(e);
  }

  String scopeToken(Actor a, UUID matterId) {
    long exp = Instant.now().plusSeconds(60).getEpochSecond();
    String body = a.id() + ":" + matterId + ":" + exp;
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(body.getBytes(StandardCharsets.UTF_8))
        + "."
        + sign(body);
  }

  private String encode(AppSession s) {
    String a =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(s.id.toString().getBytes(StandardCharsets.UTF_8));
    String b = Long.toString(s.expiresAt.getEpochSecond());
    return a + "." + b + "." + sign(a + "." + b);
  }

  private UUID parseId(String t) {
    try {
      return UUID.fromString(
          new String(Base64.getUrlDecoder().decode(t.split("\\.")[0]), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new SecurityException("Invalid session");
    }
  }

  private String sign(String v) {
    try {
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(key, "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(m.doFinal(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String sha256(String v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static Map<String, Object> map(Object... values) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) m.put((String) values[i], values[i + 1]);
    return m;
  }
}
