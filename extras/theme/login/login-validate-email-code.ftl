<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "title">
        ${msg("secondFactor.validateCode")}
    <#elseif section = "header">
        ${msg("secondFactor.validateCode")}
    <#elseif section = "form">
    	<h1>${kcSanitize(msg("secondFactor.validateCodeTitle"))?no_esc}</h1>
        <form action="${url.loginAction}" method="post">
            <div class="form-group">
               	<label for="form-input-code" class="form-input-label<#if message?has_content && (message.type == 'error' || !isAppInitiatedAction??)> label-error</#if>">${msg("secondFactor.code")}</label>
				<input type="text" id="form-input-code" name="email_code" class="form-input<#if message?has_content && (message.type == 'error' || !isAppInitiatedAction??)> input-error</#if>" autofocus/>
            </div>

            <div class="form-group submit-group">
                <input type="submit" value="${msg("doSubmit")}" id="submit"/>
            </div>
        </form>
    <#elseif section = "info" >
        ${msg("secondFactor.validateCode")}
    </#if>
</@layout.registrationLayout>
