package cz.integsoft.keycloak.browser.authenticator;

import java.util.List;
import java.util.Locale;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.DefaultActionTokenKey;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

public class ResetCredentialChooseUser implements Authenticator, AuthenticatorFactory {

	private static final Logger logger = Logger.getLogger(ResetCredentialChooseUser.class);

	public static final String PROVIDER_ID = "mbta-reset-credentials-choose-user";

	private static final String EMAIL_MBTA_DOMAIN = "@mbta.com";

	private static final String RESET_PASSWORD_FORBIDDEN = "resetpassword.forbidden";

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
		final String existingUserId = context.getAuthenticationSession().getAuthNote(AbstractIdpAuthenticator.EXISTING_USER_INFO);
		if (existingUserId != null) {
			final UserModel existingUser = AbstractIdpAuthenticator.getExistingUser(context.getSession(), context.getRealm(), context.getAuthenticationSession());

			logger.debugf("Forget-password triggered when reauthenticating user after first broker login. Prefilling reset-credential-choose-user screen with user '%s' ", existingUser.getUsername());
			context.setUser(existingUser);
			final Response challenge = context.form().createPasswordReset();
			context.challenge(challenge);
			return;
		}

		final String actionTokenUserId = context.getAuthenticationSession().getAuthNote(DefaultActionTokenKey.ACTION_TOKEN_USER_ID);
		if (actionTokenUserId != null) {
			final UserModel existingUser = context.getSession().users().getUserById(context.getRealm(), actionTokenUserId);

			// Action token logics handles checks for user ID validity and user being enabled

			logger.debugf("Forget-password triggered when reauthenticating user after authentication via action token. Skipping reset-credential-choose-user screen and using user '%s' ", existingUser.getUsername());
			context.setUser(existingUser);
			context.success();
			return;
		}

		final Response challenge = context.form().createPasswordReset();
		context.challenge(challenge);
	}

	@Override
	public void action(final AuthenticationFlowContext context) {
		final EventBuilder event = context.getEvent();
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		String username = formData.getFirst("username");
		if (username == null || username.isEmpty()) {
			event.error(Errors.USERNAME_MISSING);
			final Response challenge = context.form().addError(new FormMessage(Validation.FIELD_USERNAME, Messages.MISSING_USERNAME)).createPasswordReset();
			context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
			return;
		}

		username = username.trim();

		if (username.toLowerCase(Locale.US).contains(EMAIL_MBTA_DOMAIN)) {
			final Response challenge = context.form().addError(new FormMessage(Validation.FIELD_USERNAME, RESET_PASSWORD_FORBIDDEN)).createPasswordReset();
			context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
			return;
		}

		final RealmModel realm = context.getRealm();
		UserModel user = context.getSession().users().getUserByUsername(realm, username);
		if (user == null && realm.isLoginWithEmailAllowed() && username.contains("@")) {
			user = context.getSession().users().getUserByEmail(realm, username);
		}

		context.getAuthenticationSession().setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, username);

		// we don't want people guessing usernames, so if there is a problem, just continue, but don't set the user
		// a null user will notify further executions, that this was a failure.
		if (user == null) {
			event.clone().detail(Details.USERNAME, username).error(Errors.USER_NOT_FOUND);
			context.clearUser();
		} else if (!user.isEnabled()) {
			event.clone().detail(Details.USERNAME, username).user(user).error(Errors.USER_DISABLED);
			context.clearUser();
		} else {
			context.setUser(user);
		}

		context.success();
	}

	@Override
	public boolean requiresUser() {
		return false;
	}

	@Override
	public boolean configuredFor(final KeycloakSession session, final RealmModel realm, final UserModel user) {
		return true;
	}

	@Override
	public void setRequiredActions(final KeycloakSession session, final RealmModel realm, final UserModel user) {

	}

	@Override
	public String getDisplayType() {
		return "MBTA Choose User";
	}

	@Override
	public String getReferenceCategory() {
		return null;
	}

	@Override
	public boolean isConfigurable() {
		return false;
	}

	public static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = { AuthenticationExecutionModel.Requirement.REQUIRED };

	@Override
	public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
		return REQUIREMENT_CHOICES;
	}

	@Override
	public boolean isUserSetupAllowed() {
		return false;
	}

	@Override
	public String getHelpText() {
		return "Choose a user to reset credentials for";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return null;
	}

	@Override
	public void close() {

	}

	@Override
	public Authenticator create(final KeycloakSession session) {
		return this;
	}

	@Override
	public void init(final Config.Scope config) {

	}

	@Override
	public void postInit(final KeycloakSessionFactory factory) {

	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}
}
