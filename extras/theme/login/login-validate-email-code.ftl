<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
	<#if section = "title">
        ${msg("secondFactor.validateCode")}
    <#elseif section = "header">
        ${msg("secondFactor.validateCode")}
    <#elseif section = "form">
    	<div class="container">	
    		<h1>${kcSanitize(msg("secondFactor.validateCodeTitle"))?no_esc}</h1>
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
	        <form action="${url.loginAction}" method="post">
	            <div class="form-group">
	               	<label for="form-input-code" class="form-input-label<#if message?has_content && (message.type == 'error' || !isAppInitiatedAction??)> label-error</#if>">${msg("secondFactor.code")}</label>
					<input type="text" id="form-input-code" name="email_code" class="form-input<#if message?has_content && (message.type == 'error' || !isAppInitiatedAction??)> input-error</#if>" autofocus/>
	            </div>
	
	            <div class="form-group submit-group">
	                <input type="submit" value="${msg("doSubmit")}" id="submit"/>
	            </div>
	            <div>
	            	<span class="body-text">${msg("secondFactor.validateCode")}</span>
	            </div>
	        </form>
    	</div>    
    </#if>
</@layout.registrationLayout>
