package com.lexbridge.decision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class CaseServiceTest {
  @TempDir Path temp;
  MatterRepo matters = mock(MatterRepo.class);
  ClientRepo clients = mock(ClientRepo.class);
  DocumentRepo docs = mock(DocumentRepo.class);
  VersionRepo versions = mock(VersionRepo.class);
  TaskRepo tasks = mock(TaskRepo.class);
  DeadlineRepo deadlines = mock(DeadlineRepo.class);
  NoteRepo notes = mock(NoteRepo.class);
  AuditRepo audits = mock(AuditRepo.class);
  NotificationRepo notifications = mock(NotificationRepo.class);
  UserRepo users = mock(UserRepo.class);
  JdbcTemplate jdbc = mock(JdbcTemplate.class);
  CoreGateway core = mock(CoreGateway.class);
  CaseService service;

  @BeforeEach
  void setup() throws Exception {
    service =
        new CaseService(
            matters,
            clients,
            docs,
            versions,
            tasks,
            deadlines,
            notes,
            audits,
            notifications,
            users,
            jdbc,
            core,
            temp.toString());
  }

  @Test
  void clientCanOnlyReadOwnMatter() {
    UUID owner = UUID.randomUUID();
    Matter m = new Matter();
    m.id = UUID.randomUUID();
    m.clientId = owner;
    m.status = MatterStatus.OPEN;
    when(matters.findById(m.id)).thenReturn(Optional.of(m));
    assertSame(
        m,
        service.requireMatter(
            new Actor(UUID.randomUUID(), "c@test", "Client", Role.CLIENT, owner), m.id, false));
    assertThrows(
        SecurityException.class,
        () ->
            service.requireMatter(
                new Actor(UUID.randomUUID(), "x@test", "Other", Role.CLIENT, UUID.randomUUID()),
                m.id,
                false));
  }

  @Test
  void assignedStaffScopeIsEnforced() {
    UUID assigned = UUID.randomUUID();
    Matter m = new Matter();
    m.id = UUID.randomUUID();
    m.clientId = UUID.randomUUID();
    m.attorneyId = assigned;
    m.status = MatterStatus.OPEN;
    when(matters.findById(m.id)).thenReturn(Optional.of(m));
    assertSame(
        m,
        service.requireMatter(
            new Actor(assigned, "a@test", "Attorney", Role.ATTORNEY, null), m.id, true));
    assertThrows(
        SecurityException.class,
        () ->
            service.requireMatter(
                new Actor(UUID.randomUUID(), "p@test", "Paralegal", Role.PARALEGAL, null),
                m.id,
                false));
  }

  @Test
  void archivedMatterIsReadOnlyForStaffButNotAdmin() {
    Matter m = new Matter();
    m.id = UUID.randomUUID();
    m.clientId = UUID.randomUUID();
    m.attorneyId = UUID.randomUUID();
    m.status = MatterStatus.ARCHIVED;
    when(matters.findById(m.id)).thenReturn(Optional.of(m));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.requireMatter(
                new Actor(m.attorneyId, "a@test", "Attorney", Role.ATTORNEY, null), m.id, true));
    assertSame(
        m,
        service.requireMatter(
            new Actor(UUID.randomUUID(), "admin@test", "Admin", Role.FIRM_ADMIN, null),
            m.id,
            true));
  }

  @Test
  void transitionListsAreExplicitAndTerminalStatesHaveNoEdges() {
    assertEquals(
        Set.of(MatterStatus.CONFLICT_REVIEW), CaseService.MATTER_EDGES.get(MatterStatus.INTAKE));
    assertTrue(CaseService.MATTER_EDGES.get(MatterStatus.ARCHIVED).isEmpty());
    assertTrue(CaseService.TASK_EDGES.get(TaskStatus.COMPLETED).isEmpty());
    assertFalse(CaseService.TASK_EDGES.get(TaskStatus.OPEN).contains(TaskStatus.COMPLETED));
  }

  @Test
  void clientCannotReadPrivilegedDocument() {
    UUID owner = UUID.randomUUID();
    Matter m = new Matter();
    m.id = UUID.randomUUID();
    m.clientId = owner;
    m.status = MatterStatus.OPEN;
    DocumentRecord d = new DocumentRecord();
    d.id = UUID.randomUUID();
    d.matterId = m.id;
    d.visibility = Visibility.PRIVILEGED;
    when(docs.findById(d.id)).thenReturn(Optional.of(d));
    when(matters.findById(m.id)).thenReturn(Optional.of(m));
    assertThrows(
        SecurityException.class,
        () ->
            service.requireDocument(
                new Actor(UUID.randomUUID(), "c@test", "Client", Role.CLIENT, owner), d.id));
  }
}
