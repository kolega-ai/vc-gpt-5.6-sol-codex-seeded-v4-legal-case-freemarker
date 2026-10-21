<#import "layout.ftlh" as ui>
<@ui.page title="Document versions" active="matters">
<div class="page-head">
<div>
<div class="eyebrow">
<a href="/matters/${matter.id}">${matter.reference}</a> · Document record</div>
<h1>${name}</h1>
<p class="lede">Every version is immutable; sharing applies to the document record and is audited.</p>
</div>
<a class="btn btn-primary" href="/documents/${id}/download">Download current version</a>
</div>
<div class="facts">
<div class="fact">
<label>Visibility</label>
<@ui.status visibility/>
</div>
<div class="fact">
<label>Category</label>
<strong>${category?replace('_',' ')?lower_case?cap_first}</strong>
</div>
<div class="fact">
<label>Current version</label>
<strong>v${currentVersion}</strong>
</div>
<div class="fact">
<label>Tags</label>
<strong>${tags!'None'}</strong>
</div>
</div>
<div class="grid grid-main">
<section class="card">
<div class="card-head">
<h2>Version history</h2>
</div>
<div class="table-wrap">
<table class="table">
<thead>
<tr>
<th>Version</th>
<th>Original file</th>
<th>Type</th>
<th>Size</th>
<th>Checksum</th>
<th>
</th>
</tr>
</thead>
<tbody>
<#list versions as v>
<tr>
<td>
<strong>v${v.version}</strong>
</td>
<td>${v.originalName}<div class="subtle">${v.createdAt?keep_before('T')}</div>
</td>
<td>${v.contentType}</td>
<td>${v.sizeBytes} bytes</td>
<td>
<span class="ref" title="${v.checksum}">${v.checksum?substring(0,12)}…</span>
</td>
<td>
<a class="btn btn-small btn-secondary" href="/documents/${id}/download?version=${v.version}">Download</a>
</td>
</tr>
</#list>
</tbody>
</table>
</div>
<#if currentUser.role!='CLIENT' && matter.status!='ARCHIVED'>
<div class="card-body">
<form method="post" enctype="multipart/form-data" action="/documents/${id}/versions">
<@ui.csrf/>
<input type="hidden" name="matterId" value="${matter.id}">
<div class="field">
<label for="file">Upload a new version</label>
<input class="input" id="file" name="file" type="file" required>
<span class="hint">The current version advances only after the new bytes and checksum are stored.</span>
</div>
<button class="btn btn-primary">Upload version</button>
</form>
</div>
</#if>
</section>
<aside class="section-stack">
<section class="card">
<div class="card-head">
<h2>Sharing controls</h2>
</div>
<div class="card-body">
<p class="subtle">Client access requires an owning client account and <strong>client shared</strong> visibility. Privileged and internal documents never appear in the client portal.</p>
<#if currentUser.role=='ATTORNEY'||currentUser.role=='FIRM_ADMIN'>
<form method="post" action="/documents/${id}/visibility">
<@ui.csrf/>
<div class="field">
<label for="visibility">Visibility</label>
<select class="select" id="visibility" name="visibility">
<#list ['INTERNAL_ONLY','PRIVILEGED','CLIENT_SHARED','PUBLIC_FILING'] as x>
<option value="${x}" ${(visibility==x)?then('selected','')}>${x?replace('_',' ')?lower_case?cap_first}</option>
</#list>
</select>
</div>
<button class="btn btn-primary">Save sharing controls</button>
</form>
</#if>
</div>
</section>
<section class="card">
<div class="card-head">
<h2>Integrity</h2>
</div>
<div class="card-body">
<p class="subtle">Downloads resolve through a canonical storage base, are scope-checked, and create one audit event per request.</p>
</div>
</section>
<#if currentUser.role!='CLIENT'>
<section class="card">
<div class="card-head">
<h2>Document tools</h2>
</div>
<div class="card-body">
<form method="post" action="/documents/${id}/access-code"><@ui.csrf/><button class="btn btn-small btn-secondary">Issue client access code</button></form>
<form method="post" action="/documents/${id}/open"><@ui.csrf/>
<div class="field"><label for="pass">Access code</label><input class="input" id="pass" name="pass" inputmode="numeric"></div>
<button class="btn btn-small btn-secondary">Open with code</button>
</form>
<form method="post" action="/documents/${id}/convert"><@ui.csrf/>
<div class="field"><label for="profile">Conversion profile</label><input class="input" id="profile" name="profile" value="pdf"></div>
<button class="btn btn-small btn-secondary">Convert document</button>
</form>
<form method="get" action="/documents/exhibit-text">
<div class="field"><label for="ref">Exhibit text reference</label><input class="input" id="ref" name="ref"></div>
<button class="btn btn-small btn-secondary">Extract exhibit text</button>
</form>
<form method="post" action="/firm/exhibits/sealed-note"><@ui.csrf/>
<div class="field"><label for="sealed-ref">Sealed courier reference</label><input class="input" id="sealed-ref" name="ref"></div>
<button class="btn btn-small btn-secondary">Open sealed note</button>
</form>
<form method="post" action="/matters/${matter.id}/case-file-info"><@ui.csrf/>
<div class="field"><label for="stored-name">Case file name</label><input class="input" id="stored-name" name="storedName"></div>
<button class="btn btn-small btn-secondary">Inspect case file</button>
</form>
</div>
</section>
</#if>
</aside>
</div>
</@ui.page>
