<#import "layout.ftlh" as ui>
<@ui.page title="Matters" active="matters">
<div class="page-head">
<div>
<div class="eyebrow">Casebook</div>
<h1>Matters</h1>
<p class="lede">Search the matters you are authorized to see. Assignment and client ownership are enforced on every result.</p>
</div>
<#if currentUser.role!='CLIENT'>
<a class="btn btn-primary" href="/matters/new">Open a matter</a>
</#if>
</div>
<form class="card filterbar" method="get">
<div class="field grow">
<label for="q">Search reference or title</label>
<input class="input" id="q" name="q" value="${q!}" placeholder="e.g. LB-2026 or disclosure">
</div>
<div class="field grow">
<label for="status">Status</label>
<select class="select" id="status" name="status">
<option value="">All statuses</option>
<#list ['INTAKE','CONFLICT_REVIEW','OPEN','ON_HOLD','CLOSED','ARCHIVED'] as x>
<option value="${x}" ${(status==x)?then('selected','')}>${x?replace('_',' ')?lower_case?cap_first}</option>
</#list>
</select>
</div>
<#if currentUser.role!='CLIENT'>
<div class="field grow"><label for="assignee">Assigned person</label><select class="select" id="assignee" name="assignee"><option value="">Any assignee</option><#list users![] as u><#if u.role!='CLIENT'><option value="${u.id}" ${(assignee==u.id?string)?then('selected','')}>${u.name}</option></#if></#list></select></div>
</#if>
<div class="field grow"><label for="scope">Result scope</label><select class="select" id="scope" name="scope"><option value="">My assigned matters</option><#list ['ATTORNEY','PARALEGAL','FIRM_ADMIN'] as x><option value="${x}" ${(scope!''==x)?then('selected','')}>${x?replace('_',' ')?lower_case?cap_first} view</option></#list></select></div>
<div class="field"><label for="from">Target close from</label><input class="input" id="from" name="from" type="date" value="${from!}"></div>
<div class="field"><label for="to">Target close to</label><input class="input" id="to" name="to" type="date" value="${to!}"></div>
<button class="btn btn-secondary">Apply filters</button>
</form>
<section class="card">
<#if matters?size==0>
<div class="empty">
<strong>No matters match</strong>Clear the filters or open a new matter.</div>
<#else>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Reference</th>
<th>Matter</th>
<th>Practice area</th>
<th>Sensitivity</th>
<th>Status</th>
<th>Target close</th>
</tr>
</thead>
<tbody>
<#list matters as matter>
<tr>
<td>
<span class="ref">${matter.reference}</span>
</td>
<td>
<a class="record-link" href="/matters/${matter.id}">${matter.title}</a>
<div class="subtle">Updated ${matter.updatedAt?keep_before('T')}</div>
</td>
<td>${matter.practiceArea}</td>
<td>${matter.sensitivity?replace('_',' ')?lower_case?cap_first}</td>
<td>
<@ui.status matter.status/>
</td>
<td>${matter.targetCloseDate!'Not set'}</td>
</tr>
</#list>
</tbody>
</table>
</div>
</#if>
</section>
</@ui.page>
