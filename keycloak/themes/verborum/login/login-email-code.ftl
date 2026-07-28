<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("emailCodeTitle")}
    <#elseif section = "form">
        <form id="kc-email-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <p style="color:#666;margin:0 0 14px;">${msg("emailCodeInstruction")}</p>
                <label for="code" class="${properties.kcLabelClass!}">${msg("emailCodeLabel")}</label>
                <input id="code" name="code" type="text" inputmode="numeric" autocomplete="one-time-code"
                       pattern="[0-9]*" autofocus class="${properties.kcInputClass!}"
                       aria-invalid="<#if messagesPerField.existsError('code')>true</#if>" />
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                       type="submit" value="${msg("doLogIn")}" />
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <button class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!}"
                        name="resend" value="true" type="submit">${msg("emailCodeResend")}</button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
