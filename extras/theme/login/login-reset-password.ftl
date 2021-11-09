<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
    <#if section = "header">
        ${msg("emailForgotTitle")}
    <#elseif section = "form">
    	<h1>${kcSanitize(msg("emailForgotTitle"))?no_esc}</h1>
        <form action="${url.loginAction}" method="post">
        	<div class="form-group">
				<label for="form-input-email" class="form-input-label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
				<input type="email" id="form-input-email" name="username" class="form-input" value="${(auth.attemptedUsername!'')}" />
			</div>
			<div class="form-group submit-group">
				<a href="${url.loginUrl}" class="back-link">${kcSanitize(msg("backToLogin"))?no_esc}</a>
				<input type="submit" value="${msg("doSubmit")}" id="submit"/>
			</div>
        </form>
    <#elseif section = "info" >
    	<div>
			<span class="body-text">${kcSanitize(msg("emailInstruction"))?no_esc}</span>
		</div> 
    </#if>
</@layout.registrationLayout>
