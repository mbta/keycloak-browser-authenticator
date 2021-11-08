package cz.integsoft.keycloak.browser.authenticator;

import javax.ws.rs.core.Response;

import org.keycloak.authentication.AbstractFormAuthenticator;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.events.Errors;
import org.keycloak.models.UserModel;
import org.keycloak.services.messages.Messages;

/**
 * Abstract class for all authenticators containing utility methods for validation and error reporting.
 *
 * @author integsoft
 */
public abstract class AbstractLoginFormAuthenticator extends AbstractFormAuthenticator {

	public static final String FORM_FTL_EMAIL_CODE = "login-validate-email-code.ftl";

	private static final String BAD_EMAIL_CODE = "secondFactor.badEmailCode";

	/**
	 * Invalid code error response.
	 *
	 * @param context authentication flow context
	 * @return {@link Response}
	 */
	protected Response invalidEmailCode(final AuthenticationFlowContext context) {
		context.fork();
		return context.form().setError(BAD_EMAIL_CODE).createForm(FORM_FTL_EMAIL_CODE);
	}

	/**
	 * Invalid user error response.
	 *
	 * @param context authentication flow context
	 * @return {@link Response}
	 */
	protected Response invalidUser(final AuthenticationFlowContext context) {
		return context.form().setError(Messages.INVALID_USER).createLoginUsernamePassword();
	}

	/**
	 * User not found error response.
	 *
	 * @param context authentication flow context
	 * @param user user
	 * @return true - user does not exist / otherwise false
	 */
	public boolean invalidUser(final AuthenticationFlowContext context, final UserModel user) {
		if (user == null) {
			context.getEvent().error(Errors.USER_NOT_FOUND);
			final Response challengeResponse = invalidUser(context);
			context.failureChallenge(AuthenticationFlowError.INVALID_USER, challengeResponse);
			return true;
		}
		return false;
	}
}
