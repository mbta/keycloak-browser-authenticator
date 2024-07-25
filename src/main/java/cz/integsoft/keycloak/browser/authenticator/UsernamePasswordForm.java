package cz.integsoft.keycloak.browser.authenticator;

import static org.keycloak.services.validation.Validation.FIELD_PASSWORD;
import static org.keycloak.services.validation.Validation.FIELD_USERNAME;

import java.util.Locale;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.springframework.security.crypto.bcrypt.BCrypt;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

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
		if (!validatePassword(context, formData)) {
			return;
		}
		context.success();
	}

	private boolean validatePassword(final AuthenticationFlowContext context, final MultivaluedMap<String, String> inputData) {
		context.clearUser();
		final UserModel user = getUser(context, inputData);
		// if the user has a "password" attribute, validate it. Remove "password" from attributes if valid.
		if (user != null) {
			logger.debugf("Has user %s password configured: %s", user.getUsername(), user.credentialManager().isConfiguredFor(CredentialRepresentation.PASSWORD));
			if (user.credentialManager().isConfiguredFor(CredentialRepresentation.PASSWORD)) {
				if (user.getFirstAttribute(CredentialRepresentation.PASSWORD) != null) {
					user.removeAttribute(CredentialRepresentation.PASSWORD);
				}
				return validateUserAndPassword(context, inputData) && validateUser(context, user, inputData);
			}
			// if user has old hash type, we have to change it
			String passwordHashAttribute = user.getFirstAttribute(CredentialRepresentation.PASSWORD);
			if (passwordHashAttribute == null) {
				return badPasswordHandler(context, user, true, false);
			}
			if (passwordHashAttribute.startsWith("$2y$")) {
				passwordHashAttribute = passwordHashAttribute.replace("$2y$", "$2a$");
			}
			final String password = inputData.getFirst(CredentialRepresentation.PASSWORD);
			final boolean valid = BCrypt.checkpw(password, passwordHashAttribute);
			logger.debugf("User %s attribut password valid: %s", user.getUsername(), valid);
			if (!valid) {
				return badPasswordHandler(context, user, true, false);
			}
			context.setUser(user);
			// store the password in Keycloak
			// we catch an exception if the password does not meet the password policy
			try {
				user.credentialManager().updateCredential(UserCredentialModel.password(password));
				user.removeAttribute(CredentialRepresentation.PASSWORD);
			} catch (final ModelException e) {
				logger.infof("Password policy - unable to set password as Keycloak password of user %s.", user.getUsername());
			}
			return valid;
		}
		return false;
	}

	// Set up AuthenticationFlowContext error.
	private boolean badPasswordHandler(final AuthenticationFlowContext context, final UserModel user, final boolean clearUser, final boolean isEmptyPassword) {
		context.getEvent().user(user);
		context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
		final Response challengeResponse = challenge(context, getDefaultChallengeMessage(context), FIELD_PASSWORD);
		if (isEmptyPassword) {
			context.forceChallenge(challengeResponse);
		} else {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challengeResponse);
		}

		if (clearUser) {
			context.clearUser();
		}
		return false;
	}

	private UserModel getUser(final AuthenticationFlowContext context, final MultivaluedMap<String, String> inputData) {
		String username = inputData.getFirst(AuthenticationManager.FORM_USERNAME);
		if (username == null) {
			context.getEvent().error(Errors.USER_NOT_FOUND);
			final Response challengeResponse = challenge(context, getDefaultChallengeMessage(context), FIELD_USERNAME);
			context.failureChallenge(AuthenticationFlowError.INVALID_USER, challengeResponse);
			return null;
		}

		// remove leading and trailing whitespace
		username = username.trim();

		context.getEvent().detail(Details.USERNAME, username);
		context.getAuthenticationSession().setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, username);

		UserModel user = null;
		try {
			user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), username);
		} catch (final ModelDuplicateException mde) {
			ServicesLogger.LOGGER.modelDuplicateException(mde);

			// Could happen during federation import
			if (mde.getDuplicateFieldName() != null && mde.getDuplicateFieldName().equals(UserModel.EMAIL)) {
				setDuplicateUserChallenge(context, Errors.EMAIL_IN_USE, Messages.EMAIL_EXISTS, AuthenticationFlowError.INVALID_USER);
			} else {
				setDuplicateUserChallenge(context, Errors.USERNAME_IN_USE, Messages.USERNAME_EXISTS, AuthenticationFlowError.INVALID_USER);
			}
			return user;
		}

		testInvalidUser(context, user);
		return user;
	}

	private boolean validateUser(final AuthenticationFlowContext context, final UserModel user, final MultivaluedMap<String, String> inputData) {
		if (!enabledUser(context, user)) {
			return false;
		}
		final String rememberMe = inputData.getFirst("rememberMe");
		final boolean remember = rememberMe != null && rememberMe.equalsIgnoreCase("on");
		if (remember) {
			context.getAuthenticationSession().setAuthNote(Details.REMEMBER_ME, "true");
			context.getEvent().detail(Details.REMEMBER_ME, "true");
		} else {
			context.getAuthenticationSession().removeAuthNote(Details.REMEMBER_ME);
		}
		context.setUser(user);
		return true;
	}

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
		final MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
		final String loginHint = context.getAuthenticationSession().getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);

		final String rememberMeUsername = AuthenticationManager.getRememberMeUsername(context.getSession());

		if (context.getUser() != null) {
			final LoginFormsProvider form = context.form();
			form.setAttribute(LoginFormsProvider.USERNAME_HIDDEN, true);
			form.setAttribute(LoginFormsProvider.REGISTRATION_DISABLED, true);
			context.getAuthenticationSession().setAuthNote(USER_SET_BEFORE_USERNAME_PASSWORD_AUTH, "true");
		} else {
			context.getAuthenticationSession().removeAuthNote(USER_SET_BEFORE_USERNAME_PASSWORD_AUTH);
			if (loginHint != null || rememberMeUsername != null) {
				if (loginHint != null) {
					formData.add(AuthenticationManager.FORM_USERNAME, loginHint);
				} else {
					formData.add(AuthenticationManager.FORM_USERNAME, rememberMeUsername);
					formData.add("rememberMe", "on");
				}
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
