<#import "layout.ftlh" as ui>
<@ui.page title="Edit matter" active="matters">
<div class="page-head">
<div>
<div class="eyebrow">${reference} · Matter details</div>
<h1>Edit ${title}</h1>
<p class="lede">Update scope and target date. Status and assignments use their separate controlled workflows.</p>
</div>
</div>
<section class="card" style="max-width:850px">
<form class="card-body" method="post" action="/matters/${id}">
<@ui.csrf/>
<div class="field">
<label for="title">Matter title</label>
<input class="input" id="title" name="title" value="${title}" maxlength="200" required>
</div>
<div class="field">
<label for="targetCloseDate">Target close date</label>
<input class="input" id="targetCloseDate" name="targetCloseDate" type="date" value="${targetCloseDate!}">
</div>
<div class="field">
<label for="description">Scope and instructions</label>
<textarea class="textarea" id="description" name="description" maxlength="3000">${description!}</textarea>
</div>
<div class="form-actions">
<button class="btn btn-primary">Save changes</button>
<a class="btn btn-secondary" href="/matters/${id}">Cancel</a>
</div>
</form>
</section>
</@ui.page>
