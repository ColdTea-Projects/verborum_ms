<#--
  Branded HTML wrapper for every Verborum email. Email clients are unreliable with <style> blocks and
  have no dark-mode contract, so this is a table layout with inline styles on a light ground —
  crimson header + gold rule + "Verborum" wordmark, mirroring the app. Individual templates fill
  <#nested>.
-->
<#macro emailLayout>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
</head>
<body style="margin:0; padding:0; background:#ECECEC; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#ECECEC; padding:28px 12px;">
    <tr><td align="center">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px; background:#FFFFFF; border:1px solid #E0E0E0; border-radius:14px; overflow:hidden;">
        <tr>
          <td style="background:#C41E3A; padding:26px 0 22px; text-align:center;">
            <div style="color:#FFFFFF; font-size:26px; font-weight:700; letter-spacing:4px; text-transform:uppercase;">Verborum</div>
            <div style="width:44px; height:3px; background:#D4AF37; margin:12px auto 0; border-radius:2px; line-height:3px; font-size:0;">&nbsp;</div>
          </td>
        </tr>
        <tr>
          <td style="padding:32px 34px 34px; color:#1A1A1A; font-size:15px; line-height:1.6;">
            <#nested>
          </td>
        </tr>
        <tr>
          <td style="padding:18px 34px 26px; color:#999999; font-size:12px; line-height:1.5; border-top:1px solid #ECECEC;">
            You received this because this address was used with Verborum. If it wasn't you, you can safely ignore this email.
          </td>
        </tr>
      </table>
      <div style="color:#999999; font-size:11px; margin-top:16px;">Verborum &middot; Language, one word at a time</div>
    </td></tr>
  </table>
</body>
</html>
</#macro>
