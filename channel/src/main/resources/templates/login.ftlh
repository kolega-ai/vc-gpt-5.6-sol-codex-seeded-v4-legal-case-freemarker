<#ftl output_format="HTML">
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sign in · Lexbridge</title>
<link rel="stylesheet" href="/assets/app.css">
</head>
<body>
<main class="auth-page">
<section class="auth-story">
<div class="brand">
<span class="brand-mark">L</span>
<span>
<strong>Lexbridge</strong>
<small>Case ledger</small>
</span>
</div>
<div>
<div class="eyebrow">Clarity under pressure</div>
<h2>Every matter.<br>Every deadline.<br>In evidence.</h2>
<p>A secure working record for legal teams and the clients who rely on them.</p>
</div>
<small>Mock benchmark environment · No real client data</small>
</section>
<section class="auth-panel">
<div class="auth-box">
<div class="eyebrow">Protected workspace</div>
<h1>Welcome back</h1>
<p class="lede">Sign in to continue to your firm workspace.</p>
<#if error??>
<div class="alert alert-error" role="alert">${error}</div>
</#if>
<#if success??>
<div class="alert alert-success">${success}</div>
</#if>
<#if RequestParameters.loggedOut??>
<div class="alert alert-success">You have been signed out.</div>
</#if>
<form method="post" action="/login">
<input type="hidden" name="next" value="${next!}">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="field">
<label for="email">Work email</label>
<input class="input" id="email" name="email" type="email" autocomplete="username" required autofocus>
</div>
<div class="field">
<label for="password">Password</label>
<input class="input" id="password" name="password" type="password" autocomplete="current-password" required>
</div>
<div class="field">
<label><input type="checkbox" name="trustedDevice" value="true"> Keep me signed in on this device</label>
</div>
<button class="btn btn-primary" type="submit">Sign in securely</button>
</form>
<div class="auth-foot">
<a href="/forgot-password">Forgot your password?</a>
<br>
<br>Demo: attorney@lexbridge.test / Lexbridge!2026</div>
</div>
</section>
</main>
</body>
</html>

