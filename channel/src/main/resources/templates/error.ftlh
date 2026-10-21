<#ftl output_format="HTML">
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Request not completed · Lexbridge</title>
<link rel="stylesheet" href="/assets/app.css">
</head>
<body>
<main class="auth-panel" style="min-height:100vh">
<div class="auth-box">
<div class="eyebrow">${status!'Error'}</div>
<h1>That request could not be completed.</h1>
<p class="lede">
<#if status?? && status==403>Your account does not have access to this action.<#elseif status?? && status==404>The requested record was not found or is outside your scope.<#else>The service could not process the request. Return to your workspace and try again.</#if>
</p>
<#if trace??>
<pre class="ref" style="overflow:auto;max-height:340px;text-align:left">${trace}</pre>
</#if>
<p>
<a class="btn btn-primary" href="/dashboard">Return to overview</a>
</p>
</div>
</main>
</body>
</html>
