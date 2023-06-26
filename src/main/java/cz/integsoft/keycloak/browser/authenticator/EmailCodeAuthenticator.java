package cz.integsoft.keycloak.browser.authenticator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.infinispan.Cache;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.Authenticator;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.theme.FreeMarkerException;
import org.keycloak.theme.FreeMarkerUtil;
import org.keycloak.theme.Theme;
import org.keycloak.theme.beans.MessageFormatterMethod;

import cz.integsoft.keycloak.browser.authenticator.model.EmailTemplate;
import cz.integsoft.keycloak.browser.authenticator.model.cache.CachedCode;

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

	private static final int I_1000 = 1000;
	private static final int I_9999 = 9999;

	private final Cache<String, CachedCode> secondFactorCache;

	private final FreeMarkerUtil freeMarker;

	/**
	 * Constructor.
	 *
	 * @param secondFactorCache cache
	 */
	public EmailCodeAuthenticator(final Cache<String, CachedCode> secondFactorCache) {
		this.secondFactorCache = secondFactorCache;
		this.freeMarker = new FreeMarkerUtil();
	}

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
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

		if (user.getFirstAttribute(NUMBER_OF_LOGIN_ATTR) == null) {
			user.setSingleAttribute(NUMBER_OF_LOGIN_ATTR, "1");
			context.success();
			return;
		}
		if (!user.getFirstAttribute(NUMBER_OF_LOGIN_ATTR).equals("9")) {
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

		final int randomNumber = getRandomNumberUsingInts(I_1000, I_9999);

		log.debugf("Generated random code for user email %s is %d", user.getEmail(), randomNumber);
		secondFactorCache.put(user.getEmail(), new CachedCode(randomNumber));

		try {
			final Map<String, Object> attributes = new HashMap<>();
			attributes.put("code", randomNumber);
			send(context, user, processTemplate(context, user, EMAIL_SUBJECT, new ArrayList<>(), EMAIL_TEMPLATE, attributes));
		} catch (final EmailException e) {
			log.error("Error send email", e);
			secondFactorCache.remove(user.getEmail());
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
	 * Generates a random number in the range of values.
	 *
	 * @param min minimum
	 * @param max maximum
	 * @return random number
	 */
	private int getRandomNumberUsingInts(final int min, final int max) {
		final Random random = new Random();
		return random.ints(min, max).findFirst().getAsInt();
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

		final String code = formData.getFirst(FTL_CODE_NAME);
		int codeInt = 0;
		try {
			codeInt = Integer.parseInt(code);
		} catch (final NumberFormatException e) {
			final Response challenge = invalidEmailCode(context);
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
			return;
		}

		final CachedCode generatedCode = secondFactorCache.get(context.getUser().getEmail());

		if (generatedCode == null || generatedCode.getCode() != codeInt) {
			log.debugf("Verify second factor code - user email %s, code %s - bad", context.getUser().getEmail(), code);
			final Response challenge = invalidEmailCode(context);
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
			return;
		}
		log.debugf("Verify second factor code - user email %s, code %s - correct", context.getUser().getEmail(), code);
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
