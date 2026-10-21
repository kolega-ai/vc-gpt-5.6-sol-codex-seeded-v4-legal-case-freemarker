<#import "layout.ftlh" as ui>
<@ui.page title="Reports & exports" active="reports">
<div class="page-head">
<div>
<div class="eyebrow">Controlled extraction</div>
<h1>Reports &amp; exports</h1>
<p class="lede">Exports are generated server-side from your current role and assignment scope. Each request creates one audit event with the returned count.</p>
</div>
</div>
<div class="grid grid-3">
<section class="card">
<div class="card-head">
<h2>Deadline report</h2>
</div>
<div class="card-body">
<p>Upcoming and historic matter deadlines, responsible staff, and completion state.</p>
<a class="btn btn-primary" href="/reports/deadlines.csv">Download CSV</a>
<form class="inline-form" method="get" action="/reports/deadlines.csv">
<div class="field"><label for="saveAs">Archive copy name</label><input class="input" id="saveAs" name="saveAs" placeholder="deadlines-2026.csv"></div>
<button class="btn btn-small btn-secondary">Save copy</button>
</form>
</div>
</section>
<#if currentUser.role=='FIRM_ADMIN'>
<section class="card">
<div class="card-head">
<h2>Matter inventory</h2>
</div>
<div class="card-body">
<p>Firm-wide matter reference, practice area, status, and sensitivity.</p>
<a class="btn btn-primary" href="/reports/matters.csv">Download CSV</a>
</div>
</section>
<section class="card">
<div class="card-head">
<h2>Document inventory</h2>
</div>
<div class="card-body">
<p>Document categories, sharing levels, and current version numbers. Privileged note bodies are never included.</p>
<a class="btn btn-primary" href="/reports/documents.csv">Download CSV</a>
</div>
</section>
</#if>
</div>
</@ui.page>

