package com.lexbridge.decision;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SeedData implements CommandLineRunner {
  private final UserRepo users;
  private final ClientRepo clients;
  private final MatterRepo matters;
  private final DocumentRepo docs;
  private final VersionRepo versions;
  private final TaskRepo tasks;
  private final DeadlineRepo deadlines;
  private final NoteRepo notes;
  private final PracticeRepo practices;
  private final SettingRepo settings;
  private final AuditRepo audits;
  private final PasswordEncoder encoder;
  private final Path base;

  SeedData(
      UserRepo u,
      ClientRepo c,
      MatterRepo m,
      DocumentRepo d,
      VersionRepo v,
      TaskRepo t,
      DeadlineRepo dl,
      NoteRepo n,
      PracticeRepo p,
      SettingRepo s,
      AuditRepo a,
      PasswordEncoder e,
      @Value("${app.upload-dir}") String b) {
    users = u;
    clients = c;
    matters = m;
    docs = d;
    versions = v;
    tasks = t;
    deadlines = dl;
    notes = n;
    practices = p;
    settings = s;
    audits = a;
    encoder = e;
    base = Paths.get(b).toAbsolutePath().normalize();
  }

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    if (users.findByEmailIgnoreCase("records.officer@lexbridge.test").isEmpty())
      users.save(
          new UserAccount(
              "records.officer@lexbridge.test",
              encoder.encode("Records!2026"),
              "Records Officer",
              Role.FIRM_ADMIN,
              null));
    if (users.count() > 0) return;
    Files.createDirectories(base);
    String hash = encoder.encode("Lexbridge!2026");
    List<Client> cs = new ArrayList<>();
    String[] names = {
      "Amelia Hart",
      "Northstar Renewables Ltd",
      "Daniel Okafor",
      "Juniper Health CIC",
      "Priya Shah",
      "Redwood Design Group",
      "Oliver Chen",
      "Maya Williams",
      "Civic Harbour Trust",
      "Theo Morgan",
      "Elena Petrov",
      "Kingsway Foods Ltd"
    };
    for (int i = 0; i < 12; i++)
      cs.add(clients.save(new Client(names[i], "client" + (i + 1) + "@example.test", i)));
    UserAccount admin =
        users.save(
            new UserAccount("admin@lexbridge.test", hash, "Morgan Ellis", Role.FIRM_ADMIN, null));
    UserAccount attorney =
        users.save(
            new UserAccount("attorney@lexbridge.test", hash, "Aisha Rahman", Role.ATTORNEY, null));
    UserAccount paralegal =
        users.save(
            new UserAccount(
                "paralegal@lexbridge.test", hash, "Jamie Brooks", Role.PARALEGAL, null));
    UserAccount client =
        users.save(
            new UserAccount(
                "client@lexbridge.test", hash, "Amelia Hart", Role.CLIENT, cs.getFirst().id));
    UserAccount attorney2 =
        users.save(new UserAccount("lee@lexbridge.test", hash, "Samuel Lee", Role.ATTORNEY, null));
    UserAccount para2 =
        users.save(
            new UserAccount("nina@lexbridge.test", hash, "Nina Foster", Role.PARALEGAL, null));
    users.save(
        new UserAccount(
            "archivist@lexbridge.test",
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("MD5")
                        .digest("Archive!2026".getBytes(StandardCharsets.UTF_8))),
            "Beatrice Lowe",
            Role.ATTORNEY,
            null));
    for (String p :
        List.of(
            "Commercial disputes",
            "Employment",
            "Regulatory",
            "Corporate advisory",
            "Data protection",
            "Property")) practices.save(new PracticeArea(p));
    settings.save(new FirmSetting("firm.name", "Lexbridge Legal"));
    settings.save(new FirmSetting("retention.years", "7"));
    settings.save(new FirmSetting("reminders.enabled", "true"));
    String[] titles = {
      "Supplier contract dispute",
      "Employment grievance response",
      "Regulatory licence review",
      "Series A governance",
      "Subject access response",
      "Lease renewal negotiation",
      "Trademark opposition",
      "Service agreement review",
      "Incident response counsel",
      "Board restructuring",
      "Planning appeal",
      "Commercial settlement",
      "Employment tribunal defence",
      "Privacy programme review",
      "Acquisition due diligence",
      "Partnership dissolution",
      "Historic records retention",
      "Distribution agreement"
    };
    List<Matter> ms = new ArrayList<>();
    for (int i = 0; i < 18; i++) {
      Matter m = new Matter();
      m.reference = "LB-2026-" + String.format("%04d", i + 1);
      m.title = titles[i];
      m.clientId = cs.get(i % 12).id;
      m.practiceArea =
          List.of(
                  "Commercial disputes",
                  "Employment",
                  "Regulatory",
                  "Corporate advisory",
                  "Data protection",
                  "Property")
              .get(i % 6);
      m.attorneyId = i % 4 == 0 ? attorney2.id : attorney.id;
      m.paralegalId = i % 5 == 0 ? para2.id : paralegal.id;
      m.status = MatterStatus.INTAKE;
      m.sensitivity = i % 5 == 0 ? "HIGHLY_CONFIDENTIAL" : "CONFIDENTIAL";
      m.description =
          "Advice and representation concerning "
              + titles[i].toLowerCase(Locale.ROOT)
              + ". Mock benchmark data only.";
      m.openedOn = LocalDate.now().minusDays(i * 7L + 3);
      m.targetCloseDate = LocalDate.now().plusDays(30L + i * 8);
      matters.save(m);
      MatterStatus target =
          switch (i % 6) {
            case 0 -> MatterStatus.CONFLICT_REVIEW;
            case 1 -> MatterStatus.OPEN;
            case 2 -> MatterStatus.ON_HOLD;
            case 3 -> MatterStatus.CLOSED;
            case 4 -> MatterStatus.ARCHIVED;
            default -> MatterStatus.OPEN;
          };
      advanceMatter(m, target, admin);
      ms.add(m);
    }
    for (int i = 0; i < 60; i++) {
      Matter m = ms.get(i % 18);
      DocumentRecord d = new DocumentRecord();
      d.matterId = m.id;
      d.name =
          List.of(
                      "Client instructions",
                      "Counsel memorandum",
                      "Evidence bundle",
                      "Filed correspondence",
                      "Engagement letter")
                  .get(i % 5)
              + " "
              + (i + 1);
      d.category =
          List.of("CLIENT_UPLOAD", "CORRESPONDENCE", "PLEADING", "EVIDENCE", "ADVICE").get(i % 5);
      d.visibility = Visibility.values()[i % 4];
      d.tags = i % 2 == 0 ? "reviewed, key" : "draft";
      d.currentVersion = i % 4 == 0 ? 2 : 1;
      d.uploadedBy = i % 3 == 0 ? attorney.id : paralegal.id;
      docs.save(d);
      for (int vn = 1; vn <= d.currentVersion; vn++) {
        String text =
            "Mock legal document for "
                + m.reference
                + ". Version "
                + vn
                + ". No real personal data.";
        String storage = UUID.randomUUID() + ".txt";
        Files.writeString(
            base.resolve(storage), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        DocumentVersion v = new DocumentVersion();
        v.documentId = d.id;
        v.version = vn;
        v.originalName = d.name.replace(' ', '-').toLowerCase() + "-v" + vn + ".txt";
        v.storageName = storage;
        v.contentType = "text/plain";
        v.sizeBytes = text.getBytes(StandardCharsets.UTF_8).length;
        v.checksum =
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(text.getBytes(StandardCharsets.UTF_8)));
        v.uploadedBy = d.uploadedBy;
        versions.save(v);
      }
      audit(admin, "SEED_DOCUMENT_CREATE", "MATTER", m.id, null, d.visibility.name());
    }
    for (int i = 0; i < 40; i++) {
      Matter m = ms.get(i % 18);
      TaskItem t = new TaskItem();
      t.matterId = m.id;
      t.title =
          List.of(
                  "Review disclosure",
                  "Prepare chronology",
                  "Confirm client instructions",
                  "Draft response",
                  "Check filing requirements")
              .get(i % 5);
      t.notes = "Complete against the matter plan and record the outcome.";
      t.assigneeId = i % 10 == 0 ? client.id : (i % 5 == 0 ? attorney.id : paralegal.id);
      t.priority = List.of("LOW", "NORMAL", "HIGH", "URGENT").get(i % 4);
      t.status = TaskStatus.OPEN;
      t.dueDate = LocalDate.now().plusDays(i - 8);
      tasks.save(t);
      TaskStatus target =
          List.of(
                  TaskStatus.OPEN,
                  TaskStatus.IN_PROGRESS,
                  TaskStatus.BLOCKED,
                  TaskStatus.COMPLETED,
                  TaskStatus.CANCELLED)
              .get(i % 5);
      advanceTask(t, target, admin);
    }
    for (int i = 0; i < 20; i++) {
      Matter m = ms.get(i % 18);
      Deadline d = new Deadline();
      d.matterId = m.id;
      d.title =
          List.of("Court filing", "Client response", "Disclosure date", "Renewal notice")
              .get(i % 4);
      d.dueAt = Instant.now().plus(Duration.ofDays(i * 3L - 6));
      d.responsibleId = i % 3 == 0 ? attorney.id : paralegal.id;
      d.reminderDays = 7;
      d.completed = i % 6 == 0;
      d.reminderSent = i < 3;
      deadlines.save(d);
      audit(admin, "SEED_DEADLINE_CREATE", "MATTER", m.id, null, d.title);
    }
    for (int i = 0; i < 36; i++) {
      Matter m = ms.get(i % 18);
      NoteMessage n = new NoteMessage();
      n.matterId = m.id;
      n.authorId = i % 3 == 0 ? client.id : (i % 2 == 0 ? attorney.id : paralegal.id);
      n.kind = i % 3 == 0 ? "CLIENT_MESSAGE" : i % 3 == 1 ? "PRIVILEGED_NOTE" : "CLIENT_MESSAGE";
      n.body =
          n.kind.equals("PRIVILEGED_NOTE")
              ? "Privileged strategy note: assess evidential gaps before the next conference."
              : "Please confirm the next step and whether any further documents are required.";
      n.status = n.kind.equals("PRIVILEGED_NOTE") || i % 4 != 0 ? "APPROVED" : "DRAFT";
      notes.save(n);
      audit(admin, "SEED_NOTE_CREATE", "MATTER", m.id, null, n.kind + ":" + n.status);
    }
  }

  private void advanceMatter(Matter m, MatterStatus target, UserAccount actor) {
    while (m.status != target) {
      MatterStatus next =
          switch (m.status) {
            case INTAKE -> MatterStatus.CONFLICT_REVIEW;
            case CONFLICT_REVIEW ->
                target == MatterStatus.CONFLICT_REVIEW ? target : MatterStatus.OPEN;
            case OPEN ->
                target == MatterStatus.ON_HOLD ? MatterStatus.ON_HOLD : MatterStatus.CLOSED;
            case ON_HOLD -> MatterStatus.CLOSED;
            case CLOSED -> MatterStatus.ARCHIVED;
            case ARCHIVED -> MatterStatus.ARCHIVED;
          };
      if (next == m.status) break;
      MatterStatus old = m.status;
      m.status = next;
      matters.save(m);
      audit(actor, "SEED_WORKFLOW_TRANSITION", "MATTER", m.id, old.name(), next.name());
    }
  }

  private void advanceTask(TaskItem t, TaskStatus target, UserAccount actor) {
    if (target == TaskStatus.OPEN) return;
    TaskStatus old = t.status;
    if (target == TaskStatus.COMPLETED) {
      t.status = TaskStatus.IN_PROGRESS;
      tasks.save(t);
      audit(actor, "SEED_TASK_TRANSITION", "TASK", t.id, old.name(), t.status.name());
      old = t.status;
    }
    t.status = target;
    tasks.save(t);
    audit(actor, "SEED_TASK_TRANSITION", "TASK", t.id, old.name(), target.name());
  }

  private void audit(
      UserAccount a, String action, String type, UUID id, String before, String after) {
    AuditEvent e = new AuditEvent();
    e.actorId = a.id;
    e.action = action;
    e.resourceType = type;
    e.resourceId = id.toString();
    e.beforeValue = before;
    e.afterValue = after;
    e.authorizationBasis = "seed workflow using firm administrator authority";
    audits.save(e);
  }
}
