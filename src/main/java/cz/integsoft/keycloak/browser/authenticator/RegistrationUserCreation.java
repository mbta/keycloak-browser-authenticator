package cz.integsoft.keycloak.browser.authenticator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.ws.rs.core.MultivaluedMap;

import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

/**
 * Registration user creation flow action.
 *
 * @author integsoft
 */
public class RegistrationUserCreation implements FormAction, FormActionFactory {

	public static final String PROVIDER_ID = "mbta-registration-user-creation";

	private static final String REGISTRATION_FORBIDDEN_USERNAME = "registration.forbidden.username";
	private static final String REGISTRATION_FORBIDDEN_EMAIL = "registration.forbidden.email";
	private static final String EMAIL_MBTA_DOMAIN = "@mbta.com";

	@Override
	public String getHelpText() {
		return "This action must always be first! Validates the username of the user in validation phase.  In success phase, this will create the user in the database.";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return null;
	}

	@Override
	public void validate(final ValidationContext context) {
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		context.getEvent().detail(Details.REGISTER_METHOD, "form");

		final KeycloakSession session = context.getSession();
		final UserProfileProvider profileProvider = session.getProvider(UserProfileProvider.class);
		final UserProfile profile = profileProvider.create(UserProfileContext.REGISTRATION_USER_CREATION, formData);
		final String email = profile.getAttributes().getFirstValue(UserModel.EMAIL);

		final String username = profile.getAttributes().getFirstValue(UserModel.USERNAME);
		final String firstName = profile.getAttributes().getFirstValue(UserModel.FIRST_NAME);
		final String lastName = profile.getAttributes().getFirstValue(UserModel.LAST_NAME);
		context.getEvent().detail(Details.EMAIL, email);

		context.getEvent().detail(Details.USERNAME, username);
		context.getEvent().detail(Details.FIRST_NAME, firstName);
		context.getEvent().detail(Details.LAST_NAME, lastName);

		if (context.getRealm().isRegistrationEmailAsUsername()) {
			context.getEvent().detail(Details.USERNAME, email);
		}

		List<FormMessage> errors = new ArrayList<>();
		if (username.toLowerCase(Locale.US).contains(EMAIL_MBTA_DOMAIN)) {
			errors.add(new FormMessage(UserModel.USERNAME, REGISTRATION_FORBIDDEN_USERNAME));
		}
		if (email.toLowerCase(Locale.US).contains(EMAIL_MBTA_DOMAIN)) {
			errors.add(new FormMessage(UserModel.EMAIL, REGISTRATION_FORBIDDEN_EMAIL));
		}
		if (!errors.isEmpty()) {
			context.validationError(formData, errors);
			return;
		}

		try {
			profile.validate();
		} catch (final ValidationException pve) {
			errors = Validation.getFormErrorsFromValidation(pve.getErrors());

			if (pve.hasError(Messages.EMAIL_EXISTS)) {
				context.error(Errors.EMAIL_IN_USE);
			} else if (pve.hasError(Messages.MISSING_EMAIL, Messages.MISSING_USERNAME, Messages.INVALID_EMAIL)) {
				context.error(Errors.INVALID_REGISTRATION);
			} else if (pve.hasError(Messages.USERNAME_EXISTS)) {
				context.error(Errors.USERNAME_IN_USE);
			}

			context.validationError(formData, errors);
			return;
		}
		context.success();
	}

	@Override
	public void buildPage(final FormContext context, final LoginFormsProvider form) {

	}

	@Override
	public void success(final FormContext context) {
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		final String email = formData.getFirst(UserModel.EMAIL);
		String username = formData.getFirst(UserModel.USERNAME);

		if (context.getRealm().isRegistrationEmailAsUsername()) {
			username = email;
		}

		context.getEvent().detail(Details.USERNAME, username).detail(Details.REGISTER_METHOD, "form").detail(Details.EMAIL, email);

		final KeycloakSession session = context.getSession();

		final UserProfileProvider profileProvider = session.getProvider(UserProfileProvider.class);
		final UserProfile profile = profileProvider.create(UserProfileContext.REGISTRATION_USER_CREATION, formData);
		final UserModel user = profile.create();

		user.setEnabled(true);

		context.setUser(user);

		context.getAuthenticationSession().setClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM, username);

		context.getEvent().user(user);
		context.getEvent().success();
		context.newEvent().event(EventType.LOGIN);
		context.getEvent().client(context.getAuthenticationSession().getClient().getClientId()).detail(Details.REDIRECT_URI, context.getAuthenticationSession().getRedirectUri()).detail(Details.AUTH_METHOD,
				context.getAuthenticationSession().getProtocol());
		final String authType = context.getAuthenticationSession().getAuthNote(Details.AUTH_TYPE);
		if (authType != null) {
			context.getEvent().detail(Details.AUTH_TYPE, authType);
		}
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
	public boolean isUserSetupAllowed() {
		return false;
	}

	@Override
	public void close() {

	}

	@Override
	public String getDisplayType() {
		return "MBTA Registration User Creation";
	}

	@Override
	public String getReferenceCategory() {
		return null;
	}

	@Override
	public boolean isConfigurable() {
		return false;
	}

	private static AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = { AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED };

	@Override
	public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
		return REQUIREMENT_CHOICES;
	}

	@Override
	public FormAction create(final KeycloakSession session) {
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
