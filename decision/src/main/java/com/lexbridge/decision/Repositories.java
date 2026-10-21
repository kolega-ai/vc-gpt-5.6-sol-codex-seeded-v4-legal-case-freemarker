package com.lexbridge.decision;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface UserRepo extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmailIgnoreCase(String email);

  List<UserAccount> findAllByOrderByDisplayName();
}

interface SessionRepo extends JpaRepository<AppSession, UUID> {}

interface DocAccessRepo extends JpaRepository<DocAccessCode, UUID> {
  Optional<DocAccessCode> findFirstByDocumentIdAndUsedAtIsNullOrderByExpiresAtDesc(
      UUID documentId);
}

interface ResetRepo extends JpaRepository<ResetToken, UUID> {
  Optional<ResetToken> findByTokenHashAndUsedAtIsNull(String hash);
}

interface ClientRepo extends JpaRepository<Client, UUID> {
  List<Client> findAllByOrderByName();
}

interface PracticeRepo extends JpaRepository<PracticeArea, UUID> {}

interface MatterRepo extends JpaRepository<Matter, UUID> {
  List<Matter> findByAttorneyIdOrParalegalIdOrderByUpdatedAtDesc(UUID a, UUID p);

  List<Matter> findByClientIdOrderByUpdatedAtDesc(UUID c);

  List<Matter> findAllByOrderByUpdatedAtDesc();

  Optional<Matter> findByReference(String reference);
}

interface DocumentRepo extends JpaRepository<DocumentRecord, UUID> {
  List<DocumentRecord> findByMatterIdOrderByCreatedAtDesc(UUID m);
}

interface VersionRepo extends JpaRepository<DocumentVersion, UUID> {
  List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID d);

  Optional<DocumentVersion> findByDocumentIdAndVersion(UUID d, int v);
}

interface TaskRepo extends JpaRepository<TaskItem, UUID> {
  List<TaskItem> findByMatterIdOrderByDueDate(UUID m);

  List<TaskItem> findByAssigneeIdOrderByDueDate(UUID u);
}

interface DeadlineRepo extends JpaRepository<Deadline, UUID> {
  List<Deadline> findByMatterIdOrderByDueAt(UUID m);

  List<Deadline> findByDueAtBetweenOrderByDueAt(Instant a, Instant b);
}

interface NoteRepo extends JpaRepository<NoteMessage, UUID> {
  List<NoteMessage> findByMatterIdOrderByCreatedAtDesc(UUID m);
}

interface NotificationRepo extends JpaRepository<Notification, UUID> {
  List<Notification> findTop20ByUserIdOrderByCreatedAtDesc(UUID u);
}

interface AuditRepo extends JpaRepository<AuditEvent, UUID> {
  List<AuditEvent> findTop200ByOrderByOccurredAtDesc();

  List<AuditEvent> findByResourceTypeAndResourceIdOrderByOccurredAtDesc(String t, String id);
}

interface SettingRepo extends JpaRepository<FirmSetting, String> {}

interface ApiKeyRepo extends JpaRepository<ApiKey, UUID> {}

interface WebhookRepo extends JpaRepository<WebhookDelivery, UUID> {}

interface OutboxRepo extends JpaRepository<OutboxMail, UUID> {}
