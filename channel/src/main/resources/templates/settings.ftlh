<#import "layout.ftlh" as ui>
<@ui.page title="Firm settings" active="settings">
<div class="page-head">
<div>
<div class="eyebrow">Firm administration</div>
<h1>Firm settings</h1>
<p class="lede">Retention and reminder policies are authoritative server-side reference settings.</p>
<p><a class="btn btn-small btn-secondary" href="/admin/settings?section=delivery">Show delivery configuration</a></p>
</div>
</div>
<div class="grid grid-3">
<#list settings as key,value>
<section class="card">
<div class="card-head">
<h2>${key?replace('.',' ')?cap_first}</h2>
</div>
<form class="card-body" method="post" action="/admin/settings/${key}">
<@ui.csrf/>
<div class="field">
<label for="setting-${key?replace('.','-')}">Value</label>
<input class="input" id="setting-${key?replace('.','-')}" name="value" value="${value}" required>
</div>
<button class="btn btn-primary">Save changes</button>
</form>
</section>
</#list>
</div>
<section class="card" style="margin-top:20px"><div class="card-head"><h2>Practice areas</h2></div><div class="table-wrap"><table class="table"><thead><tr><th>Practice area</th><th>Status</th><th>Action</th></tr></thead><tbody><#list practiceAreas as p><tr><td><strong>${p.name}</strong></td><td><@ui.status p.active?then('ACTIVE','DISABLED')/></td><td><form method="post" action="/admin/practice-areas/${p.id}/active"><@ui.csrf/><input type="hidden" name="active" value="${p.active?then('false','true')}"><button class="btn btn-small btn-secondary">${p.active?then('Disable','Enable')}</button></form></td></tr></#list></tbody></table></div><form class="card-body" method="post" enctype="multipart/form-data" action="/admin/settings/brand-asset"><@ui.csrf/><div class="field"><label for="assetName">Brand mark file name</label><input class="input" id="assetName" name="assetName" placeholder="lexbridge-mark.png"></div><div class="field"><label for="brand-file">Brand mark</label><input class="input" id="brand-file" name="file" type="file" required><span class="hint">Shown in the sidebar for everyone in the firm. <a href="/brand-asset">View current mark</a>.</span></div><button class="btn btn-primary">Store brand mark</button></form>
<form class="card-body inline-form" method="post" action="/admin/practice-areas"><@ui.csrf/><label for="practice-name"><strong>New practice area</strong></label><input class="input" id="practice-name" name="name" maxlength="100" required><button class="btn btn-primary">Add area</button></form></section>
<section class="card" style="margin-top:20px"><div class="card-head"><h2>Client notice template</h2></div><form class="card-body" method="post" action="/firm/notices/preview"><@ui.csrf/><div class="field"><label for="notice-body">Notice template</label><textarea class="textarea" id="notice-body" name="body" rows="4">Dear client, the matter is progressing and no further action is required this week.</textarea></div><button class="btn btn-primary">Render preview</button></form></section>
<section class="card" style="margin-top:20px"><div class="card-head"><h2>Restore a legacy bundle</h2></div><form class="card-body" method="post" action="/firm/bundles/restore"><@ui.csrf/><div class="field"><label for="bundle-matter">Matter ID</label><input class="input" id="bundle-matter" name="matterId" placeholder="UUID"></div><div class="field"><label for="bundle">Bundle payload</label><textarea class="textarea" id="bundle" name="bundle" rows="3"></textarea></div><button class="btn btn-primary">Restore bundle</button></form></section>
<section class="card" style="margin-top:20px"><div class="card-head"><h2>Firm profile bundle</h2></div><form class="card-body" method="post" enctype="multipart/form-data" action="/firm/settings/profile-bundle"><@ui.csrf/><p class="subtle">Upload a <code>firm.profile.yml</code> export from the previous workspace to reapply branding and policy keys.</p><div class="field"><label for="profile-bundle-file">Profile bundle</label><input class="input" id="profile-bundle-file" name="file" type="file" accept=".yml,.yaml" required></div><button class="btn btn-primary">Apply bundle</button></form><form class="card-body" method="post" action="/firm/settings/profile-apply"><@ui.csrf/><div class="field"><label for="profile-bundle-text">Paste bundle</label><textarea class="textarea" id="profile-bundle-text" name="bundle" rows="4">firm.name: Lexbridge Legal</textarea></div><button class="btn btn-primary">Apply pasted bundle</button></form></section>
<section class="card" style="margin-top:20px"><div class="card-head"><h2>Message template</h2></div><form class="card-body" method="post" action="/firm/messages/preview"><@ui.csrf/><div class="field"><label for="message-body">Notification wording</label><textarea class="textarea" id="message-body" name="body" rows="3">Hello {{firm}}, your {{year}} matter update is ready.</textarea></div><button class="btn btn-primary">Render message</button></form></section>
</@ui.page>
