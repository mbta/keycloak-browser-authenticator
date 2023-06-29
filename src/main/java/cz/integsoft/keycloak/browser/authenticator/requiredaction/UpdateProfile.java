package cz.integsoft.keycloak.browser.authenticator.requiredaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.integsoft.keycloak.browser.authenticator.exception.QueueException;
import cz.integsoft.keycloak.browser.authenticator.model.ProfileUpdateEvent;
import cz.integsoft.keycloak.browser.authenticator.model.QueueConfig;
import cz.integsoft.keycloak.browser.authenticator.userprofile.EventAuditingAttributeChangeListener;

/**
 * Update profile required action rewrite.
 *
 * @author integsoft
 */
public class UpdateProfile implements RequiredActionProvider, RequiredActionFactory {

	private static Logger logger = Logger.getLogger(UpdateProfile.class);

	private static final String REQUIRED_ACTION_NAME = "MBTA_UPDATE_PROFILE";
	private static final String USER_ATTRIBUTE_PHONE_NAME = "phone_number";
	private static final String USER_ATTRIBUTE_PHONE_AREA_CODE = "phoneAreaCode";
	private static final String REGISTRATION_FORM_NAME_MOBILE_PHONE = "user.attributes.phone_number";
	private static final String REGISTRATION_FORM_NAME_MOBILE_AREA_CODE = "user.attributes.areacode";
	private static final String REGISTRATION_BAD_MOBILE_FORMAT = "registration.bad.format.phone_number";

	private static Map<String, QueueConfig> queueEnvConfig;

	@Override
	public InitiatedActionSupport initiatedActionSupport() {
		return InitiatedActionSupport.SUPPORTED;
	}

	@Override
	public void evaluateTriggers(final RequiredActionContext context) {
	}

	@Override
	public void requiredActionChallenge(final RequiredActionContext context) {
		context.challenge(createResponse(context, null, null));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void processAction(final RequiredActionContext context) {
		final EventBuilder event = context.getEvent();
		event.event(EventType.UPDATE_PROFILE);
		final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

		if (formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_PHONE) != null) {
			formData.putSingle(REGISTRATION_FORM_NAME_MOBILE_PHONE, formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_PHONE).replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("-", "").replaceAll("[ ]", ""));
		}

		final UserModel user = context.getUser();

		user.getFirstName();
		user.getLastName();
		user.getEmail();
		final String oldMobileNumber = user.getFirstAttribute(USER_ATTRIBUTE_PHONE_NAME);
		final String oldMobileAreaCode = user.getFirstAttribute(USER_ATTRIBUTE_PHONE_AREA_CODE);

		final String mobileNumber = formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_PHONE);
		final String mobileAreaCode = formData.getFirst(REGISTRATION_FORM_NAME_MOBILE_AREA_CODE);

		if (mobileNumber != null && !mobileNumber.isBlank()) {
			final Pattern p = Pattern.compile("^\\+[1-9]\\d{10}$");
			final Matcher m = p.matcher(mobileAreaCode + mobileNumber);
			if (!m.matches()) {
				final List<FormMessage> errors = new ArrayList<>();
				errors.add(new FormMessage(REGISTRATION_FORM_NAME_MOBILE_PHONE, REGISTRATION_BAD_MOBILE_FORMAT));
				context.challenge(createResponse(context, formData, errors));
				return;
			}
		}

		final UserProfileProvider provider = context.getSession().getProvider(UserProfileProvider.class);
		final UserProfile profile = provider.create(UserProfileContext.UPDATE_PROFILE, formData, user);

		try {
			final Map<String, String> updatedUserData = new HashMap<>();

			// backward compatibility with old account console where attributes are not removed if missing
			profile.update(false, new EventAuditingAttributeChangeListener(profile, event, updatedUserData));

			if (mobileNumber != null && !mobileNumber.isBlank()
					&& (oldMobileNumber == null || oldMobileNumber.isBlank() || !oldMobileNumber.equals(mobileNumber) || oldMobileAreaCode == null || oldMobileAreaCode.isBlank() || !oldMobileAreaCode.equals(mobileAreaCode))) {
				updatedUserData.put(USER_ATTRIBUTE_PHONE_NAME, mobileAreaCode + mobileNumber);
			}

			if (!updatedUserData.isEmpty()) {
				sendMessageToQueue(new ProfileUpdateEvent(user.getFirstAttribute("mbta_uuid"), updatedUserData));
			}

			context.success();
		} catch (final ValidationException pve) {
			final List<FormMessage> errors = Validation.getFormErrorsFromValidation(pve.getErrors());

			context.challenge(createResponse(context, formData, errors));
		} catch (final QueueException e) {
			final List<FormMessage> errors = new ArrayList<>();
			errors.add(new FormMessage(null, "registration.queue.error"));
			context.challenge(createResponse(context, formData, errors));
		}
	}

	/**
	 * Send updated user data to Queue.
	 *
	 * @param event updated user data
	 * @throws QueueException error
	 */
	private void sendMessageToQueue(final ProfileUpdateEvent event) throws QueueException {
		try {
			String env = System.getenv("awsEnv");
			logger.infof("System env %s", env);
			if (env == null) {
				env = System.getProperty("awsEnv");
				logger.infof("System property %s", env);
			}

			if (queueEnvConfig == null || queueEnvConfig.isEmpty() || queueEnvConfig.get(env) == null) {
				logger.error("Queue configuration was not recognized by environment");
				throw new QueueException("Queue configuration was not recognized by environment");
			}

			final ObjectMapper mapper = new ObjectMapper();
			final QueueConfig queueConfig = queueEnvConfig.get(env);

			// Create the connection factory based on the config
			final SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(), AmazonSQSClientBuilder.standard().withRegion(queueConfig.getQueueRegion()));

			// Create the connection
			final SQSConnection connection = connectionFactory.createConnection();

			// Create the nontransacted session with AUTO_ACKNOWLEDGE mode
			final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			for (final String queueAppName : queueConfig.getQueueAppNames()) {
				final StringBuffer queueName = new StringBuffer("keycloak-");
				queueName.append(env).append("-app-user-updates-").append(queueAppName);

				logger.infof("Queue name %s", queueName.toString());

				// Create a queue identity and specify the queue name to the session
				final Queue queue = session.createQueue(queueName.toString());

				// Create a producer for the 'MyQueue'
				final MessageProducer producer = session.createProducer(queue);

				// Create the text message
				final TextMessage message = session.createTextMessage(mapper.writeValueAsString(event));

				// Send the message
				producer.send(message);
				logger.infof("SQS Queue %s, JMS Message %s", queueName, message.getJMSMessageID());
			}
		} catch (final JMSException | JsonProcessingException | SdkClientException e) {
			logger.error("Queue problem catched ", e);
			throw new QueueException(e);
		}
	}

	protected UserModel.RequiredAction getResponseAction() {
		return UserModel.RequiredAction.UPDATE_PROFILE;
	}

	protected Response createResponse(final RequiredActionContext context, final MultivaluedMap<String, String> formData, final List<FormMessage> errors) {
		LoginFormsProvider form = context.form();

		if (errors != null && !errors.isEmpty()) {
			form.setErrors(errors);
		}

		if (formData != null) {
			form = form.setFormData(formData);
		}

		return form.createResponse(getResponseAction());
	}

	@Override
	public void close() {

	}

	@Override
	public RequiredActionProvider create(final KeycloakSession session) {
		return this;
	}

	@Override
	public void init(final Config.Scope config) {
		queueEnvConfig = new HashMap<>();
		queueEnvConfig.put("integsoft-sandbox", new QueueConfig(Arrays.asList("t-alerts", "alerts", "alerts-copy"), Regions.US_EAST_2));
		queueEnvConfig.put("dev", new QueueConfig(Arrays.asList("alerts-concierge-dev-green", "alerts-concierge-dev-blue"), Regions.US_EAST_1));
		queueEnvConfig.put("prod", new QueueConfig(Arrays.asList("alerts-concierge-prod"), Regions.US_EAST_1));
	}

	@Override
	public void postInit(final KeycloakSessionFactory factory) {

	}

	@Override
	public String getDisplayText() {
		return "MBTA Update Profile";
	}

	@Override
	public String getId() {
		return REQUIRED_ACTION_NAME;
	}
}
