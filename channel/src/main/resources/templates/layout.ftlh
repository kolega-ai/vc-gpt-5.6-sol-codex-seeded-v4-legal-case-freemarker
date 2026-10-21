<#ftl output_format="HTML">
<#macro csrf>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
</#macro>
<#macro status value>
<#assign cls=value?string?lower_case?replace('_','-')>
<span class="badge status-${cls}">${value?string?replace('_',' ')?lower_case?cap_first}</span>
</#macro>
<#macro page title active="">
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="Secure legal matter management">
<title>${title} · Lexbridge</title>
<link rel="stylesheet" href="/assets/app.css?v=${assetVersion}">
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<div class="shell">
<aside class="sidebar">
<a class="brand" href="/dashboard">
<img class="brand-mark" src="/brand-asset" alt="Firm brand mark">
<span>
<strong>Lexbridge</strong>
<small>Case ledger</small>
</span>
</a>
<nav class="nav" aria-label="Primary">
<a class="${(active=='dashboard')?then('active','')}" href="/dashboard">
<span class="nav-icon">⌂</span>Overview</a>
<a class="${(active=='matters')?then('active','')}" href="/matters">
<span class="nav-icon">§</span>Matters</a>
<a class="${(active=='search')?then('active','')}" href="/search">
<span class="nav-icon">⌕</span>Search</a>
<#if currentUser.role != 'CLIENT'>
<a class="${(active=='clients')?then('active','')}" href="/clients">
<span class="nav-icon">◇</span>Clients</a>
</#if>
<a class="${(active=='calendar')?then('active','')}" href="/calendar">
<span class="nav-icon">□</span>Work &amp; deadlines</a>
<#if currentUser.role != 'CLIENT'>
<a class="${(active=='reports')?then('active','')}" href="/reports">
<span class="nav-icon">↧</span>Reports</a>
</#if>
<#if currentUser.role == 'FIRM_ADMIN'>
<span class="nav-label">Firm administration</span>
<a class="${(active=='users')?then('active','')}" href="/admin/users">
<span class="nav-icon">◎</span>People &amp; access</a>
<a class="${(active=='imports')?then('active','')}" href="/admin/imports">
<span class="nav-icon">⇧</span>Imports</a>
<a class="${(active=='audit')?then('active','')}" href="/admin/audit">
<span class="nav-icon">≡</span>Audit ledger</a>
<a class="${(active=='settings')?then('active','')}" href="/admin/settings">
<span class="nav-icon">⚙</span>Firm settings</a>
<a class="${(active=='integrations')?then('active','')}" href="/admin/integrations">
<span class="nav-icon">↔</span>Integrations</a>
</#if>
</nav>
<div class="account">
<div class="account-name">${currentUser.name}</div>
<div class="account-role">${currentUser.role?replace('_',' ')?lower_case}</div>
<div class="split-action">
<a href="/profile" class="link-button">Profile</a>
<form method="post" action="/logout">
<@csrf/>
<button class="link-button" type="submit">Sign out</button>
</form>
</div>
</div>
</aside>
<div class="main">
<header class="topbar">
<div class="crumb">Lexbridge Legal / ${title}</div>
<div class="top-actions">
<span class="badge">Secure session</span>
</div>
</header>
<main id="main" class="content">
<#if success??>
<div class="alert alert-success" role="status">${success}</div>
</#if>
<#if error??>
<div class="alert alert-error" role="alert">${error}</div>
</#if>
<#nested>
</main>
</div>
</div>
</body>
</html>
</#macro>
