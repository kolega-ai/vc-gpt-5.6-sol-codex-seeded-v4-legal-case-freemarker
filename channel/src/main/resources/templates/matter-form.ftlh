<#import "layout.ftlh" as ui>
<@ui.page title="Open a matter" active="matters">
<div class="page-head">
<div>
<div class="eyebrow">Matter intake</div>
<h1>Open a new matter</h1>
<p class="lede">Create the intake record first. Conflict review is the next controlled step.</p>
</div>
</div>
<div class="grid grid-main">
<form class="card" method="post" action="/matters">
<div class="card-head">
<h2>Intake details</h2>
</div>
<div class="card-body form-grid">
<@ui.csrf/>
<div class="field span-2">
<label for="title">Matter title</label>
<input class="input" id="title" name="title" maxlength="200" required>
</div>
<div class="field">
<label for="clientId">Client</label>
<select class="select" id="clientId" name="clientId" required>
<option value="">Choose client</option>
<#list clients as c>
<option value="${c.id}">${c.name}</option>
</#list>
</select>
</div>
<div class="field">
<label for="practiceArea">Practice area</label>
<select class="select" id="practiceArea" name="practiceArea" required>
<option value="">Choose practice area</option>
<#list practiceAreas as p>
<option>${p.name}</option>
</#list>
</select>
</div>
<div class="field">
<label for="attorneyId">Responsible attorney</label>
<select class="select" id="attorneyId" name="attorneyId">
<option value="">Assign later</option>
<#list users as u>
<#if u.role=='ATTORNEY'>
<option value="${u.id}">${u.name}</option>
</#if>
</#list>
</select>
</div>
<div class="field">
<label for="paralegalId">Paralegal</label>
<select class="select" id="paralegalId" name="paralegalId">
<option value="">Assign later</option>
<#list users as u>
<#if u.role=='PARALEGAL'>
<option value="${u.id}">${u.name}</option>
</#if>
</#list>
</select>
</div>
<div class="field">
<label for="sensitivity">Sensitivity</label>
<select class="select" id="sensitivity" name="sensitivity">
<option value="CONFIDENTIAL">Confidential</option>
<option value="HIGHLY_CONFIDENTIAL">Highly confidential</option>
<option value="STANDARD">Standard</option>
</select>
</div>
<div class="field">
<label for="targetCloseDate">Target close date</label>
<input class="input" id="targetCloseDate" name="targetCloseDate" type="date">
</div>
<div class="field span-2">
<label for="description">Scope and initial instructions</label>
<textarea class="textarea" id="description" name="description" maxlength="3000">
</textarea>
</div>
<div class="form-actions span-2">
<button class="btn btn-primary">Create intake</button>
<a class="btn btn-secondary" href="/matters">Cancel</a>
</div>
</div>
</form>
<aside class="card">
<div class="card-head">
<h2>What happens next</h2>
</div>
<div class="card-body timeline">
<div class="timeline-item">
<div class="timeline-meta">Intake</div>
<div class="timeline-body">Capture the client, scope, and responsible team.</div>
</div>
<div class="timeline-item">
<div class="timeline-meta">Conflict review</div>
<div class="timeline-body">An attorney or administrator confirms the matter can proceed.</div>
</div>
<div class="timeline-item">
<div class="timeline-meta">Open</div>
<div class="timeline-body">Tasks, deadlines, documents, and communications become the working record.</div>
</div>
</div>
</aside>
</div>
</@ui.page>
