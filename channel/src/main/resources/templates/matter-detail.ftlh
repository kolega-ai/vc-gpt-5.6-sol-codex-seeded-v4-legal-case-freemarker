<#import "layout.ftlh" as ui>
<@ui.page title=reference active="matters">
<div class="detail-banner">
<div class="eyebrow">${reference} · ${practiceArea}</div>
<h1>${title}</h1>
<p>${client.name} · ${sensitivity?replace('_',' ')?lower_case?cap_first}</p>
<#if currentUser.role!='CLIENT' && status!='ARCHIVED'>
<p><a class="btn btn-secondary" href="/matters/${id}/edit">Edit matter details</a></p>
</#if>
<#if currentUser.role=='FIRM_ADMIN' && status=='INTAKE'>
<form method="post" action="/matters/${id}/delete"><@ui.csrf/><button class="btn btn-danger" type="submit">Delete empty intake</button></form>
</#if>
</div>
<div class="facts">
<div class="fact">
<label>Status</label>
<@ui.status status/>
</div>
<div class="fact">
<label>Opened</label>
<strong>${openedOn!'Not recorded'}</strong>
</div>
<div class="fact">
<label>Target close</label>
<strong>${targetCloseDate!'Not set'}</strong>
</div>
<div class="fact">
<label>Client contact</label>
<strong>${client.email}</strong>
</div>
</div>
<#if currentUser.role!='CLIENT'>
<div class="card filterbar">
<div class="field grow">
<label>Workflow action</label>
<span class="hint">Only explicit transitions allowed by the matter state machine are offered.</span>
</div>
<form class="inline-form" method="post" action="/matters/${id}/transition">
<@ui.csrf/>
<select class="select" name="status" aria-label="New matter status" required>
<option value="">Move matter…</option>
<#if status=='INTAKE'>
<option value="CONFLICT_REVIEW">Begin conflict review</option>
</#if>
<#if status=='CONFLICT_REVIEW'>
<option value="OPEN">Clear and open</option>
<option value="CLOSED">Close after conflict</option>
</#if>
<#if status=='OPEN'>
<option value="ON_HOLD">Place on hold</option>
<option value="CLOSED">Close matter</option>
</#if>
<#if status=='ON_HOLD'>
<option value="OPEN">Resume matter</option>
<option value="CLOSED">Close matter</option>
</#if>
<#if status=='CLOSED'>
<option value="OPEN">Reopen matter</option>
<option value="ARCHIVED">Archive matter</option>
</#if>
</select>
<button class="btn btn-primary" <#if currentUser.role=='PARALEGAL'>disabled title="Attorney approval required"</#if>>Update status</button>
</form>
</div>
</#if>
<nav class="tabs" aria-label="Matter sections">
<a href="#overview">Overview</a>
<a href="#documents">Documents (${documents?size})</a>
<a href="#tasks">Tasks (${tasks?size})</a>
<a href="#deadlines">Deadlines (${deadlines?size})</a>
<a href="#communications">Notes &amp; messages</a>
<#if currentUser.role!='CLIENT'>
<a href="#audit">Audit history</a>
</#if>
</nav>
<div class="section-stack">
<section id="overview" class="card">
<div class="card-head">
<h2>Matter overview</h2>
</div>
<div class="card-body">
<p>${description!'No scope description has been recorded.'}</p>
<div class="grid grid-3">
<div>
<div class="eyebrow">Client</div>
<strong>${client.name}</strong>
<div class="subtle">${client.type?lower_case?cap_first}<br>${client.phone!}<br>${client.address!}</div>
</div>
<div>
<div class="eyebrow">Practice</div>
<strong>${practiceArea}</strong>
<div class="subtle">Reference ${reference}</div>
</div>
<div>
<div class="eyebrow">Handling team</div>
<div class="subtle">Assignments are enforced server-side for every record and file.</div>
</div>
</div>
</div>
</section>
<section id="documents" class="card">
<div class="card-head">
<h2>Documents</h2>
<span class="subtle">Versions and sharing controls</span>
</div>
<#if documents?size==0>
<div class="empty">
<strong>No documents yet</strong>Upload instructions, evidence, correspondence, or filings.</div>
<#else>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Document</th>
<th>Category</th>
<th>Visibility</th>
<th>Version</th>
<th>Added</th>
</tr>
</thead>
<tbody>
<#list documents as doc>
<tr>
<td>
<a class="record-link" href="/documents/${doc.id}">${doc.name}</a>
<div class="subtle">${doc.tags!}</div>
</td>
<td>${doc.category?replace('_',' ')?lower_case?cap_first}</td>
<td>
<@ui.status doc.visibility/>
</td>
<td>v${doc.currentVersion}</td>
<td>${doc.createdAt?keep_before('T')}</td>
</tr>
</#list>
</tbody>
</table>
</div>
</#if>
<div class="card-body">
<form method="post" enctype="multipart/form-data" action="/matters/${id}/documents">
<@ui.csrf/>
<div class="form-grid">
<div class="field">
<label for="file">Upload document</label>
<input class="input" id="file" name="file" type="file" required>
<span class="hint">Maximum 20 MB. Client uploads are always internal until staff review.</span>
</div>
<div class="field">
<label for="docname">Display name</label>
<input class="input" id="docname" name="name" maxlength="220">
</div>
<#if currentUser.role!='CLIENT'>
<div class="field">
<label for="category">Category</label>
<select class="select" id="category" name="category">
<option>CLIENT_UPLOAD</option>
<option>CORRESPONDENCE</option>
<option>PLEADING</option>
<option>EVIDENCE</option>
<option>ADVICE</option>
</select>
</div>
<div class="field">
<label for="visibility">Initial visibility</label>
<select class="select" id="visibility" name="visibility">
<option>INTERNAL_ONLY</option>
<option>PRIVILEGED</option>
<option>CLIENT_SHARED</option>
<option>PUBLIC_FILING</option>
</select>
</div>
<div class="field span-2">
<label for="tags">Tags</label>
<input class="input" id="tags" name="tags" maxlength="500" placeholder="disclosure, reviewed">
</div>
</#if>
<div class="form-actions span-2">
<button class="btn btn-primary">Upload document</button>
</div>
</div>
</form>
</div>
</section>
<section id="tasks" class="card">
<div class="card-head">
<h2>Tasks</h2>
</div>
<#if tasks?size==0>
<div class="empty">
<strong>No tasks</strong>Assigned work appears here.</div>
<#else>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Task</th>
<th>Due</th>
<th>Priority</th>
<th>Status</th>
<#if currentUser.role!='CLIENT'>
<th>Update</th>
</#if>
</tr>
</thead>
<tbody>
<#list tasks as task>
<tr>
<td>
<strong>${task.title}</strong>
<div class="subtle">${task.notes!}</div>
</td>
<td>${task.dueDate!'Not set'}</td>
<td>
<@ui.status task.priority/>
</td>
<td>
<@ui.status task.status/>
</td>
<#if currentUser.role!='CLIENT'>
<td>
<form class="inline-form" method="post" action="/tasks/${task.id}/transition">
<@ui.csrf/>
<input type="hidden" name="matterId" value="${id}">
<select class="mini-select" name="status" aria-label="Update ${task.title}">
<#if task.status=='OPEN'>
<option>IN_PROGRESS</option>
<option>BLOCKED</option>
<option>CANCELLED</option>
</#if>
<#if task.status=='IN_PROGRESS'>
<option>COMPLETED</option>
<option>BLOCKED</option>
<option>CANCELLED</option>
</#if>
<#if task.status=='BLOCKED'>
<option>IN_PROGRESS</option>
<option>CANCELLED</option>
</#if>
</select>
<button class="btn btn-small btn-secondary" <#if task.status=='COMPLETED'||task.status=='CANCELLED'>disabled</#if>>Save</button>
</form>
</td>
</#if>
</tr>
</#list>
</tbody>
</table>
</div>
</#if>
<#if currentUser.role!='CLIENT' && status!='ARCHIVED'>
<div class="card-body inline-form">
<form class="inline-form" method="post" action="/matters/${id}/tasks/cancel">
<@ui.csrf/>
<label for="cancel-reason"><strong>Cancel the task list</strong></label>
<input class="input" id="cancel-reason" name="reason" maxlength="200" placeholder="Reason, e.g. matter withdrawn">
<button class="btn btn-small btn-danger">Cancel all tasks</button>
</form>
</div>
<div class="card-body">
<form method="post" action="/matters/${id}/tasks">
<@ui.csrf/>
<div class="form-grid">
<div class="field span-2">
<label for="tasktitle">Assign a task</label>
<input class="input" id="tasktitle" name="title" required maxlength="220">
</div>
<div class="field">
<label for="assigneeId">Assignee</label>
<select class="select" id="assigneeId" name="assigneeId">
<option value="">Unassigned</option>
<#list users![] as u>
<#if u.role!='CLIENT'>
<option value="${u.id}">${u.name}</option>
</#if>
</#list>
</select>
</div>
<div class="field">
<label for="priority">Priority</label>
<select class="select" id="priority" name="priority">
<option>NORMAL</option>
<option>HIGH</option>
<option>URGENT</option>
<option>LOW</option>
</select>
</div>
<div class="field">
<label for="dueDate">Due date</label>
<input class="input" id="dueDate" name="dueDate" type="date">
</div>
<div class="field span-2">
<label for="tasknotes">Instructions</label>
<textarea class="textarea" id="tasknotes" name="notes" maxlength="2000">
</textarea>
</div>
<div class="form-actions span-2">
<button class="btn btn-primary">Assign task</button>
</div>
</div>
</form>
</div>
</#if>
</section>
<section id="deadlines" class="card">
<div class="card-head">
<h2>Deadlines &amp; reminders</h2>
<#if currentUser.role!='CLIENT'>
<a href="/reports/deadlines.csv">Export deadline report</a>
</#if>
</div>
<div class="card-body">
<#if deadlines?size==0>
<div class="empty">
<strong>No deadlines recorded</strong>Add the next filing or response date.</div>
<#else>
<div class="evidence-strip">
<#list deadlines as due>
<div class="evidence-date">${due.dueAt?keep_before('T')}</div>
<div class="evidence-event">
<strong>${due.title}</strong>
<p>${due.completed?then('Completed','Reminder '+due.reminderDays+' days before')}</p>
</div>
</#list>
</div>
</#if>
<#if currentUser.role!='CLIENT' && status!='ARCHIVED'>
<form method="post" action="/matters/${id}/deadlines">
<@ui.csrf/>
<div class="form-grid">
<div class="field span-2">
<label for="deadlinetitle">Add deadline</label>
<input class="input" id="deadlinetitle" name="title" required>
</div>
<div class="field">
<label for="dueAt">Due date and time (UTC)</label>
<input class="input" id="dueAt" name="dueAt" type="datetime-local" required>
</div>
<div class="field">
<label for="responsibleId">Responsible person</label>
<select class="select" id="responsibleId" name="responsibleId">
<option value="">Unassigned</option>
<#list users![] as u>
<#if u.role!='CLIENT'>
<option value="${u.id}">${u.name}</option>
</#if>
</#list>
</select>
</div>
<div class="field">
<label for="reminderDays">Reminder days</label>
<input class="input" id="reminderDays" name="reminderDays" type="number" min="1" max="30" value="7">
</div>
<div class="form-actions span-2">
<button class="btn btn-primary">Add deadline</button>
</div>
</div>
</form>
</#if>
</div>
</section>
<section id="communications" class="card">
<div class="card-head">
<h2>
<#if currentUser.role=='CLIENT'>Messages<#else>Privileged notes &amp; client messages</#if>
</h2>
</div>
<div class="card-body">
<div class="timeline">
<#if communications?size==0>
<div class="empty">
<strong>No communication yet</strong>Start the matter record below.</div>
<#else>
<#list communications as item>
<div class="timeline-item ${(item.kind=='PRIVILEGED_NOTE')?then('internal','')}">
<div class="timeline-meta">${item.kind?replace('_',' ')?lower_case} · ${item.createdAt?keep_before('T')} · ${item.status?lower_case}</div>
<div class="timeline-body">${item.body}</div>
<#if item.status=='DRAFT' && (currentUser.role=='ATTORNEY'||currentUser.role=='FIRM_ADMIN')>
<form method="post" action="/messages/${item.id}/approve">
<@ui.csrf/>
<input type="hidden" name="matterId" value="${id}">
<button class="btn btn-small btn-secondary">Approve and share</button>
</form>
</#if>
</div>
</#list>
</#if>
</div>
<#if status!='ARCHIVED'>
<form method="post" action="/matters/${id}/notes">
<@ui.csrf/>
<div class="field">
<label for="kind">Entry type</label>
<select class="select" id="kind" name="kind">
<#if currentUser.role=='ATTORNEY'||currentUser.role=='FIRM_ADMIN'>
<option value="PRIVILEGED_NOTE">Privileged internal note</option>
</#if>
<option value="CLIENT_MESSAGE">Client message<#if currentUser.role=='PARALEGAL'> draft</#if>
</option>
</select>
</div>
<div class="field">
<label for="body">Message or note</label>
<textarea class="textarea" id="body" name="body" required maxlength="5000">
</textarea>
</div>
<button class="btn btn-primary">Save entry</button>
</form>
</#if>
</div>
</section>
<#if currentUser.role!='CLIENT'>
<section id="audit" class="card">
<div class="card-head">
<h2>Matter audit history</h2>
<span class="subtle">Append-only evidence strip</span>
</div>
<div class="card-body evidence-strip">
<#list audit![] as event>
<div class="evidence-date">${event.occurredAt?keep_before('T')}</div>
<div class="evidence-event">
<strong>${event.action?replace('_',' ')?lower_case?cap_first}</strong>
<p>${event.before!''} → ${event.after!''}<br>${event.authorizationBasis}</p>
</div>
</#list>
</div>
</section>
</#if>
</div>
</@ui.page>
