package cz.integsoft.keycloak.browser.authenticator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.FreeMarkerException;
import org.keycloak.theme.Theme;
import org.keycloak.theme.beans.MessageFormatterMethod;
import org.keycloak.theme.freemarker.FreeMarkerProvider;

import cz.integsoft.keycloak.browser.authenticator.model.EmailTemplate;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/**
 * Authenticator sending and checking the generated code sent by email.
 *
 * @author integsoft
 */
public class EmailCodeAuthenticator extends AbstractLoginFormAuthenticator implements Authenticator {

	private static Logger log = Logger.getLogger(EmailCodeAuthenticator.class);

	private static final String USER_NOT_FOUND = "secondFactor.userNotFound";
	private static final String USER_NO_EMAIL = "secondFactor.userNoEmail";
	private static final String SEND_EMAIL_ERROR = "secondFactor.sendEmailError";
	private static final String EMAIL_SUBJECT = "secondFactor.emailSubject";
	private static final String EMAIL_TEMPLATE = "second-factor-code.ftl";
	private static final String FTL_CODE_NAME = "email_code";
	private static final String SKIP_MFA_ATTR = "skip_mfa";
	private static final String NUMBER_OF_LOGIN_ATTR = "number_of_login";
	private static final long THOUSAND_LONG = 1000L;

	private final FreeMarkerProvider freeMarker;

	private final Properties properties;

	/**
	 * Constructor.
	 *
	 * @param session {@link KeycloakSession}
	 * @param properties authenticator properties
	 */
	public EmailCodeAuthenticator(final KeycloakSession session, final Properties properties) {
		this.freeMarker = session.getProvider(FreeMarkerProvider.class);
		this.properties = properties;
	}

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
		final AuthenticatorConfigModel config = context.getAuthenticatorConfig();
		final AuthenticationSessionModel session = context.getAuthenticationSession();

		final UserModel user = context.getUser();
		if (user == null) {
			returnFailure(context, USER_NOT_FOUND);
			return;
		}

		if (user.getFirstAttribute(SKIP_MFA_ATTR) != null && user.getFirstAttribute(SKIP_MFA_ATTR).equals("true")) {
			log.debugf("User %s - skip MFA = true", user.getUsername());
			context.success();
			return;
		}

		if (properties.getProperty("skipMFA.clients") != null && context.getAuthenticationSession().getClient().getClientId().equalsIgnoreCase(properties.getProperty("skipMFA.clients"))) {
			log.debugf("Client %s - skip MFA", context.getAuthenticationSession().getClient().getClientId());
			context.success();
			return;
		}

		int length = EmailCodeAuthenticatorFactory.DEFAULT_LENGTH;
		int loginCount = EmailCodeAuthenticatorFactory.DEFAULT_LOGIN_COUNT;
		int ttl = EmailCodeAuthenticatorFactory.DEFAULT_TTL;

		if (config != null) {
			// get config values
			length = Integer.parseInt(config.getConfig().get(EmailCodeAuthenticatorFactory.CODE_LENGTH));
			loginCount = Integer.parseInt(config.getConfig().get(EmailCodeAuthenticatorFactory.LOGIN_COUNT));
			ttl = Integer.parseInt(config.getConfig().get(EmailCodeAuthenticatorFactory.CODE_TTL));
		}

		if (loginCount > 1 && user.getFirstAttribute(NUMBER_OF_LOGIN_ATTR) == null) {
			user.setSingleAttribute(NUMBER_OF_LOGIN_ATTR, "1");
			context.success();
			return;
		}
		if (loginCount > 1 && Integer.parseInt(user.getFirstAttribute(NUMBER_OF_LOGIN_ATTR)) + 1 < loginCount) {
			final int numberOflogin = Integer.parseInt(user.getFirstAttribute(NUMBER_OF_LOGIN_ATTR)) + 1;
			user.setSingleAttribute(NUMBER_OF_LOGIN_ATTR, String.valueOf(numberOflogin));
			context.success();
			return;
		}

		if (user.getEmail() == null) {
			log.warnf("User %s has no email", user.getUsername());
			returnFailure(context, USER_NO_EMAIL);
			return;
		}

		final String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);

		log.debugf("Generated random code for user email %s is %d", user.getEmail(), code);
		session.setAuthNote(EmailCodeAuthenticatorFactory.CODE, code);
		session.setAuthNote(EmailCodeAuthenticatorFactory.CODE_TTL, Long.toString(System.currentTimeMillis() + (ttl * THOUSAND_LONG)));

		try {
			final Map<String, Object> attributes = new HashMap<>();
			attributes.put("code", code);
			send(context, user, processTemplate(context, user, EMAIL_SUBJECT, new ArrayList<>(), EMAIL_TEMPLATE, attributes));
		} catch (final EmailException e) {
			log.error("Error send email", e);
			session.removeAuthNote(EmailCodeAuthenticatorFactory.CODE);
			session.removeAuthNote(EmailCodeAuthenticatorFactory.CODE_TTL);
			returnFailure(context, SEND_EMAIL_ERROR);
			return;
		}

		final Response challengeResponse = challenge(context);
		context.getAuthenticationSession().setClientNote(AuthenticationProcessor.CURRENT_AUTHENTICATION_EXECUTION, context.getExecution().getId());
		context.challenge(challengeResponse);
	}

	/**
	 * Send email.
	 *
	 * @param context {@link AuthenticationFlowContext}
	 * @param user log in user
	 * @param template email template
	 * @throws EmailException send email problem
	 */
	protected void send(final AuthenticationFlowContext context, final UserModel user, final EmailTemplate template) throws EmailException {
		final EmailSenderProvider emailSender = context.getSession().getProvider(EmailSenderProvider.class);
		emailSender.send(context.getRealm().getSmtpConfig(), user, template.getSubject(), template.getTextBody(), template.getHtmlBody());
	}

	/**
	 * Create email template.
	 *
	 * @param context {@link AuthenticationFlowContext}
	 * @param user {@link UserModel}
	 * @param subjectKey subject key
	 * @param subjectAttributes subject attributes
	 * @param template template
	 * @param attributes template attributes
	 * @return {@link EmailTemplate}
	 * @throws EmailException template creation error
	 */
	private EmailTemplate processTemplate(final AuthenticationFlowContext context, final UserModel user, final String subjectKey, final List<Object> subjectAttributes, final String template, final Map<String, Object> attributes)
			throws EmailException {
		try {
			final Theme theme = context.getSession().theme().getTheme(Theme.Type.EMAIL);
			final Locale locale = context.getSession().getContext().resolveLocale(user);
			attributes.put("locale", locale);
			final Properties rb = theme.getMessages(locale);
			final Map<String, String> localizationTexts = context.getRealm().getRealmLocalizationTextsByLocale(locale.toLanguageTag());
			rb.putAll(localizationTexts);
			attributes.put("msg", new MessageFormatterMethod(locale, rb));
			attributes.put("properties", theme.getProperties());
			final String subject = new MessageFormat(rb.getProperty(subjectKey, subjectKey), locale).format(subjectAttributes.toArray());
			final String textTemplate = String.format("text/%s", template);
			String textBody;
			try {
				textBody = freeMarker.processTemplate(attributes, textTemplate, theme);
			} catch (final FreeMarkerException e) {
				throw new EmailException("Failed to template plain text email.", e);
			}
			final String htmlTemplate = String.format("html/%s", template);
			String htmlBody;
			try {
				htmlBody = freeMarker.processTemplate(attributes, htmlTemplate, theme);
			} catch (final FreeMarkerException e) {
				throw new EmailException("Failed to template html email.", e);
			}

			return new EmailTemplate(subject, textBody, htmlBody);
		} catch (final Exception e) {
			throw new EmailException("Failed to template email", e);
		}
	}

	/**
	 * Error response.
	 *
	 * @param context login flow context
	 * @param message error message
	 * @return {@link Response}
	 */
	private Response returnFailure(final AuthenticationFlowContext context, final String message) {
		context.fork();
		return context.form().setError(message).createForm(FORM_FTL_EMAIL_CODE);
	}

	@Override
	public void action(final AuthenticationFlowContext context) {
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		final AuthenticationSessionModel session = context.getAuthenticationSession();
		final String code = session.getAuthNote(EmailCodeAuthenticatorFactory.CODE);
		final String ttl = session.getAuthNote(EmailCodeAuthenticatorFactory.CODE_TTL);
		final String enteredCode = formData.getFirst(FTL_CODE_NAME);

		if (code == null || !code.equals(enteredCode)) {
			log.debugf("Verify second factor code - user email %s, code %s - bad", context.getUser().getEmail(), enteredCode);
			context.getEvent().error(Errors.INVALID_CODE);
			final Response challenge = invalidEmailCode(context);
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
			return;
		}
		log.debugf("Verify second factor code - user email %s, code %s - correct", context.getUser().getEmail(), enteredCode);
		if (Long.parseLong(ttl) < System.currentTimeMillis()) {
			// expired
			log.debugf("Second factor code - user email %s, code %s - expired", context.getUser().getEmail(), enteredCode);
			context.getEvent().error(Errors.EXPIRED_CODE);
			final Response challenge = emailCodeExpired(context);
			context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE, challenge);
			return;
		}
		// valid
		context.getAuthenticationSession().removeAuthNote(EmailCodeAuthenticatorFactory.CODE);
		if (context.getUser().getFirstAttribute(SKIP_MFA_ATTR) == null || !context.getUser().getFirstAttribute(SKIP_MFA_ATTR).equals("true")) {
			context.getUser().removeAttribute(NUMBER_OF_LOGIN_ATTR);
		}
		context.success();
	}

	/**
	 * Create an email code verification form.
	 *
	 * @param context {@link AuthenticationFlowContext}
	 * @return {@link Response}
	 */
	private Response challenge(final AuthenticationFlowContext context) {
		final LoginFormsProvider forms = context.form();
		return forms.createForm(FORM_FTL_EMAIL_CODE);
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
	public void close() {

	}
}
