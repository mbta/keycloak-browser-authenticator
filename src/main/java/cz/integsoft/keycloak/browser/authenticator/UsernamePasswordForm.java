package cz.integsoft.keycloak.browser.authenticator;

import java.util.Locale;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.validation.Validation;

/**
 * Username password browser authenticator.
 *
 * @author integsoft
 */
public class UsernamePasswordForm extends AbstractUsernameFormAuthenticator implements Authenticator {

	private static Logger logger = Logger.getLogger(UsernamePasswordForm.class);

	private static final String EMAIL_MBTA_DOMAIN = "@mbta.com";

	private static final String MBTA_LOGIN_FORBIDDEN = "login.forbidden";

	@Override
	public void action(final AuthenticationFlowContext context) {
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		if (formData.containsKey("cancel")) {
			context.cancelLogin();
			return;
		}

		final String username = formData.getFirst(AuthenticationManager.FORM_USERNAME);

		if (username != null && username.trim().toLowerCase(Locale.US).contains(EMAIL_MBTA_DOMAIN)) {
			final Response challenge = context.form().addError(new FormMessage(Validation.FIELD_USERNAME, MBTA_LOGIN_FORBIDDEN)).createLoginUsernamePassword();
			context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
			return;
		}
		if (!validateForm(context, formData)) {
			return;
		}
		// check existence of mbta_uuid
		if (context.getUser() != null && context.getUser().getFirstAttribute("mbta_uuid") == null) {
			final String uuid = UUID.randomUUID().toString();
			context.getUser().setSingleAttribute("mbta_uuid", uuid);
			logger.infof("Added uuid %s to user %s", uuid, context.getUser().getUsername());
		}
		context.success();
	}

	protected boolean validateForm(final AuthenticationFlowContext context, final MultivaluedMap<String, String> formData) {
		return validateUserAndPassword(context, formData);
	}

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
		final MultivaluedMap<String, String> formData = new MultivaluedMapImpl<>();
		final String loginHint = context.getAuthenticationSession().getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);

		final String rememberMeUsername = AuthenticationManager.getRememberMeUsername(context.getRealm(), context.getHttpRequest().getHttpHeaders());

		if (loginHint != null || rememberMeUsername != null) {
			if (loginHint != null) {
				formData.add(AuthenticationManager.FORM_USERNAME, loginHint);
			} else {
				formData.add(AuthenticationManager.FORM_USERNAME, rememberMeUsername);
				formData.add("rememberMe", "on");
			}
		}
		final Response challengeResponse = challenge(context, formData);
		context.challenge(challengeResponse);
	}

	@Override
	public boolean requiresUser() {
		return false;
	}

	protected Response challenge(final AuthenticationFlowContext context, final MultivaluedMap<String, String> formData) {
		final LoginFormsProvider forms = context.form();

		if (formData.size() > 0) {
			forms.setFormData(formData);
		}

		return forms.createLoginUsernamePassword();
	}

	@Override
	public boolean configuredFor(final KeycloakSession session, final RealmModel realm, final UserModel user) {
		// never called
		return true;
	}

	@Override
	public void setRequiredActions(final KeycloakSession session, final RealmModel realm, final UserModel user) {
		// never called
	}

	@Override
	public void close() {

	}

}
