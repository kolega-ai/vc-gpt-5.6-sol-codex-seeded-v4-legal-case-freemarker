<#import "layout.ftlh" as ui>
<@ui.page title="Imports" active="imports">
<div class="page-head">
<div>
<div class="eyebrow">Controlled intake</div>
<h1>CSV imports</h1>
<p class="lede">Import mock client or matter records. Every row is validated; matter imports always enter the intake state.</p>
</div>
</div>
<div class="grid grid-2">
<section class="card">
<div class="card-head">
<h2>Import clients</h2>
</div>
<form class="card-body" method="post" enctype="multipart/form-data" action="/admin/imports/clients">
<@ui.csrf/>
<p class="subtle">Headers: <code>name,email,type</code>. Type is PERSON or ORGANIZATION.</p>
<div class="field">
<label for="clients-file">Client CSV</label>
<input class="input" id="clients-file" name="file" type="file" accept=".csv,text/csv" required>
</div>
<button class="btn btn-primary">Import clients</button>
</form>
</section>
<section class="card">
<div class="card-head">
<h2>Import matters</h2>
</div>
<form class="card-body" method="post" enctype="multipart/form-data" action="/admin/imports/matters">
<@ui.csrf/>
<p class="subtle">Headers: <code>title,client_email,practice_area</code>. Client email must already exist.</p>
<div class="field">
<label for="matters-file">Matter CSV</label>
<input class="input" id="matters-file" name="file" type="file" accept=".csv,text/csv" required>
</div>
<button class="btn btn-primary">Import matters</button>
</form>
</section>
<section class="card">
<div class="card-head">
<h2>Import a court filing manifest</h2>
</div>
<form class="card-body" method="post" action="/admin/imports/filings">
<@ui.csrf/>
<p class="subtle">Paste the manifest issued by the court portal. Each <code>&lt;filing&gt;</code> becomes a deadline on the matter.</p>
<div class="field">
<label for="matterId">Matter ID</label>
<input class="input" id="matterId" name="matterId" placeholder="UUID" required>
</div>
<div class="field">
<label for="manifestXml">Manifest XML</label>
<textarea class="input" id="manifestXml" name="manifestXml" rows="8" required></textarea>
</div>
<button class="btn btn-primary">Import manifest</button>
</form>
</section>
<section class="card">
<div class="card-head">
<h2>Import court filing receipts</h2>
</div>
<form class="card-body" method="post" enctype="multipart/form-data" action="/firm/imports/receipts">
<@ui.csrf/>
<p class="subtle">Upload the signed receipt bundle issued by the court portal. Each receipt is matched to its matter and recorded as a filing acknowledgement.</p>
<div class="field">
<label for="receipts-file">Receipt bundle</label>
<input class="input" id="receipts-file" name="file" type="file" required>
</div>
<button class="btn btn-primary">Import receipts</button>
</form>
</section>
</div>
</@ui.page>

