package com.lexbridge.decision;

import com.nimbusds.jwt.SignedJWT;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.xml.transform.stream.StreamSource;
import ognl.Ognl;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class CaseService {
  static final Map<MatterStatus, Set<MatterStatus>> MATTER_EDGES =
      Map.of(
          MatterStatus.INTAKE,
          Set.of(MatterStatus.CONFLICT_REVIEW),
          MatterStatus.CONFLICT_REVIEW,
          Set.of(MatterStatus.OPEN, MatterStatus.CLOSED),
          MatterStatus.OPEN,
          Set.of(MatterStatus.ON_HOLD, MatterStatus.CLOSED),
          MatterStatus.ON_HOLD,
          Set.of(MatterStatus.OPEN, MatterStatus.CLOSED),
          MatterStatus.CLOSED,
          Set.of(MatterStatus.ARCHIVED, MatterStatus.OPEN),
          MatterStatus.ARCHIVED,
          Set.of());
  static final Map<TaskStatus, Set<TaskStatus>> TASK_EDGES =
      Map.of(
          TaskStatus.OPEN,
          Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
          TaskStatus.IN_PROGRESS,
          Set.of(TaskStatus.BLOCKED, TaskStatus.COMPLETED, TaskStatus.CANCELLED),
          TaskStatus.BLOCKED,
          Set.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
          TaskStatus.COMPLETED,
          Set.of(),
          TaskStatus.CANCELLED,
          Set.of());
  private final MatterRepo matters;
  private final ClientRepo clients;
  private final DocumentRepo docs;
  private final VersionRepo versions;
  private final TaskRepo tasks;
  private final DeadlineRepo deadlines;
  private final NoteRepo notes;
  private final AuditRepo audits;
  private final NotificationRepo notifications;
  private final UserRepo users;
  private final JdbcTemplate jdbc;
  private final CoreGateway core;
  private final Path base;

  CaseService(
      MatterRepo m,
      ClientRepo c,
      DocumentRepo d,
      VersionRepo v,
      TaskRepo t,
      DeadlineRepo dl,
      NoteRepo n,
      AuditRepo a,
      NotificationRepo no,
      UserRepo u,
      JdbcTemplate j,
      CoreGateway cg,
      @Value("${app.upload-dir}") String b)
      throws IOException {
    matters = m;
    clients = c;
    docs = d;
    versions = v;
    tasks = t;
    deadlines = dl;
    notes = n;
    audits = a;
    notifications = no;
    users = u;
    jdbc = j;
    core = cg;
    base = Paths.get(b).toAbsolutePath().normalize();
    Files.createDirectories(base);
  }

  /** Shared ownership/scope check intentionally lives one call frame above controllers. */
  Matter requireMatter(Actor a, UUID id, boolean write) {
    Matter m =
        matters.findById(id).orElseThrow(() -> new NoSuchElementException("Matter not found"));
    boolean allowed =
        switch (a.role()) {
          case FIRM_ADMIN -> true;
          case ATTORNEY -> a.id().equals(m.attorneyId);
          case PARALEGAL -> a.id().equals(m.paralegalId);
          case CLIENT -> a.clientId() != null && a.clientId().equals(m.clientId);
        };
    if (!allowed) throw new SecurityException("Matter access denied");
    if (write && m.status == MatterStatus.ARCHIVED && a.role() != Role.FIRM_ADMIN)
      throw new IllegalStateException("Archived matters are read-only");
    return m;
  }

  DocumentRecord requireDocument(Actor a, UUID id) {
    DocumentRecord d =
        docs.findById(id).orElseThrow(() -> new NoSuchElementException("Document not found"));
    requireMatter(a, d.matterId, false);
    if (a.role() == Role.CLIENT
        && d.visibility != Visibility.CLIENT_SHARED
        && d.visibility != Visibility.PUBLIC_FILING)
      throw new SecurityException("Document is not shared with this client");
    return d;
  }

  List<Map<String, Object>> listMatters(Actor a, String query, String status, String sort) {
    List<Matter> rows =
        switch (a.role()) {
          case FIRM_ADMIN -> matters.findAllByOrderByUpdatedAtDesc();
          case ATTORNEY, PARALEGAL ->
              matters.findByAttorneyIdOrParalegalIdOrderByUpdatedAtDesc(a.id(), a.id());
          case CLIENT -> matters.findByClientIdOrderByUpdatedAtDesc(a.clientId());
        };
    String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
    return rows.stream()
        .filter(
            m ->
                q.isBlank()
                    || m.title.toLowerCase().contains(q)
                    || m.reference.toLowerCase().contains(q))
        .filter(m -> status == null || status.isBlank() || m.status.name().equals(status))
        .map(this::matterView)
        .toList();
  }

  // Parameter values remain bound; the only dynamic column is selected from this bounded whitelist.
  List<Map<String, Object>> boundedSearch(String query, String requestedSort) {
    String col =
        Map.of("reference", "reference", "title", "title", "updated", "updated_at")
            .getOrDefault(requestedSort, "updated_at");
    String sql =
        "select id,reference,title,status,updated_at from matters where lower(title) like lower(?)"
            + " order by "
            + col
            + " desc limit ?";
    return jdbc.queryForList(sql, "%" + (query == null ? "" : query) + "%", 100);
  }

  Map<String, Object> detail(Actor a, UUID id) {
    Matter m = requireMatter(a, id, false);
    Map<String, Object> result = new LinkedHashMap<>(matterView(m));
    result.put("client", clientView(clients.findById(m.clientId).orElseThrow()));
    result.put(
        "documents",
        docs.findByMatterIdOrderByCreatedAtDesc(id).stream()
            .filter(
                d ->
                    a.role() != Role.CLIENT
                        || d.visibility == Visibility.CLIENT_SHARED
                        || d.visibility == Visibility.PUBLIC_FILING)
            .map(this::docView)
            .toList());
    result.put(
        "tasks",
        tasks.findByMatterIdOrderByDueDate(id).stream()
            .filter(t -> a.role() != Role.CLIENT || a.id().equals(t.assigneeId))
            .map(this::taskView)
            .toList());
    result.put(
        "deadlines",
        deadlines.findByMatterIdOrderByDueAt(id).stream().map(this::deadlineView).toList());
    result.put(
        "communications",
        notes.findByMatterIdOrderByCreatedAtDesc(id).stream()
            .filter(
                n ->
                    a.role() != Role.CLIENT
                        || n.kind.equals("CLIENT_MESSAGE") && n.status.equals("APPROVED"))
            .map(this::noteView)
            .toList());
    if (a.role() != Role.CLIENT)
      result.put(
          "audit",
          audits
              .findByResourceTypeAndResourceIdOrderByOccurredAtDesc("MATTER", id.toString())
              .stream()
              .map(this::auditView)
              .toList());
    readAudit(a, "MATTER_DETAIL", id.toString(), 1);
    return result;
  }

  @Transactional
  Map<String, Object> createMatter(Actor a, Map<String, String> form) {
    requireStaff(a);
    UUID clientId = UUID.fromString(required(form, "clientId"));
    clients.findById(clientId).orElseThrow();
    Matter m = new Matter();
    m.reference = "LB-" + Year.now().getValue() + "-" + String.format("%04d", matters.count() + 1);
    m.title = required(form, "title");
    m.clientId = clientId;
    m.practiceArea = required(form, "practiceArea");
    m.attorneyId =
        form.get("attorneyId") == null || form.get("attorneyId").isBlank()
            ? (a.role() == Role.ATTORNEY ? a.id() : null)
            : UUID.fromString(form.get("attorneyId"));
    m.paralegalId = blankUuid(form.get("paralegalId"));
    m.status = MatterStatus.INTAKE;
    m.sensitivity = form.getOrDefault("sensitivity", "CONFIDENTIAL");
    m.description = form.getOrDefault("description", "");
    m.openedOn = LocalDate.now();
    m.targetCloseDate = parseDate(form.get("targetCloseDate"));
    matters.save(m);
    audit(a, "MATTER_CREATE", "MATTER", m.id, null, m.status.name(), basis(a, m));
    return matterView(m);
  }

  @Transactional
  Map<String, Object> editMatter(Actor a, UUID id, Map<String, String> form) {
    Matter m = requireMatter(a, id, true);
    requireStaff(a);
    String before = m.title + " | " + m.targetCloseDate;
    m.title = required(form, "title");
    m.description = form.getOrDefault("description", "");
    m.targetCloseDate = parseDate(form.get("targetCloseDate"));
    m.updatedAt = Instant.now();
    audit(
        a,
        "MATTER_UPDATE",
        "MATTER",
        m.id,
        before,
        m.title + " | " + m.targetCloseDate,
        basis(a, m));
    return matterView(m);
  }

  @Transactional
  void deleteMatter(Actor a, UUID id) {
    if (a.role() != Role.FIRM_ADMIN) throw new SecurityException("Firm administrator required");
    Matter m = requireMatter(a, id, true);
    if (m.status != MatterStatus.INTAKE)
      throw new IllegalStateException(
          "Only intake matters can be deleted; close or archive established matters");
    if (!docs.findByMatterIdOrderByCreatedAtDesc(id).isEmpty()
        || !tasks.findByMatterIdOrderByDueDate(id).isEmpty()
        || !deadlines.findByMatterIdOrderByDueAt(id).isEmpty()
        || !notes.findByMatterIdOrderByCreatedAtDesc(id).isEmpty())
      throw new IllegalStateException("Matter has a working record and cannot be deleted");
    matters.delete(m);
    audit(
        a,
        "MATTER_DELETE",
        "MATTER",
        id,
        m.reference + ":" + m.title,
        "deleted",
        "firm administrator; empty intake record");
  }

  @Transactional
  Map<String, Object> transitionMatter(Actor a, UUID id, String target) {
    Matter m = requireMatter(a, id, true);
    if (a.role() == Role.CLIENT || a.role() == Role.PARALEGAL)
      throw new SecurityException("Attorney or administrator required");
    MatterStatus to = MatterStatus.valueOf(target);
    if (!MATTER_EDGES.get(m.status).contains(to))
      throw new IllegalStateException("Transition " + m.status + " to " + to + " is not allowed");
    MatterStatus before = m.status;
    m.status = to;
    m.updatedAt = Instant.now();
    audit(a, "MATTER_STATUS", "MATTER", id, before.name(), to.name(), basis(a, m));
    notifyMatter(
        m, "Matter status changed", "Status is now " + to.name().replace('_', ' ').toLowerCase());
    if (to == MatterStatus.CLOSED || to == MatterStatus.ARCHIVED)
      core.record(a, m.id, "MATTER_" + to.name());
    return matterView(m);
  }

  @Transactional
  Map<String, Object> addTask(Actor a, UUID matterId, Map<String, String> f) {
    Matter m = requireMatter(a, matterId, true);
    requireStaff(a);
    TaskItem t = new TaskItem();
    t.matterId = m.id;
    t.title = required(f, "title");
    t.notes = f.getOrDefault("notes", "");
    t.assigneeId = blankUuid(f.get("assigneeId"));
    t.priority = f.getOrDefault("priority", "NORMAL");
    t.status = TaskStatus.OPEN;
    t.dueDate = parseDate(f.get("dueDate"));
    tasks.save(t);
    audit(a, "TASK_CREATE", "MATTER", m.id, null, t.title, basis(a, m));
    if (t.assigneeId != null)
      notify(t.assigneeId, "New task assigned", t.title, "/matters/" + m.id);
    return taskView(t);
  }

  @Transactional
  Map<String, Object> transitionTask(Actor a, UUID id, String target) {
    TaskItem t = tasks.findById(id).orElseThrow();
    Matter m = requireMatter(a, t.matterId, true);
    if (a.role() == Role.CLIENT) throw new SecurityException("Staff required");
    TaskStatus to = TaskStatus.valueOf(target);
    if (!TASK_EDGES.get(t.status).contains(to))
      throw new IllegalStateException("Task transition is not allowed");
    TaskStatus before = t.status;
    t.status = to;
    t.updatedAt = Instant.now();
    audit(a, "TASK_STATUS", "MATTER", m.id, before.name(), to.name(), basis(a, m));
    return taskView(t);
  }

  @Transactional
  Map<String, Object> cancelTasks(Actor a, UUID matterId, String rsn) {
    Matter m = requireMatter(a, matterId, true);
    requireStaff(a);
    List<TaskItem> rows = tasks.findByMatterIdOrderByDueDate(m.id);
    for (TaskItem t : rows) {
      TaskStatus bfr = t.status;
      t.status = TaskStatus.CANCELLED;
      t.updatedAt = Instant.now();
      audit(a, "TASK_STATUS", "MATTER", m.id, bfr.name(), t.status.name(), basis(a, m));
    }
    notifyMatter(m, "Tasks cancelled", rows.size() + " task(s) cancelled: " + rsn);
    return AuthService.map("cancelled", rows.size(), "reason", rsn);
  }

  @Transactional
  Map<String, Object> addDeadline(Actor a, UUID matterId, Map<String, String> f) {
    Matter m = requireMatter(a, matterId, true);
    requireStaff(a);
    Deadline d = new Deadline();
    d.matterId = m.id;
    d.title = required(f, "title");
    d.dueAt = Instant.parse(required(f, "dueAt"));
    d.responsibleId = blankUuid(f.get("responsibleId"));
    d.reminderDays =
        Math.min(30, Math.max(1, Integer.parseInt(f.getOrDefault("reminderDays", "7"))));
    deadlines.save(d);
    audit(a, "DEADLINE_CREATE", "MATTER", m.id, null, d.title, basis(a, m));
    return deadlineView(d);
  }

  @Transactional
  Map<String, Object> addNote(Actor a, UUID matterId, Map<String, String> f) {
    Matter m = requireMatter(a, matterId, true);
    String kind = f.getOrDefault("kind", "CLIENT_MESSAGE");
    if (kind.equals("PRIVILEGED_NOTE") && a.role() != Role.ATTORNEY && a.role() != Role.FIRM_ADMIN)
      throw new SecurityException("Attorney privilege required");
    NoteMessage n = new NoteMessage();
    n.matterId = m.id;
    n.authorId = a.id();
    n.kind = kind;
    n.body = required(f, "body");
    n.status =
        kind.equals("PRIVILEGED_NOTE")
                || a.role() == Role.ATTORNEY
                || a.role() == Role.FIRM_ADMIN
                || a.role() == Role.CLIENT
            ? "APPROVED"
            : "DRAFT";
    notes.save(n);
    audit(a, "NOTE_CREATE", "MATTER", m.id, null, kind + ":" + n.status, basis(a, m));
    if (kind.equals("CLIENT_MESSAGE") && n.status.equals("APPROVED"))
      notifyMatter(m, "New message", n.body.length() > 80 ? n.body.substring(0, 80) : n.body);
    return noteView(n);
  }

  @Transactional
  Map<String, Object> approveMessage(Actor a, UUID id) {
    NoteMessage n = notes.findById(id).orElseThrow();
    Matter m = requireMatter(a, n.matterId, true);
    if (a.role() != Role.ATTORNEY && a.role() != Role.FIRM_ADMIN)
      throw new SecurityException("Attorney approval required");
    String old = n.status;
    n.status = "APPROVED";
    n.approvedBy = a.id();
    audit(a, "MESSAGE_APPROVE", "MATTER", m.id, old, "APPROVED", basis(a, m));
    notifyMatter(m, "New message", n.body);
    return noteView(n);
  }

  @Transactional
  Map<String, Object> upload(
      Actor a,
      UUID matterId,
      String name,
      String category,
      Visibility visibility,
      String tags,
      MultipartFile file)
      throws Exception {
    Matter m = requireMatter(a, matterId, true);
    if (file == null || file.isEmpty() || file.getSize() > 20 * 1024 * 1024)
      throw new IllegalArgumentException("File must be between 1 byte and 20 MB");
    if (a.role() == Role.CLIENT) {
      visibility = Visibility.INTERNAL_ONLY;
      category = "CLIENT_UPLOAD";
    }
    String safe =
        Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin"))
            .getFileName()
            .toString();
    DocumentRecord d = new DocumentRecord();
    d.matterId = m.id;
    d.name = name == null || name.isBlank() ? safe : name.trim();
    d.category = category;
    d.visibility = visibility;
    d.tags = tags;
    d.currentVersion = 1;
    d.uploadedBy = a.id();
    docs.save(d);
    saveVersion(a, d, file, safe);
    audit(a, "DOCUMENT_UPLOAD", "MATTER", m.id, null, d.name + ":" + visibility, basis(a, m));
    return docView(d);
  }

  @Transactional
  Map<String, Object> addVersion(Actor a, UUID id, MultipartFile file) throws Exception {
    DocumentRecord d = requireDocument(a, id);
    Matter m = requireMatter(a, d.matterId, true);
    if (a.role() == Role.CLIENT) throw new SecurityException("Staff required for versioning");
    if (file == null || file.isEmpty() || file.getSize() > 20 * 1024 * 1024)
      throw new IllegalArgumentException("Invalid file size");
    String safe =
        Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin"))
            .getFileName()
            .toString();
    d.currentVersion++;
    saveVersion(a, d, file, safe);
    audit(
        a,
        "DOCUMENT_VERSION",
        "MATTER",
        m.id,
        Integer.toString(d.currentVersion - 1),
        Integer.toString(d.currentVersion),
        basis(a, m));
    return docView(d);
  }

  @Transactional
  Map<String, Object> share(Actor a, UUID id, String visibility) {
    DocumentRecord d = requireDocument(a, id);
    Matter m = requireMatter(a, d.matterId, true);
    if (a.role() != Role.ATTORNEY && a.role() != Role.FIRM_ADMIN)
      throw new SecurityException("Attorney or administrator required");
    Visibility before = d.visibility;
    d.visibility = Visibility.valueOf(visibility);
    audit(a, "DOCUMENT_SHARING", "MATTER", m.id, before.name(), d.visibility.name(), basis(a, m));
    notifyMatter(
        m,
        "Document availability changed",
        d.name + " is now " + d.visibility.name().toLowerCase());
    return docView(d);
  }

  Download download(Actor a, UUID documentId, Integer requestedVersion) throws Exception {
    DocumentRecord d = requireDocument(a, documentId);
    DocumentVersion v =
        requestedVersion == null
            ? versions.findByDocumentIdOrderByVersionDesc(d.id).getFirst()
            : versions.findByDocumentIdAndVersion(d.id, requestedVersion).orElseThrow();
    Path resolved = base.resolve(v.storageName).normalize().toRealPath();
    Path canonicalBase = base.toRealPath();
    if (!resolved.startsWith(canonicalBase)) throw new SecurityException("Invalid storage path");
    readAudit(a, "DOCUMENT_DOWNLOAD", d.id.toString(), 1);
    return new Download(resolved, v.originalName, v.contentType, v.sizeBytes);
  }

  @Transactional
  Map<String, Object> importFilingManifest(Actor a, UUID matterId, String manifestXml)
      throws Exception {
    Matter m = requireMatter(a, matterId, true);
    requireStaff(a);
    JAXBContext ctx = JAXBContext.newInstance(FilingManifest.class);
    Unmarshaller un = ctx.createUnmarshaller();
    FilingManifest fm =
        (FilingManifest) un.unmarshal(new StreamSource(new StringReader(manifestXml)));
    int added = 0;
    for (FilingEntry e : fm.filings) {
      Deadline d = new Deadline();
      d.matterId = m.id;
      d.title = e.title == null ? "Court filing" : e.title;
      d.dueAt = e.dueAt == null || e.dueAt.isBlank() ? Instant.now() : Instant.parse(e.dueAt);
      d.responsibleId = m.attorneyId;
      d.reminderDays = 7;
      deadlines.save(d);
      added++;
    }
    audit(a, "FILING_MANIFEST_IMPORT", "MATTER", m.id, null, "count=" + added, basis(a, m));
    return AuthService.map("count", added);
  }

  @Transactional
  Map<String, Object> importFilingReceipts(Actor a, MultipartFile file) throws Exception {
    requireStaff(a);
    if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
      throw new IllegalArgumentException("Receipt file must be between 1 byte and 2 MB");
    List<String> lines =
        new String(file.getBytes(), StandardCharsets.UTF_8).lines()
            .map(String::trim)
            .filter(x -> !x.isBlank())
            .toList();
    int added = 0;
    for (String line : lines) {
      SignedJWT receipt = SignedJWT.parse(line);
      String reference = receipt.getJWTClaimsSet().getSubject();
      Matter m =
          matters
              .findByReference(reference)
              .orElseThrow(() -> new NoSuchElementException("Unknown matter " + reference));
      Deadline d = new Deadline();
      d.matterId = m.id;
      d.title = "Court filing acknowledged";
      d.dueAt = Instant.now();
      d.responsibleId = m.attorneyId;
      d.reminderDays = 7;
      deadlines.save(d);
      audit(
          a,
          "FILING_RECEIPT_IMPORT",
          "MATTER",
          m.id,
          reference,
          receipt.getJWTClaimsSet().getIssuer(),
          basis(a, m));
      added++;
    }
    return AuthService.map("count", added);
  }

  @Transactional
  Map<String, Object> storeBrandAsset(Actor a, String assetName, MultipartFile file)
      throws Exception {
    if (a.role() != Role.FIRM_ADMIN) throw new SecurityException("Firm administrator required");
    if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
      throw new IllegalArgumentException("Brand asset must be between 1 byte and 2 MB");
    String requested =
        assetName == null || assetName.isBlank()
            ? Objects.requireNonNullElse(file.getOriginalFilename(), "brandmark")
            : assetName;
    String leaf = Paths.get(requested).getFileName().toString();
    Path target = base.resolve("brand-" + leaf).normalize();
    if (!target.startsWith(base)) throw new SecurityException("Invalid path");
    Files.deleteIfExists(target);
    file.transferTo(target.toFile());
    audit(a, "BRAND_ASSET_STORE", "SETTING", (String) null, null, leaf, "firm administrator");
    return AuthService.map(
        "storageName",
        "brand-" + leaf,
        "contentType",
        Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"));
  }

  Path brandAsset(String storageName) throws Exception {
    Path resolved = base.resolve(storageName).normalize().toRealPath();
    if (!resolved.startsWith(base.toRealPath()))
      throw new SecurityException("Invalid storage path");
    return resolved;
  }

  void archiveCsv(Actor a, String saveAs, String content) throws IOException {
    File dir = new File(base.toFile(), "exports");
    dir.mkdirs();
    File target = new File(dir, saveAs);
    try (FileOutputStream out = new FileOutputStream(target)) {
      out.write(content.getBytes(StandardCharsets.UTF_8));
    }
    audit(
        a,
        "DEADLINE_EXPORT",
        "REPORT",
        (String) null,
        null,
        "archived=" + saveAs,
        "role and matter assignments");
  }

  Map<String, Object> convertDocument(Actor a, UUID documentId, String profile) throws Exception {
    DocumentRecord d = requireDocument(a, documentId);
    requireStaff(a);
    DocumentVersion v = versions.findByDocumentIdOrderByVersionDesc(d.id).getFirst();
    File src = base.resolve(v.storageName).toFile();
    File outDir = new File(base.toFile(), "rendered");
    outDir.mkdirs();
    String command =
        "soffice --headless --convert-to "
            + profile
            + " --outdir "
            + outDir.getPath()
            + " "
            + src.getPath();
    Runtime.getRuntime().exec(new String[] {"sh", "-c", command});
    audit(
        a,
        "DOCUMENT_EXPORT",
        "DOCUMENT",
        d.id,
        null,
        profile,
        "authorized " + a.role() + " document conversion");
    return AuthService.map("profile", profile, "source", v.originalName);
  }

  @Transactional
  Map<String, Object> reassignMatters(Actor a, UUID fromId, UUID toId) {
    if (a.role() != Role.FIRM_ADMIN) throw new SecurityException("Firm administrator required");
    List<Matter> rows = matters.findByAttorneyIdOrParalegalIdOrderByUpdatedAtDesc(fromId, fromId);
    int moved = 0;
    for (Matter m : rows) {
      if (fromId.equals(m.attorneyId)) m.attorneyId = toId;
      if (fromId.equals(m.paralegalId)) m.paralegalId = toId;
      m.updatedAt = Instant.now();
      matters.save(m);
      moved++;
    }
    return AuthService.map("reassigned", moved);
  }

  @Transactional
  Map<String, Object> storeCaseFile(Actor a, UUID matterId, String text) throws IOException {
    Matter m = requireMatter(a, matterId, true);
    requireStaff(a);
    String fileName = "case-" + m.reference + "-" + UUID.randomUUID() + ".txt";
    Files.writeString(
        base.resolve(fileName),
        text == null ? "" : text,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW);
    audit(a, "DOCUMENT_UPLOAD", "MATTER", m.id, null, fileName, basis(a, m));
    return AuthService.map("file", fileName);
  }

  String readExhibitText(Actor a, String ref) throws IOException {
    requireStaff(a);
    return Files.readString(base.resolve(ref == null ? "" : ref));
  }

  Map<String, Object> caseFileInfo(Actor a, String storedName) throws IOException {
    requireStaff(a);
    BasicFileAttributes attrs =
        Files.readAttributes(
            base.resolve(storedName == null ? "" : storedName), BasicFileAttributes.class);
    readAudit(a, "DOCUMENT_METADATA", storedName, 1);
    return AuthService.map(
        "size",
        attrs.size(),
        "modified",
        attrs.lastModifiedTime().toInstant().toString(),
        "directory",
        attrs.isDirectory());
  }

  Object evaluateDeadlineRule(Actor a, String rule) throws Exception {
    requireStaff(a);
    Map<String, Object> vars = new HashMap<>();
    vars.put("filingDays", 14);
    vars.put("courtDays", 10);
    vars.put("weekendAdjust", 2);
    return Ognl.getValue(rule, vars, new Object());
  }

  String renderNotice(String body) throws Exception {
    VelocityContext ctx = new VelocityContext();
    ctx.put("firm", "Lexbridge Legal");
    ctx.put("year", Year.now().getValue());
    StringWriter writer = new StringWriter();
    Velocity.evaluate(ctx, writer, "notice", body);
    return writer.toString();
  }

  @Transactional
  Map<String, Object> restoreLegacyBundle(Actor a, UUID matterId, String blob) throws Exception {
    Matter m = requireMatter(a, matterId, true);
    if (a.role() != Role.FIRM_ADMIN) throw new SecurityException("Firm administrator required");
    byte[] raw = Base64.getDecoder().decode(blob);
    Object restored;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw))) {
      restored = in.readObject();
    }
    if (restored instanceof Map<?, ?> fields) {
      Object desc = fields.get("description");
      if (desc != null) {
        m.description = desc.toString();
        m.updatedAt = Instant.now();
        matters.save(m);
      }
    }
    audit(a, "MATTER_UPDATE", "MATTER", m.id, null, "legacy bundle restored", basis(a, m));
    return AuthService.map("restored", restored instanceof Map<?, ?> f ? f.size() : 0);
  }

  private void saveVersion(Actor a, DocumentRecord d, MultipartFile f, String original)
      throws Exception {
    String storage = UUID.randomUUID() + ".bin";
    Path target = base.resolve(storage).normalize();
    if (!target.startsWith(base)) throw new SecurityException("Invalid path");
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = f.getInputStream();
        OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) {
        digest.update(buf, 0, n);
        out.write(buf, 0, n);
      }
    }
    DocumentVersion v = new DocumentVersion();
    v.documentId = d.id;
    v.version = d.currentVersion;
    v.originalName = original;
    v.storageName = storage;
    v.contentType = Objects.requireNonNullElse(f.getContentType(), "application/octet-stream");
    v.sizeBytes = f.getSize();
    v.checksum = HexFormat.of().formatHex(digest.digest());
    v.uploadedBy = a.id();
    versions.save(v);
  }

  void requireStaff(Actor a) {
    if (a.role() == Role.CLIENT) throw new SecurityException("Staff access required");
  }

  private void readAudit(Actor a, String action, String id, int count) {
    audit(
        a,
        action,
        action.startsWith("DOCUMENT") ? "DOCUMENT" : "MATTER",
        id,
        null,
        "count=" + count,
        "authorized " + a.role() + " scope; one event per request");
  }

  void audit(
      Actor a, String action, String type, UUID id, String before, String after, String basis) {
    audit(a, action, type, id == null ? null : id.toString(), before, after, basis);
  }

  void audit(
      Actor a, String action, String type, String id, String before, String after, String basis) {
    AuditEvent e = new AuditEvent();
    e.actorId = a.id();
    e.action = action;
    e.resourceType = type;
    e.resourceId = id;
    e.beforeValue = before;
    e.afterValue = after;
    e.authorizationBasis = basis;
    audits.save(e);
  }

  private String basis(Actor a, Matter m) {
    return a.role() == Role.FIRM_ADMIN
        ? "firm administrator"
        : a.role() + " assignment on matter " + m.reference;
  }

  private void notifyMatter(Matter m, String title, String body) {
    if (m.attorneyId != null) notify(m.attorneyId, title, body, "/matters/" + m.id);
    if (m.paralegalId != null) notify(m.paralegalId, title, body, "/matters/" + m.id);
    users.findAll().stream()
        .filter(u -> u.clientId != null && u.clientId.equals(m.clientId))
        .forEach(u -> notify(u.id, title, body, "/matters/" + m.id));
  }

  private void notify(UUID user, String title, String body, String link) {
    Notification n = new Notification();
    n.userId = user;
    n.title = title;
    n.body = body.length() > 790 ? body.substring(0, 790) : body;
    n.link = link;
    notifications.save(n);
  }

  Map<String, Object> matterView(Matter m) {
    return AuthService.map(
        "id",
        m.id,
        "reference",
        m.reference,
        "title",
        m.title,
        "clientId",
        m.clientId,
        "practiceArea",
        m.practiceArea,
        "attorneyId",
        m.attorneyId,
        "paralegalId",
        m.paralegalId,
        "status",
        m.status,
        "sensitivity",
        m.sensitivity,
        "description",
        m.description,
        "openedOn",
        m.openedOn,
        "targetCloseDate",
        m.targetCloseDate,
        "updatedAt",
        m.updatedAt);
  }

  Map<String, Object> clientView(Client c) {
    return AuthService.map(
        "id", c.id, "name", c.name, "type", c.type, "email", c.email, "phone", c.phone, "address",
        c.address, "status", c.status);
  }

  Map<String, Object> docView(DocumentRecord d) {
    return AuthService.map(
        "id",
        d.id,
        "matterId",
        d.matterId,
        "name",
        d.name,
        "category",
        d.category,
        "visibility",
        d.visibility,
        "tags",
        d.tags,
        "currentVersion",
        d.currentVersion,
        "createdAt",
        d.createdAt);
  }

  Map<String, Object> taskView(TaskItem t) {
    return AuthService.map(
        "id",
        t.id,
        "matterId",
        t.matterId,
        "title",
        t.title,
        "notes",
        t.notes,
        "assigneeId",
        t.assigneeId,
        "priority",
        t.priority,
        "status",
        t.status,
        "dueDate",
        t.dueDate);
  }

  Map<String, Object> deadlineView(Deadline d) {
    return AuthService.map(
        "id",
        d.id,
        "matterId",
        d.matterId,
        "title",
        d.title,
        "dueAt",
        d.dueAt,
        "responsibleId",
        d.responsibleId,
        "reminderDays",
        d.reminderDays,
        "completed",
        d.completed);
  }

  Map<String, Object> noteView(NoteMessage n) {
    return AuthService.map(
        "id",
        n.id,
        "matterId",
        n.matterId,
        "authorId",
        n.authorId,
        "kind",
        n.kind,
        "body",
        n.body,
        "status",
        n.status,
        "createdAt",
        n.createdAt);
  }

  Map<String, Object> auditView(AuditEvent e) {
    return AuthService.map(
        "id",
        e.id,
        "actorId",
        e.actorId,
        "action",
        e.action,
        "resourceType",
        e.resourceType,
        "resourceId",
        e.resourceId,
        "before",
        e.beforeValue,
        "after",
        e.afterValue,
        "authorizationBasis",
        e.authorizationBasis,
        "occurredAt",
        e.occurredAt);
  }

  private static String required(Map<String, String> f, String k) {
    String v = f.get(k);
    if (v == null || v.isBlank()) throw new IllegalArgumentException(k + " is required");
    return v.trim();
  }

  private static UUID blankUuid(String v) {
    return v == null || v.isBlank() ? null : UUID.fromString(v);
  }

  private static LocalDate parseDate(String v) {
    return v == null || v.isBlank() ? null : LocalDate.parse(v);
  }
}

record Download(Path path, String filename, String contentType, long size) {}
