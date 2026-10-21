<#import "layout.ftlh" as ui>
<@ui.page title="Profile">
<div class="page-head">
<div>
<div class="eyebrow">Account</div>
<h1>Your profile</h1>
<p class="lede">Update how your name appears in matter activity and communications.</p>
</div>
</div>
<section class="card" style="max-width:620px">
<form class="card-body" method="post">
<@ui.csrf/>
<div class="field">
<label for="name">Display name</label>
<input class="input" id="name" name="name" value="${currentUser.name}" maxlength="120" required>
</div>
<#if currentUser.role!='CLIENT'>
<div class="field">
<label for="designation">Practice designation</label>
<select class="select" id="designation" name="designation">
<option value="">Leave unchanged</option>
<#list ['ATTORNEY','PARALEGAL'] as d>
<option value="${d}" ${(currentUser.role==d)?then('selected','')}>${d?lower_case?cap_first}</option>
</#list>
</select>
<span class="hint">Shown beside your name in matter activity.</span>
</div>
</#if>
<div class="field">
<label>Email</label>
<input class="input" value="${currentUser.email}" disabled>
<span class="hint">Contact a firm administrator to change sign-in email.</span>
</div>
<button class="btn btn-primary">Save changes</button>
</form>
</section>
</@ui.page>
