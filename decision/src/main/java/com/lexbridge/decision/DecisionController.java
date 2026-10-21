package com.lexbridge.decision;

import com.github.jknack.handlebars.Handlebars;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

record LoginDto(@Email @NotBlank String email, @NotBlank String password) {}

record ResetDto(@NotBlank String token, @Size(min = 12, max = 100) String password) {}

record TextDto(@NotBlank String value) {}

record ProfileEdit(@NotBlank String nm, String designation) {}

@RestController
@RequestMapping("/internal")
class DecisionController {
  private final AuthService auth;
  private final CaseService cases;
  private final ClientRepo clients;
  private final UserRepo users;
  private final MatterRepo matters;
  private final DocumentRepo documents;
  private final VersionRepo versions;
  private final TaskRepo tasks;
  private final DeadlineRepo deadlines;
  private final NotificationRepo notifications;
  private final AuditRepo audits;
  private final PracticeRepo practices;
  private final SettingRepo settings;
  private final OutboxRepo outbox;
  private final ApiKeyRepo apiKeys;
  private final WebhookRepo webhooks;
  private final DocAccessRepo accessCodes;
  private final StaffDirectory directory;
  private final org.springframework.security.crypto.password.PasswordEncoder passwords;
  private final OkHttpClient http = new OkHttpClient();
  private final String coreCredential;
  private final String signingKey;
  private static final org.slf4j.Logger ACCESS_LOG =
      org.slf4j.LoggerFactory.getLogger("lexbridge.intake.access");

  DecisionController(
      AuthService a,
      CaseService c,
      ClientRepo cr,
      UserRepo u,
      MatterRepo m,
      DocumentRepo d,
      VersionRepo v,
      TaskRepo t,
      DeadlineRepo dl,
      NotificationRepo n,
      AuditRepo ar,
      PracticeRepo p,
      SettingRepo s,
      OutboxRepo o,
      ApiKeyRepo ak,
      WebhookRepo wh,
      DocAccessRepo da,
      StaffDirectory dir,
      org.springframework.security.crypto.password.PasswordEncoder pw,
      @Value("${app.core-credential}") String cc,
      @Value("${app.signing-key}") String sk) {
    auth = a;
    cases = c;
    clients = cr;
    users = u;
    matters = m;
    documents = d;
    versions = v;
    tasks = t;
    deadlines = dl;
    notifications = n;
    audits = ar;
    practices = p;
    settings = s;
    outbox = o;
    apiKeys = ak;
    webhooks = wh;
    accessCodes = da;
    directory = dir;
    passwords = pw;
    coreCredential = cc;
    signingKey = sk;
  }

  private Actor actor(HttpServletRequest r) {
    return (Actor) r.getAttribute("actor");
  }

  @PostMapping("/auth/login")
  Map<String, Object> login(
      @Valid @RequestBody LoginDto d,
      @RequestParam(defaultValue = "false") boolean trustedDevice) {
    return auth.login(d.email(), d.password(), trustedDevice);
  }

  @PostMapping("/auth/logout")
  void logout(@RequestHeader("X-Session-Token") String token) {
    auth.logout(token);
  }

  @PostMapping("/auth/forgot")
  void forgot(@RequestBody Map<String, String> d) {
    auth.requestReset(d.get("email"));
  }

  @PostMapping("/auth/reset")
  Map<String, Object> reset(@Valid @RequestBody ResetDto d) {
    return auth.resetPassword(d.token(), d.password());
  }

  @PostMapping("/auth/invite-status")
  Map<String, Object> inviteStatus(@RequestBody Map<String, String> d) {
    String email = required(d, "email").toLowerCase(Locale.ROOT);
    UserAccount u =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new NoSuchElementException("No invitation exists for " + email));
    return AuthService.map(
        "status", u.enabled ? "ACTIVE" : "PENDING", "name", u.displayName);
  }

  @GetMapping("/auth/me")
  Map<String, Object> me(HttpServletRequest r) {
    Actor a = actor(r);
    return AuthService.map(
        "id",
        a.id(),
        "email",
        a.email(),
        "name",
        a.name(),
        "role",
        a.role(),
        "clientId",
        a.clientId());
  }

  @PostMapping("/auth/profile")
  Map<String, Object> profile(HttpServletRequest r, @Valid @RequestBody ProfileEdit d) {
    return auth.profile(actor(r), d);
  }

  @GetMapping("/dashboard")
  Map<String, Object> dashboard(HttpServletRequest r) {
    Actor a = actor(r);
    List<Map<String, Object>> ms = cases.listMatters(a, "", "", "");
    List<Map<String, Object>> work =
        a.role() == Role.CLIENT
            ? List.of()
            : tasks.findByAssigneeIdOrderByDueDate(a.id()).stream()
                .limit(8)
                .map(cases::taskView)
                .toList();
    List<Map<String, Object>> due =
        deadlines
            .findByDueAtBetweenOrderByDueAt(Instant.now(), Instant.now().plus(Duration.ofDays(30)))
            .stream()
            .filter(
                d -> {
                  try {
                    cases.requireMatter(a, d.matterId, false);
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                })
            .limit(8)
            .map(cases::deadlineView)
            .toList();
    return AuthService.map(
        "matterCount",
        ms.size(),
        "openCount",
        ms.stream().filter(m -> m.get("status") == MatterStatus.OPEN).count(),
        "matters",
        ms.stream().limit(7).toList(),
        "tasks",
        work,
        "deadlines",
        due,
        "notifications",
        notifications.findTop20ByUserIdOrderByCreatedAtDesc(a.id()).stream()
            .limit(6)
            .map(
                n ->
                    AuthService.map(
                        "title", n.title, "body", n.body, "link", n.link, "createdAt", n.createdAt))
            .toList());
  }

  @GetMapping("/matters")
  List<Map<String, Object>> matters(
      HttpServletRequest r,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "updated") String sort,
      @RequestParam(defaultValue = "") String assignee,
      @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to,
      @RequestParam(defaultValue = "") String scope) {
    Actor a = actor(r);
    Actor view =
        scope.isBlank()
            ? a
            : new Actor(a.id(), a.email(), a.name(), Role.valueOf(scope), a.clientId());
    return cases.listMatters(view, q, status, sort).stream()
        .filter(
            m ->
                assignee.isBlank()
                    || assignee.equals(Objects.toString(m.get("attorneyId"), ""))
                    || assignee.equals(Objects.toString(m.get("paralegalId"), "")))
        .filter(
            m ->
                from.isBlank()
                    || m.get("targetCloseDate") != null
                        && !((LocalDate) m.get("targetCloseDate")).isBefore(LocalDate.parse(from)))
        .filter(
            m ->
                to.isBlank()
                    || m.get("targetCloseDate") != null
                        && !((LocalDate) m.get("targetCloseDate")).isAfter(LocalDate.parse(to)))
        .toList();
  }

  @GetMapping("/matters/{id}")
  Map<String, Object> matter(HttpServletRequest r, @PathVariable UUID id) {
    return cases.detail(actor(r), id);
  }

  @PostMapping("/matters")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> createMatter(HttpServletRequest r, @RequestBody Map<String, String> f) {
    return cases.createMatter(actor(r), f);
  }

  @PostMapping("/matters/{id}")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> editMatter(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> f) {
    return cases.editMatter(actor(r), id, f);
  }

  @DeleteMapping("/matters/{id}")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  void deleteMatter(HttpServletRequest r, @PathVariable UUID id) {
    cases.deleteMatter(actor(r), id);
  }

  @PostMapping("/matters/{id}/transition")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY')")
  Map<String, Object> transition(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody TextDto d) {
    return cases.transitionMatter(actor(r), id, d.value());
  }

  @PostMapping("/matters/{id}/tasks")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> task(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> f) {
    return cases.addTask(actor(r), id, f);
  }

  @PostMapping("/tasks/{id}/transition")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> taskTransition(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody TextDto d) {
    return cases.transitionTask(actor(r), id, d.value());
  }

  @PostMapping("/matters/{id}/tasks/cancel")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> cancelTasks(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> f) {
    return cases.cancelTasks(actor(r), id, f.getOrDefault("reason", "withdrawn"));
  }

  @PostMapping("/matters/{id}/deadlines")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> deadline(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> f) {
    return cases.addDeadline(actor(r), id, f);
  }

  @PostMapping("/matters/{id}/notes")
  Map<String, Object> note(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> f) {
    return cases.addNote(actor(r), id, f);
  }

  @PostMapping("/messages/{id}/approve")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY')")
  Map<String, Object> approve(HttpServletRequest r, @PathVariable UUID id) {
    return cases.approveMessage(actor(r), id);
  }

  @PostMapping(value = "/matters/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Map<String, Object> upload(
      HttpServletRequest r,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "CORRESPONDENCE") String category,
      @RequestParam(defaultValue = "INTERNAL_ONLY") String visibility,
      @RequestParam(defaultValue = "") String tags,
      @RequestPart MultipartFile file)
      throws Exception {
    return cases.upload(actor(r), id, name, category, Visibility.valueOf(visibility), tags, file);
  }

  @PostMapping(value = "/documents/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> version(
      HttpServletRequest r, @PathVariable UUID id, @RequestPart MultipartFile file)
      throws Exception {
    return cases.addVersion(actor(r), id, file);
  }

  @PostMapping("/documents/{id}/visibility")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY')")
  Map<String, Object> visibility(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody TextDto d) {
    return cases.share(actor(r), id, d.value());
  }

  @GetMapping("/documents/{id}")
  Map<String, Object> document(HttpServletRequest r, @PathVariable UUID id) {
    Actor a = actor(r);
    DocumentRecord d = cases.requireDocument(a, id);
    Map<String, Object> out = new LinkedHashMap<>(cases.docView(d));
    out.put("matter", cases.matterView(cases.requireMatter(a, d.matterId, false)));
    out.put(
        "versions",
        versions.findByDocumentIdOrderByVersionDesc(id).stream()
            .map(
                v ->
                    AuthService.map(
                        "id",
                        v.id,
                        "version",
                        v.version,
                        "originalName",
                        v.originalName,
                        "contentType",
                        v.contentType,
                        "sizeBytes",
                        v.sizeBytes,
                        "checksum",
                        v.checksum,
                        "createdAt",
                        v.createdAt))
            .toList());
    return out;
  }

  @GetMapping("/documents/{id}/download")
  ResponseEntity<FileSystemResource> download(
      HttpServletRequest r, @PathVariable UUID id, @RequestParam(required = false) Integer version)
      throws Exception {
    Download d = cases.download(actor(r), id, version);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.contentType()))
        .contentLength(d.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(d.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new FileSystemResource(d.path()));
  }

  @GetMapping("/clients")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  List<Map<String, Object>> clientList() {
    return clients.findAllByOrderByName().stream().map(cases::clientView).toList();
  }

  @PostMapping("/clients")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> client(HttpServletRequest r, @RequestBody Map<String, String> f) {
    Actor a = actor(r);
    Client c = new Client();
    c.name = required(f, "name");
    c.email = required(f, "email");
    c.type = f.getOrDefault("type", "PERSON");
    c.phone = f.getOrDefault("phone", "");
    c.address = f.getOrDefault("address", "");
    c.conflictMetadata = f.getOrDefault("conflictMetadata", "");
    c.status = "ACTIVE";
    clients.save(c);
    cases.audit(a, "CLIENT_CREATE", "CLIENT", c.id, null, c.name, "staff role");
    return cases.clientView(c);
  }

  @GetMapping("/directory")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> directory() {
    return AuthService.map(
        "clients",
        clients.findAllByOrderByName().stream().map(cases::clientView).toList(),
        "users",
        users.findAllByOrderByDisplayName().stream().map(auth::view).toList(),
        "practiceAreas",
        practices.findAll().stream()
            .map(p -> AuthService.map("id", p.id, "name", p.name, "active", p.active))
            .toList());
  }

  @GetMapping("/work")
  Map<String, Object> work(HttpServletRequest r) {
    Actor a = actor(r);
    List<Map<String, Object>> ts =
        a.role() == Role.CLIENT
            ? List.of()
            : tasks.findByAssigneeIdOrderByDueDate(a.id()).stream().map(cases::taskView).toList();
    List<Map<String, Object>> ds =
        deadlines.findAll().stream()
            .filter(
                d -> {
                  try {
                    cases.requireMatter(a, d.matterId, false);
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                })
            .sorted(Comparator.comparing(d -> d.dueAt))
            .map(cases::deadlineView)
            .toList();
    return AuthService.map("tasks", ts, "deadlines", ds);
  }

  @GetMapping("/search")
  Map<String, Object> search(HttpServletRequest r, @RequestParam(defaultValue = "") String q) {
    Actor a = actor(r);
    String needle = q.trim().toLowerCase(Locale.ROOT);
    if (needle.length() > 100)
      throw new IllegalArgumentException("Search is limited to 100 characters");
    List<Map<String, Object>> matterRows = cases.listMatters(a, needle, "", "");
    List<Map<String, Object>> docRows =
        documents.findAll().stream()
            .filter(
                d ->
                    d.name.toLowerCase(Locale.ROOT).contains(needle)
                        || Objects.requireNonNullElse(d.tags, "")
                            .toLowerCase(Locale.ROOT)
                            .contains(needle))
            .filter(
                d -> {
                  try {
                    cases.requireDocument(a, d.id);
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                })
            .limit(50)
            .map(cases::docView)
            .toList();
    List<Map<String, Object>> taskRows =
        tasks.findAll().stream()
            .filter(t -> t.title.toLowerCase(Locale.ROOT).contains(needle))
            .filter(
                t -> {
                  try {
                    cases.requireMatter(a, t.matterId, false);
                    return a.role() != Role.CLIENT || a.id().equals(t.assigneeId);
                  } catch (Exception e) {
                    return false;
                  }
                })
            .limit(50)
            .map(cases::taskView)
            .toList();
    List<Map<String, Object>> deadlineRows =
        deadlines.findAll().stream()
            .filter(d -> d.title.toLowerCase(Locale.ROOT).contains(needle))
            .filter(
                d -> {
                  try {
                    cases.requireMatter(a, d.matterId, false);
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                })
            .limit(50)
            .map(cases::deadlineView)
            .toList();
    List<Map<String, Object>> clientRows =
        a.role() == Role.CLIENT
            ? List.of()
            : clients.findAllByOrderByName().stream()
                .filter(
                    c ->
                        c.name.toLowerCase(Locale.ROOT).contains(needle)
                            || c.email.toLowerCase(Locale.ROOT).contains(needle))
                .limit(50)
                .map(cases::clientView)
                .toList();
    cases.audit(
        a,
        "GLOBAL_SEARCH",
        "SEARCH",
        (String) null,
        null,
        "matters="
            + matterRows.size()
            + ",documents="
            + docRows.size()
            + ",tasks="
            + taskRows.size()
            + ",deadlines="
            + deadlineRows.size()
            + ",clients="
            + clientRows.size(),
        "role and object scope; one event per request");
    return AuthService.map(
        "query",
        q,
        "matters",
        matterRows,
        "documents",
        docRows,
        "tasks",
        taskRows,
        "deadlines",
        deadlineRows,
        "clients",
        clientRows);
  }

  @GetMapping("/reports/deadlines.csv")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  ResponseEntity<byte[]> deadlineExport(
      HttpServletRequest r, @RequestParam(defaultValue = "") String saveAs) throws Exception {
    Actor a = actor(r);
    StringBuilder csv = new StringBuilder("matter_reference,title,due_at,completed\n");
    int[] count = {0};
    for (Deadline d : deadlines.findAll()) {
      try {
        Matter m = cases.requireMatter(a, d.matterId, false);
        csv.append(cell(m.reference))
            .append(',')
            .append(cell(d.title))
            .append(',')
            .append(d.dueAt)
            .append(',')
            .append(d.completed)
            .append('\n');
        count[0]++;
      } catch (SecurityException ignored) {
      }
    }
    cases.audit(
        a,
        "DEADLINE_EXPORT",
        "REPORT",
        (String) null,
        null,
        "count=" + count[0],
        "role and matter assignments");
    if (!saveAs.isBlank()) cases.archiveCsv(a, saveAs, csv.toString());
    return csv(csv.toString(), "deadlines.csv");
  }

  @GetMapping("/reports/matters.csv")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  ResponseEntity<byte[]> matterExport(HttpServletRequest r) {
    Actor a = actor(r);
    StringBuilder csv = new StringBuilder("reference,title,practice_area,status,sensitivity\n");
    matters
        .findAllByOrderByUpdatedAtDesc()
        .forEach(
            m ->
                csv.append(cell(m.reference))
                    .append(',')
                    .append(cell(m.title))
                    .append(',')
                    .append(cell(m.practiceArea))
                    .append(',')
                    .append(m.status)
                    .append(',')
                    .append(cell(m.sensitivity))
                    .append('\n'));
    cases.audit(
        a,
        "MATTER_EXPORT",
        "REPORT",
        (String) null,
        null,
        "count=" + matters.count(),
        "firm administrator");
    return csv(csv.toString(), "matter-inventory.csv");
  }

  @GetMapping("/reports/documents.csv")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  ResponseEntity<byte[]> documentExport(HttpServletRequest r) {
    Actor a = actor(r);
    StringBuilder csv = new StringBuilder("matter_id,name,category,visibility,version\n");
    documents
        .findAll()
        .forEach(
            d ->
                csv.append(d.matterId)
                    .append(',')
                    .append(cell(d.name))
                    .append(',')
                    .append(d.category)
                    .append(',')
                    .append(d.visibility)
                    .append(',')
                    .append(d.currentVersion)
                    .append('\n'));
    cases.audit(
        a,
        "DOCUMENT_EXPORT",
        "REPORT",
        (String) null,
        null,
        "count=" + documents.count(),
        "firm administrator");
    return csv(csv.toString(), "document-inventory.csv");
  }

  @GetMapping("/admin/audit")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  List<Map<String, Object>> audit(HttpServletRequest r) {
    List<AuditEvent> rows = audits.findTop200ByOrderByOccurredAtDesc();
    cases.audit(
        actor(r),
        "AUDIT_READ",
        "AUDIT",
        (String) null,
        null,
        "count=" + rows.size(),
        "firm administrator; one event per request");
    return rows.stream().map(cases::auditView).toList();
  }

  @GetMapping("/admin/users")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  List<Map<String, Object>> userList() {
    return users.findAllByOrderByDisplayName().stream().map(auth::view).toList();
  }

  @PostMapping("/admin/users")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> invite(HttpServletRequest r, @RequestBody Map<String, String> d) {
    Actor a = actor(r);
    String email = required(d, "email").toLowerCase(Locale.ROOT);
    if (users.findByEmailIgnoreCase(email).isPresent())
      throw new IllegalArgumentException("Email already exists");
    Role role = Role.valueOf(required(d, "role"));
    UUID clientId =
        d.get("clientId") == null || d.get("clientId").isBlank()
            ? null
            : UUID.fromString(d.get("clientId"));
    if (role == Role.CLIENT && clientId == null)
      throw new IllegalArgumentException("Client account requires a client record");
    String temporary = UUID.randomUUID() + "!aA1";
    UserAccount u =
        users.save(
            new UserAccount(
                email, passwords.encode(temporary), required(d, "name"), role, clientId));
    OutboxMail mail = new OutboxMail();
    mail.recipient = email;
    mail.subject = "Your Lexbridge invitation";
    mail.body = "Temporary local password: " + temporary;
    outbox.save(mail);
    cases.audit(a, "USER_INVITE", "USER", u.id, null, role.name(), "firm administrator");
    return auth.view(u);
  }

  @PostMapping("/admin/users/{id}/role")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> role(HttpServletRequest r, @PathVariable UUID id, @RequestBody TextDto d) {
    Actor a = actor(r);
    UserAccount u = users.findById(id).orElseThrow();
    Role target = Role.valueOf(d.value());
    if (target == Role.CLIENT && u.clientId == null)
      throw new IllegalArgumentException("Assign a client record before changing to client role");
    Role before = u.role;
    u.role = target;
    users.save(u);
    cases.audit(
        a, "USER_ROLE_CHANGE", "USER", u.id, before.name(), u.role.name(), "firm administrator");
    return auth.view(u);
  }

  @PostMapping("/admin/users/{id}/enabled")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> enabled(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, Boolean> d) {
    Actor a = actor(r);
    if (a.id().equals(id) && Boolean.FALSE.equals(d.get("enabled")))
      throw new IllegalArgumentException("You cannot disable your own account");
    UserAccount u = users.findById(id).orElseThrow();
    boolean before = u.enabled;
    u.enabled = Boolean.TRUE.equals(d.get("enabled"));
    users.save(u);
    cases.audit(
        a,
        "USER_ENABLED_CHANGE",
        "USER",
        u.id,
        Boolean.toString(before),
        Boolean.toString(u.enabled),
        "firm administrator");
    return auth.view(u);
  }

  @GetMapping("/admin/settings")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, String> settingList(@RequestParam(defaultValue = "") String section) {
    Map<String, String> m = new LinkedHashMap<>();
    settings.findAll().forEach(s -> m.put(s.key, s.value));
    if (section.equals("delivery")) {
      m.put("core.credential", coreCredential);
      m.put("session.signing", signingKey);
    }
    return m;
  }

  @PostMapping(value = "/admin/import/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> importCsv(
      HttpServletRequest r, @PathVariable String type, @RequestPart MultipartFile file)
      throws Exception {
    if (file.isEmpty() || file.getSize() > 2_000_000)
      throw new IllegalArgumentException("CSV must be between 1 byte and 2 MB");
    List<String> lines = new String(file.getBytes(), StandardCharsets.UTF_8).lines().toList();
    if (lines.size() < 2) throw new IllegalArgumentException("CSV has no data rows");
    int count = 0;
    Actor a = actor(r);
    ACCESS_LOG.info(
        "csv intake type="
            + type
            + " file="
            + file.getOriginalFilename()
            + " bytes="
            + file.getSize()
            + " actor="
            + a.email());
    if (type.equals("clients")) {
      for (String line : lines.subList(1, lines.size())) {
        List<String> c = parseCsv(line);
        if (c.size() < 3)
          throw new IllegalArgumentException("Clients CSV columns: name,email,type");
        if (clients.findAll().stream().noneMatch(x -> x.email.equalsIgnoreCase(c.get(1)))) {
          Client x = new Client();
          x.name = c.get(0);
          x.email = c.get(1);
          x.type = Set.of("PERSON", "ORGANIZATION").contains(c.get(2)) ? c.get(2) : "PERSON";
          x.status = "ACTIVE";
          clients.save(x);
          count++;
        }
      }
    } else if (type.equals("matters")) {
      for (String line : lines.subList(1, lines.size())) {
        List<String> c = parseCsv(line);
        if (c.size() < 3)
          throw new IllegalArgumentException(
              "Matters CSV columns: title,client_email,practice_area");
        Client cl =
            clients.findAll().stream()
                .filter(x -> x.email.equalsIgnoreCase(c.get(1)))
                .findFirst()
                .orElseThrow(
                    () -> new IllegalArgumentException("Unknown client email: " + c.get(1)));
        cases.createMatter(
            a,
            Map.of(
                "title",
                c.get(0),
                "clientId",
                cl.id.toString(),
                "practiceArea",
                c.get(2),
                "sensitivity",
                "CONFIDENTIAL"));
        count++;
      }
    } else throw new IllegalArgumentException("Import type must be clients or matters");
    cases.audit(a, "CSV_IMPORT", "IMPORT", type, null, "count=" + count, "firm administrator");
    return Map.of("count", count);
  }

  @PostMapping("/admin/import/filings")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> importFilings(HttpServletRequest r, @RequestBody Map<String, String> d)
      throws Exception {
    UUID matterId = UUID.fromString(required(d, "matterId"));
    return cases.importFilingManifest(actor(r), matterId, required(d, "manifestXml"));
  }

  @PostMapping(value = "/admin/brand-asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> brandAsset(
      HttpServletRequest r,
      @RequestParam(defaultValue = "") String assetName,
      @RequestPart MultipartFile file)
      throws Exception {
    Map<String, Object> stored = cases.storeBrandAsset(actor(r), assetName, file);
    save("firm.brandmark", stored.get("storageName").toString());
    save("firm.brandmark.type", stored.get("contentType").toString());
    return stored;
  }

  @GetMapping("/admin/brand-asset")
  ResponseEntity<FileSystemResource> brandAsset() throws Exception {
    FirmSetting name = settings.findById("firm.brandmark").orElseThrow();
    String type =
        settings
            .findById("firm.brandmark.type")
            .map(x -> x.value)
            .orElse("application/octet-stream");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(type))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .body(new FileSystemResource(cases.brandAsset(name.value)));
  }

  private void save(String key, String value) {
    FirmSetting s = settings.findById(key).orElse(new FirmSetting(key, value));
    s.value = value;
    s.updatedAt = Instant.now();
    settings.save(s);
  }

  @PostMapping("/admin/settings/{key}")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  void setting(HttpServletRequest r, @PathVariable String key, @RequestBody TextDto d) {
    if (!Set.of("retention.years", "firm.name", "reminders.enabled").contains(key))
      throw new IllegalArgumentException("Unknown setting");
    FirmSetting s = settings.findById(key).orElse(new FirmSetting(key, d.value()));
    String old = s.value;
    s.value = d.value();
    s.updatedAt = Instant.now();
    settings.save(s);
    cases.audit(actor(r), "SETTING_CHANGE", "SETTING", key, old, s.value, "firm administrator");
  }

  @PostMapping("/admin/practice-areas")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> practice(HttpServletRequest r, @RequestBody Map<String, String> d) {
    String name = required(d, "name");
    if (practices.findAll().stream().anyMatch(p -> p.name.equalsIgnoreCase(name)))
      throw new IllegalArgumentException("Practice area already exists");
    PracticeArea p = practices.save(new PracticeArea(name));
    cases.audit(
        actor(r),
        "PRACTICE_AREA_CREATE",
        "PRACTICE_AREA",
        p.id,
        null,
        p.name,
        "firm administrator");
    return AuthService.map("id", p.id, "name", p.name, "active", p.active);
  }

  @PostMapping("/admin/practice-areas/{id}/active")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> practiceActive(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, Boolean> d) {
    PracticeArea p = practices.findById(id).orElseThrow();
    boolean before = p.active;
    p.active = Boolean.TRUE.equals(d.get("active"));
    practices.save(p);
    cases.audit(
        actor(r),
        "PRACTICE_AREA_STATUS",
        "PRACTICE_AREA",
        p.id,
        Boolean.toString(before),
        Boolean.toString(p.active),
        "firm administrator");
    return AuthService.map("id", p.id, "name", p.name, "active", p.active);
  }

  @GetMapping("/admin/integrations")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> integrations() {
    return AuthService.map(
        "apiKeys",
        apiKeys.findAll().stream()
            .map(
                k ->
                    AuthService.map(
                        "id",
                        k.id,
                        "name",
                        k.name,
                        "prefix",
                        k.prefix,
                        "enabled",
                        k.enabled,
                        "createdAt",
                        k.createdAt))
            .toList(),
        "webhooks",
        webhooks.findAll().stream()
            .sorted(Comparator.comparing((WebhookDelivery w) -> w.attemptedAt).reversed())
            .limit(50)
            .map(
                w ->
                    AuthService.map(
                        "id",
                        w.id,
                        "eventType",
                        w.eventType,
                        "targetUrl",
                        w.targetUrl,
                        "status",
                        w.status,
                        "responseCode",
                        w.responseCode,
                        "attemptedAt",
                        w.attemptedAt))
            .toList());
  }

  @PostMapping("/admin/integrations/api-keys")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> apiKey(HttpServletRequest r, @RequestBody Map<String, String> d)
      throws Exception {
    String name = required(d, "name");
    byte[] rawBytes = new byte[32];
    new java.security.SecureRandom().nextBytes(rawBytes);
    String raw = "lb_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
    ApiKey key = new ApiKey();
    key.name = name;
    key.prefix = raw.substring(0, 16);
    key.keyHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    apiKeys.save(key);
    cases.audit(
        actor(r),
        "API_KEY_CREATE",
        "API_KEY",
        key.id,
        null,
        key.prefix,
        "firm administrator; secret shown once");
    return AuthService.map("id", key.id, "name", key.name, "prefix", key.prefix, "secret", raw);
  }

  @PostMapping("/admin/integrations/webhooks/test")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> webhook(HttpServletRequest r, @RequestBody Map<String, String> d) {
    String target = required(d, "targetUrl");
    if (!target.matches("https?://(localhost|127\\.0\\.0\\.1)(:[0-9]{2,5})?(/.*)?"))
      throw new IllegalArgumentException("Local mock webhooks must target localhost");
    WebhookDelivery w = new WebhookDelivery();
    w.eventType = "matter.updated";
    w.targetUrl = target;
    w.status = "SIMULATED_DELIVERED";
    w.responseCode = 202;
    webhooks.save(w);
    cases.audit(
        actor(r),
        "WEBHOOK_TEST",
        "WEBHOOK",
        w.id,
        null,
        w.status,
        "firm administrator; local mock only");
    return AuthService.map("status", w.status, "responseCode", w.responseCode);
  }

  @PostMapping("/admin/integrations/webhooks/deliver")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> deliverWebhook(HttpServletRequest r, @RequestBody Map<String, String> d)
      throws Exception {
    String targetUrl = required(d, "targetUrl");
    String eventType = d.getOrDefault("eventType", "matter.updated");
    okhttp3.Request call =
        new okhttp3.Request.Builder()
            .url(targetUrl)
            .post(
                okhttp3.RequestBody.create(
                    "{\"event\":\"" + eventType + "\"}",
                    okhttp3.MediaType.parse("application/json")))
            .build();
    int responseCode;
    try (okhttp3.Response response = http.newCall(call).execute()) {
      responseCode = response.code();
    }
    WebhookDelivery w = new WebhookDelivery();
    w.eventType = eventType;
    w.targetUrl = targetUrl;
    w.status = responseCode < 400 ? "DELIVERED" : "FAILED";
    w.responseCode = responseCode;
    webhooks.save(w);
    cases.audit(
        actor(r),
        "WEBHOOK_DELIVER",
        "WEBHOOK",
        w.id,
        null,
        w.status,
        "firm administrator; partner endpoint");
    return AuthService.map("status", w.status, "responseCode", w.responseCode);
  }

  @GetMapping("/reference/practice-areas")
  ResponseEntity<List<String>> reference() throws Exception {
    List<String> values =
        practices.findAll().stream().filter(p -> p.active).map(p -> p.name).sorted().toList();
    String joined =
        String.join(
            ",",
            values); /* MD5 is non-cryptographic here: it is only an HTTP ETag for public reference data. */
    String etag =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("MD5").digest(joined.getBytes(StandardCharsets.UTF_8)));
    return ResponseEntity.ok().eTag('"' + etag + '"').body(values);
  }

  @PostMapping("/documents/{id}/convert/{profile}")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> convert(
      HttpServletRequest r, @PathVariable UUID id, @PathVariable String profile) throws Exception {
    return cases.convertDocument(actor(r), id, profile);
  }

  @PostMapping("/documents/{id}/access-code")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> accessCode(HttpServletRequest r, @PathVariable UUID id) {
    Actor a = actor(r);
    DocumentRecord d = cases.requireDocument(a, id);
    DocAccessCode c = new DocAccessCode();
    c.documentId = d.id;
    c.code = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
    c.expiresAt = Instant.now().plus(Duration.ofHours(48));
    accessCodes.save(c);
    cases.audit(a, "DOCUMENT_SHARING", "DOCUMENT", d.id, null, "access code issued", "attorney sharing");
    return AuthService.map("code", c.code);
  }

  @GetMapping("/documents/{id}/open")
  Map<String, Object> open(
      HttpServletRequest r,
      @PathVariable UUID id,
      @CookieValue(name = "lb_view", required = false) String pass) {
    Actor a = actor(r);
    DocumentRecord d = cases.requireDocument(a, id);
    DocAccessCode c =
        accessCodes.findFirstByDocumentIdAndUsedAtIsNullOrderByExpiresAtDesc(d.id).orElse(null);
    if (c == null
        || pass == null
        || !c.code.equals(pass)
        || c.expiresAt.isBefore(Instant.now()))
      throw new SecurityException("Access code is invalid or expired");
    c.usedAt = Instant.now();
    accessCodes.save(c);
    return cases.docView(d);
  }

  @PostMapping("/firm/matters/reassign")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> reassign(
      HttpServletRequest r, @RequestParam UUID fromId, @RequestParam UUID toId) {
    return cases.reassignMatters(actor(r), fromId, toId);
  }

  @PostMapping("/matters/{id}/case-files")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> caseFile(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> d)
      throws Exception {
    return cases.storeCaseFile(actor(r), id, d.getOrDefault("text", ""));
  }

  @PostMapping("/firm/notices/render")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> notice(@RequestParam String body) throws Exception {
    return AuthService.map("rendered", cases.renderNotice(body));
  }

  @GetMapping("/exhibits/text")
  Map<String, Object> exhibitText(HttpServletRequest r, @RequestHeader("X-Exhibit-Ref") String ref)
      throws Exception {
    return AuthService.map("text", cases.readExhibitText(actor(r), ref));
  }

  @PostMapping("/deadlines/preview")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> previewRule(HttpServletRequest r, @RequestHeader("X-Rule-Expr") String expr)
      throws Exception {
    return AuthService.map("result", cases.evaluateDeadlineRule(actor(r), expr));
  }

  @PostMapping("/matters/{id}/legacy-bundle")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> legacyBundle(
      HttpServletRequest r, @PathVariable UUID id, @RequestParam String bundle) throws Exception {
    return cases.restoreLegacyBundle(actor(r), id, bundle);
  }

  @PostMapping(value = "/firm/filing-receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> filingReceipts(HttpServletRequest r, @RequestPart MultipartFile file)
      throws Exception {
    return cases.importFilingReceipts(actor(r), file);
  }

  @PostMapping(value = "/firm/profile-bundle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> profileBundle(HttpServletRequest r, @RequestPart MultipartFile file)
      throws Exception {
    if (file == null || file.isEmpty() || file.getSize() > 1_000_000)
      throw new IllegalArgumentException("Profile bundle must be between 1 byte and 1 MB");
    Object node = new Yaml().load(file.getInputStream());
    if (!(node instanceof Map<?, ?> profile))
      throw new IllegalArgumentException("Profile bundle must be a mapping");
    int applied = 0;
    for (Map.Entry<?, ?> e : profile.entrySet()) {
      String k = String.valueOf(e.getKey());
      if (k.startsWith("firm.") || k.startsWith("retention.") || k.startsWith("reminders.")) {
        save(k, String.valueOf(e.getValue()));
        applied++;
      }
    }
    cases.audit(
        actor(r),
        "BRANDING_BUNDLE",
        "SETTING",
        (String) null,
        null,
        "applied=" + applied,
        "firm administrator");
    return AuthService.map("applied", applied);
  }

  @PostMapping("/firm/profile-bundle/apply")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> profileApply(HttpServletRequest r, @RequestBody TextDto d) {
    Object node = new Yaml().load(d.value());
    if (!(node instanceof Map<?, ?> profile))
      throw new IllegalArgumentException("Profile bundle must be a mapping");
    int applied = 0;
    for (Map.Entry<?, ?> e : profile.entrySet()) {
      String k = String.valueOf(e.getKey());
      if (k.startsWith("firm.") || k.startsWith("retention.") || k.startsWith("reminders.")) {
        save(k, String.valueOf(e.getValue()));
        applied++;
      }
    }
    cases.audit(
        actor(r),
        "BRANDING_BUNDLE",
        "SETTING",
        (String) null,
        null,
        "applied=" + applied,
        "firm administrator");
    return AuthService.map("applied", applied);
  }

  @GetMapping("/directory/registry")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> registry(@CookieValue(name = "lb_staff", required = false) String handle)
      throws Exception {
    return AuthService.map("staff", directory.resolve(handle == null ? "" : handle));
  }

  @PostMapping("/firm/message-templates/preview")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  Map<String, Object> messageTemplate(@RequestBody TextDto d) throws Exception {
    Handlebars hb = new Handlebars();
    String rendered =
        hb.compileInline(d.value())
            .apply(AuthService.map("firm", "Lexbridge Legal", "year", Year.now().getValue()));
    return AuthService.map("rendered", rendered);
  }

  @GetMapping(value = "/firm/register/view", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  void registerView(
      @RequestHeader(value = "X-Register-Caption", defaultValue = "") String caption,
      HttpServletResponse resp)
      throws Exception {
    resp.setContentType(MediaType.TEXT_HTML_VALUE);
    resp.getWriter().write("<h2>Court register</h2><p>" + caption + "</p>");
  }

  @PostMapping("/matters/{id}/case-file-info")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  Map<String, Object> caseFileInfo(
      HttpServletRequest r, @PathVariable UUID id, @RequestBody Map<String, String> d)
      throws Exception {
    cases.requireMatter(actor(r), id, false);
    return cases.caseFileInfo(actor(r), d.getOrDefault("storedName", ""));
  }

  @PostMapping("/registry/signin")
  Map<String, Object> registrySignIn(
      @RequestHeader("X-Registry-Account") String account,
      @RequestHeader("X-Registry-Phrase") String phrase) {
    return auth.registrySignIn(account, phrase);
  }

  private static String required(Map<String, String> m, String k) {
    String v = m.get(k);
    if (v == null || v.isBlank()) throw new IllegalArgumentException(k + " is required");
    return v.trim();
  }

  private static String cell(String v) {
    return '"' + Objects.requireNonNullElse(v, "").replace("\"", "\"\"") + '"';
  }

  private static ResponseEntity<byte[]> csv(String s, String n) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + n + "\"")
        .body(s.getBytes(StandardCharsets.UTF_8));
  }

  private static List<String> parseCsv(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder b = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          b.append('"');
          i++;
        } else quoted = !quoted;
      } else if (ch == ',' && !quoted) {
        out.add(b.toString().trim());
        b.setLength(0);
      } else b.append(ch);
    }
    out.add(b.toString().trim());
    return out;
  }
}

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(SecurityException.class)
  ResponseEntity<Map<String, String>> denied(SecurityException e) {
    return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  ResponseEntity<Map<String, String>> bad(RuntimeException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<Map<String, String>> missing(NoSuchElementException e) {
    return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(javax.crypto.BadPaddingException.class)
  ResponseEntity<Map<String, String>> sealed(javax.crypto.BadPaddingException e) {
    return ResponseEntity.status(400).body(Map.of("error", "sealed note could not be opened"));
  }
}
