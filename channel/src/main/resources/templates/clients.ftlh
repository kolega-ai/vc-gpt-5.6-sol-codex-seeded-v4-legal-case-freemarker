<#import "layout.ftlh" as ui>
<@ui.page title="Clients" active="clients">
<div class="page-head">
<div>
<div class="eyebrow">Client directory</div>
<h1>Clients</h1>
<p class="lede">Contact, organization type, conflict-screening context, and active standing.</p>
</div>
</div>
<div class="grid grid-main">
<section class="card">
<#if clients?size==0>
<div class="empty">
<strong>No clients recorded</strong>Create the first client using the intake panel.</div>
<#else>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Client</th>
<th>Type</th>
<th>Contact</th>
<th>Phone</th>
<th>Status</th>
</tr>
</thead>
<tbody>
<#list clients as client>
<tr>
<td>
<strong>${client.name}</strong>
<div class="subtle">${client.address!}</div>
</td>
<td>${client.type?lower_case?cap_first}</td>
<td>
<a href="mailto:${client.email}">${client.email}</a>
</td>
<td>${client.phone!}</td>
<td>
<@ui.status client.status/>
</td>
</tr>
</#list>
</tbody>
</table>
</div>
</#if>
</section>
<aside class="card">
<div class="card-head">
<h2>Add client</h2>
</div>
<form class="card-body" method="post" action="/clients">
<@ui.csrf/>
<div class="field">
<label for="name">Name</label>
<input class="input" id="name" name="name" maxlength="160" required>
</div>
<div class="field">
<label for="email">Email</label>
<input class="input" id="email" name="email" type="email" maxlength="180" required>
</div>
<div class="field">
<label for="type">Client type</label>
<select class="select" id="type" name="type">
<option>PERSON</option>
<option>ORGANIZATION</option>
</select>
</div>
<div class="field">
<label for="phone">Phone</label>
<input class="input" id="phone" name="phone" maxlength="40">
</div>
<div class="field">
<label for="address">Address</label>
<textarea class="textarea" id="address" name="address" maxlength="400">
</textarea>
</div>
<button class="btn btn-primary">Create client</button>
</form>
</aside>
</div>
</@ui.page>

