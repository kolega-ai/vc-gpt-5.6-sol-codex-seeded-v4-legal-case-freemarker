<#import "layout.ftlh" as ui>
<@ui.page title="Audit ledger" active="audit">
<div class="page-head">
<div>
<div class="eyebrow">Append-only record</div>
<h1>Audit ledger</h1>
<p class="lede">Important reads, state changes, sharing controls, exports, and privileged actions. Audit events cannot be edited or deleted.</p>
</div>
</div>
<section class="card">
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Time</th>
<th>Action</th>
<th>Resource</th>
<th>Before → after</th>
<th>Authorization basis</th>
</tr>
</thead>
<tbody>
<#list events as e>
<tr>
<td>
<span class="ref">${e.occurredAt?replace('T',' ')?keep_before('.')}</span>
</td>
<td>
<strong>${e.action?replace('_',' ')?lower_case?cap_first}</strong>
</td>
<td>${e.resourceType}<div class="ref">${e.resourceId!}</div>
</td>
<td>
<span class="subtle">${e.before!''} → ${e.after!''}</span>
</td>
<td>${e.authorizationBasis}</td>
</tr>
</#list>
</tbody>
</table>
</div>
</section>
</@ui.page>

