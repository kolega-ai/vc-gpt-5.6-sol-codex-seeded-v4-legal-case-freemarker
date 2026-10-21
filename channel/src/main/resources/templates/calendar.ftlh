<#import "layout.ftlh" as ui>
<@ui.page title="Work & deadlines" active="calendar">
<div class="page-head">
<div>
<div class="eyebrow">Forward view</div>
<h1>Work &amp; deadlines</h1>
<p class="lede">A chronological view of obligations and assigned work within your authorized matters.</p>
</div>
<#if currentUser.role!='CLIENT'>
<a class="btn btn-secondary" href="/reports/deadlines.csv">Export deadline report</a>
</#if>
</div>
<#if currentUser.role!='CLIENT'>
<form class="card filterbar" method="post" action="/deadlines/preview"><@ui.csrf/><div class="field grow"><label for="rule">Deadline rule formula</label><input class="input" id="rule" name="rule" value="filingDays + courtDays" required><span class="hint">Preview a court-rule formula against the firm's day counts.</span></div><button class="btn btn-secondary">Evaluate</button></form>
</#if>
<div class="grid grid-2">
<section class="card">
<div class="card-head">
<h2>Deadline calendar</h2>
</div>
<div class="card-body evidence-strip">
<#if deadlines?size==0>
<div>
</div>
<div class="empty">
<strong>No deadlines</strong>New dates will appear here.</div>
<#else>
<#list deadlines as due>
<div class="evidence-date">${due.dueAt?keep_before('T')}</div>
<div class="evidence-event">
<strong>
<a href="/matters/${due.matterId}">${due.title}</a>
</strong>
<p>${due.completed?then('Completed','Pending')} · reminder ${due.reminderDays} days before</p>
</div>
</#list>
</#if>
</div>
</section>
<section class="card">
<div class="card-head">
<h2>Assigned tasks</h2>
</div>
<#if tasks?size==0>
<div class="empty">
<strong>No assigned tasks</strong>Your queue is clear.</div>
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
</div>
</@ui.page>

