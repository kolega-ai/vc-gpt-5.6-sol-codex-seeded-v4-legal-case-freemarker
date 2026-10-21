package com.lexbridge.decision;

import jakarta.persistence.*;
import jakarta.xml.bind.annotation.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

enum Role {
  FIRM_ADMIN,
  ATTORNEY,
  PARALEGAL,
  CLIENT
}

enum MatterStatus {
  INTAKE,
  CONFLICT_REVIEW,
  OPEN,
  ON_HOLD,
  CLOSED,
  ARCHIVED
}

enum Visibility {
  INTERNAL_ONLY,
  PRIVILEGED,
  CLIENT_SHARED,
  PUBLIC_FILING
}

enum TaskStatus {
  OPEN,
  IN_PROGRESS,
  BLOCKED,
  COMPLETED,
  CANCELLED
}

@MappedSuperclass
abstract class IdEntity {
  @Id UUID id = UUID.randomUUID();
}

@Entity
@Table(name = "user_accounts")
class UserAccount extends IdEntity {
  String email;

  @Column(name = "password_hash")
  String passwordHash;

  @Column(name = "display_name")
  String displayName;

  @Enumerated(EnumType.STRING)
  Role role;

  @Column(name = "client_id")
  UUID clientId;

  boolean enabled;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  protected UserAccount() {}

  UserAccount(String e, String p, String n, Role r, UUID c) {
    email = e;
    passwordHash = p;
    displayName = n;
    role = r;
    clientId = c;
    enabled = true;
  }
}

@Entity
@Table(name = "app_sessions")
class AppSession extends IdEntity {
  @Column(name = "user_id")
  UUID userId;

  @Column(name = "expires_at")
  Instant expiresAt;

  boolean revoked;

  protected AppSession() {}

  AppSession(UUID u) {
    this(u, Duration.ofHours(8));
  }

  AppSession(UUID u, Duration life) {
    userId = u;
    expiresAt = Instant.now().plus(life);
  }
}

@Entity
@Table(name = "reset_tokens")
class ResetToken extends IdEntity {
  @Column(name = "user_id")
  UUID userId;

  @Column(name = "token_hash")
  String tokenHash;

  @Column(name = "expires_at")
  Instant expiresAt;

  @Column(name = "used_at")
  Instant usedAt;

  protected ResetToken() {}
}

@Entity
@Table(name = "doc_access_codes")
class DocAccessCode extends IdEntity {
  @Column(name = "document_id")
  UUID documentId;

  String code;

  @Column(name = "expires_at")
  Instant expiresAt;

  @Column(name = "used_at")
  Instant usedAt;

  protected DocAccessCode() {}
}

@Entity
@Table(name = "clients")
class Client extends IdEntity {
  String name;
  String type;
  String email;
  String phone;
  String address;

  @Column(name = "conflict_metadata")
  String conflictMetadata;

  String status;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  protected Client() {}

  Client(String n, String e, int i) {
    name = n;
    email = e;
    type = i % 3 == 0 ? "ORGANIZATION" : "PERSON";
    phone = "+44 20 7946 " + String.format("%04d", 1000 + i);
    address = (10 + i) + " Chancery Lane, London";
    conflictMetadata = "Screened against active client list";
    status = "ACTIVE";
  }
}

@Entity
@Table(name = "practice_areas")
class PracticeArea extends IdEntity {
  String name;
  boolean active = true;

  protected PracticeArea() {}

  PracticeArea(String n) {
    name = n;
  }
}

@Entity
@Table(name = "matters")
class Matter extends IdEntity {
  String reference;
  String title;

  @Column(name = "client_id")
  UUID clientId;

  @Column(name = "practice_area")
  String practiceArea;

  @Column(name = "attorney_id")
  UUID attorneyId;

  @Column(name = "paralegal_id")
  UUID paralegalId;

  @Enumerated(EnumType.STRING)
  MatterStatus status;

  String sensitivity;

  @Column(length = 3000)
  String description;

  @Column(name = "opened_on")
  LocalDate openedOn;

  @Column(name = "target_close_date")
  LocalDate targetCloseDate;

  @Column(name = "updated_at")
  Instant updatedAt = Instant.now();

  protected Matter() {}
}

@Entity
@Table(name = "documents")
class DocumentRecord extends IdEntity {
  @Column(name = "matter_id")
  UUID matterId;

  String name;
  String category;

  @Enumerated(EnumType.STRING)
  Visibility visibility;

  String tags;

  @Column(name = "current_version")
  int currentVersion;

  @Column(name = "uploaded_by")
  UUID uploadedBy;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  protected DocumentRecord() {}
}

@Entity
@Table(name = "document_versions")
class DocumentVersion extends IdEntity {
  @Column(name = "document_id")
  UUID documentId;

  int version;

  @Column(name = "original_name")
  String originalName;

  @Column(name = "storage_name")
  String storageName;

  @Column(name = "content_type")
  String contentType;

  @Column(name = "size_bytes")
  long sizeBytes;

  String checksum;

  @Column(name = "uploaded_by")
  UUID uploadedBy;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  protected DocumentVersion() {}
}

@Entity
@Table(name = "task_items")
class TaskItem extends IdEntity {
  @Column(name = "matter_id")
  UUID matterId;

  String title;

  @Column(length = 2000)
  String notes;

  @Column(name = "assignee_id")
  UUID assigneeId;

  String priority;

  @Enumerated(EnumType.STRING)
  TaskStatus status;

  @Column(name = "due_date")
  LocalDate dueDate;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  Instant updatedAt = Instant.now();

  protected TaskItem() {}
}

@Entity
@Table(name = "deadlines")
class Deadline extends IdEntity {
  @Column(name = "matter_id")
  UUID matterId;

  String title;

  @Column(name = "due_at")
  Instant dueAt;

  @Column(name = "responsible_id")
  UUID responsibleId;

  @Column(name = "reminder_days")
  int reminderDays;

  boolean completed;

  @Column(name = "reminder_sent")
  boolean reminderSent;

  protected Deadline() {}
}

@Entity
@Table(name = "note_messages")
class NoteMessage extends IdEntity {
  @Column(name = "matter_id")
  UUID matterId;

  @Column(name = "author_id")
  UUID authorId;

  String kind;

  @Column(length = 5000)
  String body;

  String status;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  @Column(name = "approved_by")
  UUID approvedBy;

  protected NoteMessage() {}
}

@Entity
@Table(name = "notifications")
class Notification extends IdEntity {
  @Column(name = "user_id")
  UUID userId;

  String title;
  String body;
  String link;

  @Column(name = "read_at")
  Instant readAt;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();

  protected Notification() {}
}

@Entity
@Table(name = "audit_events")
class AuditEvent extends IdEntity {
  @Column(name = "actor_id")
  UUID actorId;

  String action;

  @Column(name = "resource_type")
  String resourceType;

  @Column(name = "resource_id")
  String resourceId;

  @Column(name = "before_value")
  String beforeValue;

  @Column(name = "after_value")
  String afterValue;

  @Column(name = "authorization_basis")
  String authorizationBasis;

  @Column(name = "occurred_at")
  Instant occurredAt = Instant.now();

  protected AuditEvent() {}
}

@Entity
@Table(name = "settings")
class FirmSetting {
  @Id
  @Column(name = "setting_key")
  String key;

  @Column(name = "setting_value")
  String value;

  @Column(name = "updated_at")
  Instant updatedAt = Instant.now();

  protected FirmSetting() {}

  FirmSetting(String k, String v) {
    key = k;
    value = v;
  }
}

@Entity
@Table(name = "api_keys")
class ApiKey extends IdEntity {
  String name;

  @Column(name = "key_hash")
  String keyHash;

  String prefix;
  boolean enabled = true;

  @Column(name = "created_at")
  Instant createdAt = Instant.now();
}

@Entity
@Table(name = "webhook_deliveries")
class WebhookDelivery extends IdEntity {
  @Column(name = "event_type")
  String eventType;

  @Column(name = "target_url")
  String targetUrl;

  String status;

  @Column(name = "response_code")
  Integer responseCode;

  @Column(name = "attempted_at")
  Instant attemptedAt = Instant.now();
}

@Entity
@Table(name = "outbox")
class OutboxMail extends IdEntity {
  String recipient;
  String subject;
  String body;
  String status = "QUEUED";

  @Column(name = "created_at")
  Instant createdAt = Instant.now();
}

@XmlRootElement(name = "filingManifest")
@XmlAccessorType(XmlAccessType.FIELD)
class FilingManifest {
  @XmlElement(name = "filing")
  List<FilingEntry> filings = new ArrayList<>();
}

@XmlAccessorType(XmlAccessType.FIELD)
class FilingEntry {
  @XmlElement String title;

  @XmlElement String dueAt;

  @XmlElement String court;
}
