<#ftl output_format="HTML">
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Reset password · Lexbridge</title>
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
<h2>Recover access without losing the record.</h2>
</div>
</section>
<section class="auth-panel">
<div class="auth-box">
<div class="eyebrow">Account recovery</div>
<h1>Reset your password</h1>
<p class="lede">We’ll write a time-limited reset message to the local development outbox.</p>
<#if success??>
<div class="alert alert-success">${success}</div>
</#if>
<#if error??>
<div class="alert alert-error">${error}</div>
</#if>
<form method="post">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="field">
<label for="email">Email</label>
<input class="input" id="email" name="email" type="email" required>
</div>
<button class="btn btn-primary">Request reset</button>
</form>
<hr>
<h2>Activate an invitation</h2>
<p class="lede">Check whether an invitation is waiting for your address.</p>
<form method="post" action="/invite-status">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="field">
<label for="invite-email">Email</label>
<input class="input" id="invite-email" name="email" type="email" required>
</div>
<button class="btn btn-secondary">Check invitation</button>
</form>
<p class="auth-foot">
<a href="/login">Return to sign in</a>
</p>
</div>
</section>
</main>
</body>
</html>

