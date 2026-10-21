<#import "layout.ftlh" as ui>
<@ui.page title="People & access" active="users">
<div class="page-head">
<div>
<div class="eyebrow">Firm administration</div>
<h1>People &amp; access</h1>
<p class="lede">Invite users, set their role, and disable access without impersonating them.</p>
</div>
</div>
<div class="grid grid-main">
<section class="card">
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Person</th>
<th>Role</th>
<th>State</th>
<th>Role change</th>
<th>Access</th>
</tr>
</thead>
<tbody>
<#list users as user>
<tr>
<td>
<strong>${user.name}</strong>
<div class="subtle">${user.email}</div>
</td>
<td>
<@ui.status user.role/>
</td>
<td>
<@ui.status user.enabled?then('ACTIVE','DISABLED')/>
</td>
<td>
<form class="inline-form" method="post" action="/admin/users/${user.id}/role">
<@ui.csrf/>
<select class="mini-select" name="role">
<#list ['FIRM_ADMIN','ATTORNEY','PARALEGAL','CLIENT'] as role>
<option ${(user.role==role)?then('selected','')}>${role}</option>
</#list>
</select>
<button class="btn btn-small btn-secondary">Change</button>
</form>
</td>
<td>
<form method="post" action="/admin/users/${user.id}/enabled">
<@ui.csrf/>
<input type="hidden" name="enabled" value="${user.enabled?then('false','true')}">
<button class="btn btn-small ${user.enabled?then('btn-danger','btn-secondary')}">${user.enabled?then('Disable','Enable')}</button>
</form>
</td>
</tr>
</#list>
</tbody>
</table>
</div>
</section>
<aside class="card">
<div class="card-head">
<h2>Invite a user</h2>
</div>
<form class="card-body" method="post" action="/admin/users">
<@ui.csrf/>
<div class="field">
<label for="name">Full name</label>
<input class="input" id="name" name="name" maxlength="120" required>
</div>
<div class="field">
<label for="email">Email</label>
<input class="input" id="email" name="email" type="email" required>
</div>
<div class="field">
<label for="role">Role</label>
<select class="select" id="role" name="role">
<option>ATTORNEY</option>
<option>PARALEGAL</option>
<option>CLIENT</option>
<option>FIRM_ADMIN</option>
</select>
</div>
<div class="field">
<label for="clientId">Client record ID (client role only)</label>
<input class="input" id="clientId" name="clientId" placeholder="UUID">
</div>
<button class="btn btn-primary">Create invitation</button>
<p class="hint">A temporary password is written to the local outbox. Support never impersonates the user.</p>
</form>
<form class="card-body" method="post" action="/firm/matters/reassign">
<@ui.csrf/>
<h2>Transfer a caseload</h2>
<div class="field"><label for="fromId">Reassign from (user ID)</label><input class="input" id="fromId" name="fromId" placeholder="UUID" required></div>
<div class="field"><label for="toId">Reassign to (user ID)</label><input class="input" id="toId" name="toId" placeholder="UUID" required></div>
<button class="btn btn-secondary">Transfer matters</button>
</form>
</aside>
</div>
</@ui.page>

