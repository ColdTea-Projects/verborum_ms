<#import "template.ftl" as layout>
<@layout.emailLayout>
  <p style="margin:0 0 16px; font-size:18px; font-weight:600; color:#1A1A1A;">Your sign-in code</p>
  <p style="margin:0 0 20px;">Enter this code to finish signing in to Verborum:</p>
  <p style="margin:0 0 22px; text-align:center;">
    <span style="display:inline-block; font-size:30px; font-weight:700; letter-spacing:8px; color:#C41E3A; background:#F5F5F5; border:1px solid #E0E0E0; border-radius:10px; padding:14px 10px 14px 18px;">${code}</span>
  </p>
  <p style="margin:0; color:#666666; font-size:13px;">This code expires in ${ttlMinutes} minutes. If you didn't try to sign in, you can ignore this email.</p>
</@layout.emailLayout>
