<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
    	<div class="container">
			<h1>${msg("loginAccountTitle")}</h1>
			<#if realm.password>
	        	<#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
					<#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span>
						<div class="form-group">
		                    <div class="form-message-container form-success" role="alert">
		                        <strong class="form-message-summary">${msg('message.success')}</strong>
		                        <ul>
		                            <li class="form-message-text">${kcSanitize(message.summary)?no_esc}</li>
		                        </ul>                    
		                    </div>
		                </div>
					</#if>
					<#if message.type = 'warning'>
						<div class="form-group">
		                    <div class="form-message-container form-warning" role="alert">
		                        <strong class="form-message-summary">${msg('message.warning')}</strong>
		                        <ul>
		                            <li class="form-message-text">${kcSanitize(message.summary)?no_esc}</li>
		                        </ul>
		                    </div>
		                </div>
					</#if>
					<#if message.type = 'error'>
						<div class="form-group">
							<div class="form-message-container form-error" role="alert">
								<strong class="form-message-summary">${msg('message.error')}</strong>
								<ul>
									<li class="form-message-text">${kcSanitize(message.summary)?no_esc}</li>
								</ul>
							</div>
						</div>
					</#if>
					<#if message.type = 'info'>
						<div class="form-group">
		                    <div class="form-message-container form-info" role="alert">
		                        <strong class="form-message-summary">${msg('message.info')}</strong>
		                        <ul>
		                            <li class="form-message-text">${kcSanitize(message.summary)?no_esc}</li>
		                        </ul>
		                    </div>
		                </div>
					</#if>	
				</#if>
				
	            <form onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
	            	<div class="form-group">
                        <label class="form-input-label<#if messagesPerField.existsError('username')> label-error</#if>" for="form-input-email"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                        <#if usernameEditDisabled??>
	                        <input id="form-input-email" class="form-input<#if messagesPerField.existsError('username')> input-error</#if>" name="username" value="${(login.username!'')}" type="email" disabled />
	                    <#else>
	                        <input id="form-input-email" class="form-input<#if messagesPerField.existsError('username')> input-error</#if>" name="username" value="${(login.username!'')}" type="email" autofocus autocomplete="off" />
	                    </#if>
                    </div>
                    <div class="form-group">
                        <label for="form-input-password" class="form-input-label<#if messagesPerField.existsError('password')> label-error</#if>">${msg("password")}</label>
                    	<input id="form-input-password" class="form-input<#if messagesPerField.existsError('password')> input-error</#if>" name="password" type="password" autocomplete="off" />
                    </div>
                    <div class="form-group submit-group">
                    	<#if realm.resetPasswordAllowed>
							<a href="${url.loginResetCredentialsUrl}" class="forgot-password">${msg("doForgotPassword")}</a>
						</#if>
						
						<input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
	                    <input name="login" id="sign-in" type="submit" value="${msg("doLogIn")}"/>
                    </div>
                    
                    <#--if realm.password && social.providers??>
			            <div id="kc-social-providers" class="${properties.kcFormSocialAccountSectionClass!}">
			                <hr/>
			                <h4>${msg("identity-provider-login-label")}</h4>
			
			                <ul class="${properties.kcFormSocialAccountListClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountListGridClass!}</#if>">
			                    <#list social.providers as p>
			                        <a id="social-${p.alias}" class="${properties.kcFormSocialAccountListButtonClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountGridItem!}</#if>"
			                                type="button" href="${p.loginUrl}">
			                            <#if p.iconClasses?has_content>
			                                <i class="${properties.kcCommonLogoIdP!} ${p.iconClasses!}" aria-hidden="true"></i>
			                                <span class="${properties.kcFormSocialAccountNameClass!} kc-social-icon-text">${p.displayName!}</span>
			                            <#else>
			                                <span class="${properties.kcFormSocialAccountNameClass!}">${p.displayName!}</span>
			                            </#if>
			                        </a>
			                    </#list>
			                </ul>
			            </div>
			        </#if-->
                    
                    <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
			            <div>
							<span class="body-text">${msg("noAccount")}</span> <a href="${url.registrationUrl}">${msg("doRegister")}</a>
			            </div>
			        </#if>
	            </form>
			</#if>
		</div>
    </#if>
</@layout.registrationLayout>
