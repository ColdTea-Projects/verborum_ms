<#import "template.ftl" as layout>
<@layout.emailLayout>
  <p style="margin:0 0 18px; font-size:18px; font-weight:600; color:#1A1A1A;">Confirm your email</p>
  <p style="margin:0 0 22px;">Welcome to Verborum! Tap the button below to verify this email address and finish setting up your account.</p>
  <p style="margin:0 0 26px; text-align:center;">
    <a href="${link}" style="display:inline-block; background:#C41E3A; color:#FFFFFF; text-decoration:none; font-weight:600; font-size:15px; padding:13px 30px; border-radius:10px; letter-spacing:.3px;">Verify my email</a>
  </p>
  <p style="margin:0 0 8px; color:#666666; font-size:13px;">This link expires in ${linkExpirationFormatter(linkExpiration)}.</p>
  <p style="margin:0; color:#999999; font-size:12px;">If the button doesn't work, paste this link into your browser:<br><span style="color:#C41E3A; word-break:break-all;">${link}</span></p>
</@layout.emailLayout>
