package cz.integsoft.keycloak.browser.authenticator;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationPage;
import org.keycloak.authentication.requiredactions.TermsAndConditions;
import org.keycloak.common.util.Time;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.Constants;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.ClientSessionCode;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.resources.LoginActionsService;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.Attributes;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Registration user creation flow action.
 *
 * @author integsoft
 */
public class RegistrationUserCreation implements FormAction, FormActionFactory {

	private static Logger logger = Logger.getLogger(RegistrationUserCreation.class);

	public static final String PROVIDER_ID = "mbta-registration-user-creation";

	private static final String REGISTRATION_FORBIDDEN_EMAIL = "registration.forbidden.email";
	private static final String EMAIL_MBTA_DOMAIN = "@mbta.com";
	private static final String REGISTRATION_BAD_MOBILE_FORMAT = "registration.bad.format.phone_number";
	private static final String REGISTRATION_FORM_NAME_MOBILE_AREA_CODE = "user.attributes.areacode";
	private static final String REGISTRATION_FORM_NAME_MOBILE_PHONE = "user.attributes.phone_number";
	private static final String REGISTRATION_FORM_TERMS_OF_USE = "terms_of_use";

	protected static final String FIELD = "termsAccepted";

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
		trimPhone(formData);
		context.getEvent().detail(Details.REGISTER_METHOD, "form");

		final UserProfile profile = getOrCreateUserProfile(context, formData);
		final Attributes attributes = profile.getAttributes();

		final String email = attributes.getFirst(UserModel.EMAIL);
		final String username = attributes.getFirst(UserModel.USERNAME);
		final String firstName = attributes.getFirst(UserModel.FIRST_NAME);
		final String lastName = attributes.getFirst(UserModel.LAST_NAME);

		context.getEvent().detail(Details.EMAIL, email);
		context.getEvent().detail(Details.USERNAME, username);
		context.getEvent().detail(Details.FIRST_NAME, firstName);
		context.getEvent().detail(Details.LAST_NAME, lastName);

		if (context.getRealm().isRegistrationEmailAsUsername()) {
			context.getEvent().detail(Details.USERNAME, email);
		}

		final List<FormMessage> errors = new ArrayList<>();
		final String token = formData.getFirst("token");
		final String robot = formData.getFirst("robot");
		final String mobileAreaCode = formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_AREA_CODE);
		final String mobileNumber = formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_PHONE);
		final String termsOfUse = formData.getFirst(REGISTRATION_FORM_TERMS_OF_USE);

		if ((token != null && !token.equals(String.valueOf(LocalDate.now().getYear())) || robot != null)) {
			errors.add(new FormMessage(null, "login.error.robot"));
			context.validationError(formData, errors);
			return;
		}

		if (termsOfUse == null || !termsOfUse.equals("on")) {
			errors.add(new FormMessage(REGISTRATION_FORM_TERMS_OF_USE, "termsOfUseRequired"));
		}

		if (email != null && email.toLowerCase(Locale.US).contains(EMAIL_MBTA_DOMAIN)) {
			final IdentityProviderModel idpm = getFirstIdentityProvider(context);
			final String loginUrl = Urls.identityProviderAuthnRequest(prepareBaseUriBuilder(context), idpm.getAlias(), context.getRealm().getName()).toString();
			errors.add(new FormMessage(UserModel.EMAIL, REGISTRATION_FORBIDDEN_EMAIL, idpm != null ? loginUrl : ""));
		}
		if (mobileNumber != null && !mobileNumber.isBlank()) {
			final Pattern p = Pattern.compile("^\\+[1-9]\\d{10}$");
			final String mob = mobileAreaCode + mobileNumber;
			logger.debugf("Validate mobile number %s", mob);
			final Matcher m = p.matcher(mob);
			if (!m.matches()) {
				errors.add(new FormMessage(REGISTRATION_FORM_NAME_MOBILE_PHONE, REGISTRATION_BAD_MOBILE_FORMAT));
			}
		}

		if (!errors.isEmpty()) {
			context.validationError(formData, errors);
			return;
		}

		try {
			profile.validate();
		} catch (final ValidationException pve) {
			final List<FormMessage> errs = Validation.getFormErrorsFromValidation(pve.getErrors());
			if (errs != null) {
				errors.addAll(errs);
			}

			if (pve.hasError(Messages.EMAIL_EXISTS, Messages.INVALID_EMAIL)) {
				context.getEvent().detail(Details.EMAIL, attributes.getFirst(UserModel.EMAIL));
			}

			if (pve.hasError(Messages.EMAIL_EXISTS)) {
				context.error(Errors.EMAIL_IN_USE);
			} else if (pve.hasError(Messages.MISSING_EMAIL, Messages.MISSING_USERNAME, Messages.INVALID_EMAIL)) {
				context.error(Errors.INVALID_REGISTRATION);
			} else if (pve.hasError(Messages.USERNAME_EXISTS)) {
				context.error(Errors.USERNAME_IN_USE);
			}
			if (context.getRealm().isRegistrationEmailAsUsername() && errors.stream().anyMatch(e -> e.getField().equalsIgnoreCase("username"))) {
				errors.removeIf(e -> e != null && e.getField().equalsIgnoreCase("username"));
			}
			if (context.getRealm().isRegistrationEmailAsUsername() && errors.stream().anyMatch(e -> e.getField().equalsIgnoreCase("firstName"))) {
				errors.removeIf(e -> e != null && e.getField().equalsIgnoreCase("firstName"));
				errors.add(new FormMessage("firstName", "missingFirstNameMessage"));
			}
			if (context.getRealm().isRegistrationEmailAsUsername() && errors.stream().anyMatch(e -> e.getField().equalsIgnoreCase("lastName"))) {
				errors.removeIf(e -> e != null && e.getField().equalsIgnoreCase("lastName"));
				errors.add(new FormMessage("lastName", "missingLastNameMessage"));
			}
			context.validationError(formData, errors);
			return;
		}
		if (mobileNumber != null && !mobileNumber.isBlank()) {
			context.getEvent().detail("mobileNumber", mobileAreaCode + mobileNumber);
		}
		context.success();
	}

	private void trimPhone(final MultivaluedMap<String, String> formData) {
		if (formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_PHONE) != null) {
			formData.putSingle(REGISTRATION_FORM_NAME_MOBILE_PHONE, formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_PHONE).replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("-", "").replaceAll("[ ]", ""));
		}
	}

	private URI prepareBaseUriBuilder(final ValidationContext context) {
		final String requestURI = context.getUriInfo().getBaseUri().getPath();
		final UriBuilder uriBuilder = UriBuilder.fromUri(requestURI);
		uriBuilder.replaceQuery(null);
		uriBuilder.queryParam(Constants.CLIENT_ID, context.getAuthenticationSession().getClient().getClientId());
		uriBuilder.queryParam(Constants.TAB_ID, context.getAuthenticationSession().getTabId());

		final ClientSessionCode accessCode = new ClientSessionCode(context.getSession(), context.getRealm(), context.getAuthenticationSession());
		context.getAuthenticationSession().getParentSession().setTimestamp(Time.currentTime());
		final String accessCodeString = accessCode.getOrGenerateCode();
		uriBuilder.queryParam(LoginActionsService.SESSION_CODE, accessCodeString);

		return uriBuilder.build();
	}

	/**
	 * Find first identity provider.
	 *
	 * @param context {@link ValidationContext}
	 * @return provider model or null
	 */
	private IdentityProviderModel getFirstIdentityProvider(final ValidationContext context) {
		return context.getRealm().getIdentityProvidersStream().findFirst().orElse(null);
	}

	@Override
	public void buildPage(final FormContext context, final LoginFormsProvider form) {
		checkNotOtherUserAuthenticating(context);
	}

	@Override
	public void success(final FormContext context) {
		checkNotOtherUserAuthenticating(context);

		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		final String email = formData.getFirst(UserModel.EMAIL);
		String username = formData.getFirst(UserModel.USERNAME);

		trimPhone(formData);

		if (context.getRealm().isRegistrationEmailAsUsername()) {
			username = email;
		}

		context.getEvent().detail(Details.USERNAME, username).detail(Details.REGISTER_METHOD, "form").detail(Details.EMAIL, email);

		final String uuid = UUID.randomUUID().toString();

		formData.add("user.attributes.mbta_uuid", uuid);

		final UserProfile profile = getOrCreateUserProfile(context, formData);
		final UserModel user = profile.create();

		user.setEnabled(true);

		if ("on".equals(formData.getFirst(FIELD))) {
			// if accepted terms and conditions checkbox, remove action and add the attribute if enabled
			final RequiredActionProviderModel tacModel = context.getRealm().getRequiredActionProviderByAlias(UserModel.RequiredAction.TERMS_AND_CONDITIONS.name());
			if (tacModel != null && tacModel.isEnabled()) {
				user.setSingleAttribute(TermsAndConditions.USER_ATTRIBUTE, Integer.toString(Time.currentTime()));
				context.getAuthenticationSession().removeRequiredAction(UserModel.RequiredAction.TERMS_AND_CONDITIONS);
				user.removeRequiredAction(UserModel.RequiredAction.TERMS_AND_CONDITIONS);
			}
		}

		logger.infof("Added uuid %s to user %s", uuid, user.getUsername());

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
		return "MBTA Registration User Profile Creation";
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

	private void checkNotOtherUserAuthenticating(final FormContext context) {
		if (context.getUser() != null) {
			// the user probably did some back navigation in the browser, hitting this page in a strange state
			context.getEvent().detail(Details.EXISTING_USER, context.getUser().getUsername());
			throw new AuthenticationFlowException(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR, Errors.DIFFERENT_USER_AUTHENTICATING, Messages.EXPIRED_ACTION);
		}
	}

	private MultivaluedMap<String, String> normalizeFormParameters(final MultivaluedMap<String, String> formParams) {
		final MultivaluedHashMap<String, String> copy = new MultivaluedHashMap<>(formParams);

		// Remove "password" and "password-confirm" to avoid leaking them in the user-profile data
		copy.remove(RegistrationPage.FIELD_PASSWORD);
		copy.remove(RegistrationPage.FIELD_PASSWORD_CONFIRM);

		return copy;
	}

	/**
	 * Get user profile instance for current HTTP request (KeycloakSession) and for given context. This assumes that there is single user registered within HTTP request, which is always the case in Keycloak
	 */
	public UserProfile getOrCreateUserProfile(final FormContext formContext, MultivaluedMap<String, String> formData) {
		final KeycloakSession session = formContext.getSession();
		UserProfile profile = (UserProfile) session.getAttribute("UP_REGISTER");
		if (profile == null) {
			formData = normalizeFormParameters(formData);
			final UserProfileProvider profileProvider = session.getProvider(UserProfileProvider.class);
			profile = profileProvider.create(UserProfileContext.REGISTRATION, formData);
			session.setAttribute("UP_REGISTER", profile);
		}
		return profile;
	}
}
