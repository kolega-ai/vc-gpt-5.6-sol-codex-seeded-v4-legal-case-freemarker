<#import "layout.ftlh" as ui>
<@ui.page title=(currentUser.role=='CLIENT')?then('My legal matters','Today’s case ledger') active="dashboard">
<div class="page-head">
<div>
<div class="eyebrow">${currentUser.role?replace('_',' ')?lower_case} workspace</div>
<h1>
<#if currentUser.role=='CLIENT'>Your matters, clearly explained.<#else>Good morning, ${currentUser.name?keep_before(' ')}.</#if>
</h1>
<p class="lede">
<#if currentUser.role=='CLIENT'>Documents, updates, and actions your legal team has shared with you.<#else>The decisions, deadlines, and client work that need attention now.</#if>
</p>
</div>
<#if currentUser.role!='CLIENT'>
<a class="btn btn-primary" href="/matters/new">Open a matter</a>
</#if>
</div>
<div class="metrics">
<div class="metric">
<div class="label">Matters in scope</div>
<div class="value">${matterCount}</div>
<div class="subtle">Based on your assignments</div>
</div>
<div class="metric green">
<div class="label">Open matters</div>
<div class="value">${openCount}</div>
<div class="subtle">Currently active</div>
</div>
<div class="metric amber">
<div class="label">Upcoming deadlines</div>
<div class="value">${deadlines?size}</div>
<div class="subtle">Next 30 days</div>
</div>
<div class="metric">
<div class="label">Assigned work</div>
<div class="value">${tasks?size}</div>
<div class="subtle">Your immediate queue</div>
</div>
</div>
<div class="grid grid-main">
<div class="section-stack">
<section class="card">
<div class="card-head">
<h2>
<#if currentUser.role=='CLIENT'>Your matters<#else>Active casebook</#if>
</h2>
<a href="/matters">View all</a>
</div>
<#if matters?size==0>
<div class="empty">
<strong>No matters in scope</strong>Your assigned matters will appear here.</div>
<#else>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Reference</th>
<th>Matter</th>
<th>Practice</th>
<th>Status</th>
</tr>
</thead>
<tbody>
<#list matters as item>
<tr>
<td>
<span class="ref">${item.reference}</span>
</td>
<td>
<a class="record-link" href="/matters/${item.id}">${item.title}</a>
</td>
<td>${item.practiceArea}</td>
<td>
<@ui.status item.status/>
</td>
</tr>
</#list>
</tbody>
</table>
</div>
</#if>
</section>
<#if currentUser.role!='CLIENT'>
<section class="card">
<div class="card-head">
<h2>Your work queue</h2>
<a href="/calendar">Open queue</a>
</div>
<#if tasks?size==0>
<div class="empty">
<strong>Your queue is clear</strong>New assignments will appear here.</div>
<#else>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Task</th>
<th>Due</th>
<th>Priority</th>
<th>Status</th>
</tr>
</thead>
<tbody>
<#list tasks as task>
<tr>
<td>
<a class="record-link" href="/matters/${task.matterId}">${task.title}</a>
</td>
<td>${task.dueDate!'Not set'}</td>
<td>
<@ui.status task.priority/>
</td>
<td>
<@ui.status task.status/>
</td>
</tr>
</#list>
</tbody>
</table>
</div>
</#if>
</section>
</#if>
</div>
<aside class="section-stack">
<section class="card">
<div class="card-head">
<h2>Deadline watch</h2>
</div>
<div class="card-body">
<#if deadlines?size==0>
<div class="empty">
<strong>No near deadlines</strong>Nothing falls due in the next 30 days.</div>
<#else>
<div class="evidence-strip">
<#list deadlines as due>
<div class="evidence-date">${due.dueAt?keep_before('T')}</div>
<div class="evidence-event">
<strong>
<a href="/matters/${due.matterId}">${due.title}</a>
</strong>
<p>${due.completed?then('Completed','Action pending')}</p>
</div>
</#list>
</div>
</#if>
</div>
</section>
<section class="card">
<div class="card-head">
<h2>Notifications</h2>
</div>
<div class="card-body timeline">
<#if notifications?size==0>
<div class="empty">
<strong>Nothing new</strong>Workflow updates will appear here.</div>
<#else>
<#list notifications as note>
<div class="timeline-item">
<div class="timeline-meta">${note.createdAt?keep_before('T')}</div>
<div class="timeline-body">
<strong>${note.title}</strong>
<br>${note.body}</div>
</div>
</#list>
</#if>
</div>
</section>
</aside>
</div>
</@ui.page>

