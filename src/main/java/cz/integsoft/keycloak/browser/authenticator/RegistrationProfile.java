package cz.integsoft.keycloak.browser.authenticator;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

/**
 * Registration profile.
 *
 * @author integsoft
 */
public class RegistrationProfile implements FormAction, FormActionFactory {

	public static final String PROVIDER_ID = "mbta-registration-profile-action";

	// private static Logger logger = Logger.getLogger(RegistrationProfile.class);

	@Override
	public String getHelpText() {
		return "Validates email, first name, and last name attributes and stores them in user data.";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return null;
	}

	@Override
	public void validate(final org.keycloak.authentication.ValidationContext context) {
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		context.getEvent().detail(Details.REGISTER_METHOD, "form");

		final UserProfileProvider profileProvider = context.getSession().getProvider(UserProfileProvider.class);
		final UserProfile profile = profileProvider.create(UserProfileContext.REGISTRATION_PROFILE, formData);

		try {
			profile.validate();
		} catch (final ValidationException pve) {
			final List<FormMessage> errors = Validation.getFormErrorsFromValidation(pve.getErrors());

			if (pve.hasError(Messages.EMAIL_EXISTS, Messages.INVALID_EMAIL)) {
				context.getEvent().detail(Details.EMAIL, profile.getAttributes().getFirstValue(UserModel.EMAIL));
			}

			if (pve.hasError(Messages.EMAIL_EXISTS)) {
				context.error(Errors.EMAIL_IN_USE);
			} else {
				context.error(Errors.INVALID_REGISTRATION);
			}

			if (context.getRealm().isRegistrationEmailAsUsername() && errors.stream().anyMatch(e -> e.getField().equalsIgnoreCase("username"))) {
				errors.remove(errors.stream().filter(e -> e.getField().equalsIgnoreCase("username")).findFirst().get());
			}

			context.validationError(formData, errors);

			return;
		}

		context.success();
	}

	@Override
	public void success(final FormContext context) {
		final UserModel user = context.getUser();
		final UserProfileProvider provider = context.getSession().getProvider(UserProfileProvider.class);
		provider.create(UserProfileContext.REGISTRATION_PROFILE, context.getHttpRequest().getDecodedFormParameters(), user).update();
	}

	@Override
	public void buildPage(final FormContext context, final LoginFormsProvider form) {
		// complete
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
		return "MBTA Profile Validation";
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
