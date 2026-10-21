package com.lexbridge.decision;

import java.time.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ReminderJob {
  private final DeadlineRepo deadlines;
  private final NotificationRepo notifications;
  private final AuditRepo audits;

  ReminderJob(DeadlineRepo d, NotificationRepo n, AuditRepo a) {
    deadlines = d;
    notifications = n;
    audits = a;
  }

  @Scheduled(cron = "0 15 * * * *")
  @Transactional
  void send() {
    Instant now = Instant.now(), limit = now.plus(Duration.ofDays(7));
    for (Deadline d : deadlines.findByDueAtBetweenOrderByDueAt(now, limit)) {
      if (d.completed || d.reminderSent || d.responsibleId == null) continue;
      Notification n = new Notification();
      n.userId = d.responsibleId;
      n.title = "Deadline approaching";
      n.body = d.title + " is due " + d.dueAt;
      n.link = "/matters/" + d.matterId;
      notifications.save(n);
      d.reminderSent = true;
      AuditEvent e = new AuditEvent();
      e.action = "DEADLINE_REMINDER";
      e.resourceType = "MATTER";
      e.resourceId = d.matterId.toString();
      e.beforeValue = "reminder_sent=false";
      e.afterValue = "reminder_sent=true";
      e.authorizationBasis = "scheduled reminder policy";
      audits.save(e);
    }
  }
}
