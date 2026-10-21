<#ftl output_format="HTML">
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Registry sign-in · Lexbridge</title>
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
<div class="eyebrow">Records office</div>
<h2>Legacy registry accounts.<br>One familiar step.</h2>
<p>Sign in with the credentials issued by the previous case system.</p>
</div>
<small>Mock benchmark environment · No real client data</small>
</section>
<section class="auth-panel">
<div class="auth-box">
<div class="eyebrow">Legacy registry</div>
<h1>Registry sign-in</h1>
<p class="lede">Accounts migrated from the prior registry still use their original sign-in phrase.</p>
<#if error??>
<div class="alert alert-error" role="alert">${error}</div>
</#if>
<form method="post" action="/registry-signin">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="field">
<label for="account">Registry account</label>
<input class="input" id="account" name="account" type="email" autocomplete="username" required autofocus>
</div>
<div class="field">
<label for="registryKey">Sign-in phrase</label>
<input class="input" id="registryKey" name="registryKey" type="password" autocomplete="current-password" required>
</div>
<button class="btn btn-primary" type="submit">Continue</button>
</form>
<div class="auth-foot"><a href="/login">Return to firm sign-in</a></div>
</div>
</section>
</main>
</body>
</html>
