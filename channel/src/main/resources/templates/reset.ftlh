<#ftl output_format="HTML">
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Choose password · Lexbridge</title>
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
<h2>A new key for your workspace.</h2>
</div>
</section>
<section class="auth-panel">
<div class="auth-box">
<div class="eyebrow">Account recovery</div>
<h1>Choose a new password</h1>
<#if error??>
<div class="alert alert-error">${error}</div>
</#if>
<form method="post">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="field">
<label for="token">Reset token</label>
<input class="input" id="token" name="token" value="${token!}" required>
</div>
<div class="field">
<label for="password">New password</label>
<input class="input" id="password" name="password" type="password" minlength="12" autocomplete="new-password" required>
<span class="hint">At least 12 characters.</span>
</div>
<button class="btn btn-primary">Change password</button>
</form>
<p class="auth-foot">
<a href="/login">Return to sign in</a>
</p>
</div>
</section>
</main>
</body>
</html>
