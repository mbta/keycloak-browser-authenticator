package cz.integsoft.keycloak.browser.authenticator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.messages.Messages;

import jakarta.ws.rs.core.Response;

/**
 * IDP create user authenticator.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 * @author Integsoft
 */
public class IdpCreateUserIfUniqueAuthenticator extends org.keycloak.authentication.authenticators.broker.IdpCreateUserIfUniqueAuthenticator {

	private static Logger logger = Logger.getLogger(IdpCreateUserIfUniqueAuthenticator.class);

	private static final String USER_ATTRIBUTE_PHONE_NAME = "phone_number";
	private static final String USER_ATTRIBUTE_PHONE_AREA_CODE = "phoneAreaCode";
	private static final String USER_ATTRIBUTE_PHONE_COMP_NAME = "phone_comp";

	@Override
	protected void authenticateImpl(final AuthenticationFlowContext context, final SerializedBrokeredIdentityContext serializedCtx, final BrokeredIdentityContext brokerContext) {

		final KeycloakSession session = context.getSession();
		final RealmModel realm = context.getRealm();

		if (context.getAuthenticationSession().getAuthNote(EXISTING_USER_INFO) != null) {
			context.attempted();
			return;
		}

		final String username = getUsername(context, serializedCtx, brokerContext);
		if (username == null) {
			ServicesLogger.LOGGER.resetFlow(realm.isRegistrationEmailAsUsername() ? "Email" : "Username");
			context.getAuthenticationSession().setAuthNote(ENFORCE_UPDATE_PROFILE, "true");
			context.resetFlow();
			return;
		}

		final ExistingUserInfo duplication = checkExistingUser(context, username, serializedCtx, brokerContext);

		if (duplication == null) {
			logger.debugf("No duplication detected. Creating account for user '%s' and linking with identity provider '%s' .", username, brokerContext.getIdpConfig().getAlias());

			final UserModel federatedUser = session.users().addUser(realm, username);
			federatedUser.setEnabled(true);

			final String uuid = UUID.randomUUID().toString();
			federatedUser.setSingleAttribute("mbta_uuid", uuid);
			logger.infof("Added uuid %s to user %s", uuid, federatedUser.getUsername());

			if (federatedUser.getFirstAttribute(USER_ATTRIBUTE_PHONE_AREA_CODE) != null && federatedUser.getFirstAttribute(USER_ATTRIBUTE_PHONE_NAME) != null) {
				final String phoneComp = federatedUser.getFirstAttribute(USER_ATTRIBUTE_PHONE_AREA_CODE) + federatedUser.getFirstAttribute(USER_ATTRIBUTE_PHONE_NAME);
				federatedUser.setSingleAttribute(USER_ATTRIBUTE_PHONE_COMP_NAME, phoneComp);
				logger.infof("Added phone_comp %s to user %s", phoneComp, federatedUser.getUsername());
			}

			for (final Map.Entry<String, List<String>> attr : serializedCtx.getAttributes().entrySet()) {
				if (!UserModel.USERNAME.equalsIgnoreCase(attr.getKey())) {
					federatedUser.setAttribute(attr.getKey(), attr.getValue());
				}
			}

			final AuthenticatorConfigModel config = context.getAuthenticatorConfig();
			if (config != null && Boolean.parseBoolean(config.getConfig().get(IdpCreateUserIfUniqueAuthenticatorFactory.REQUIRE_PASSWORD_UPDATE_AFTER_REGISTRATION))) {
				logger.debugf("User '%s' required to update password", federatedUser.getUsername());
				federatedUser.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
			}

			userRegisteredSuccess(context, federatedUser, serializedCtx, brokerContext);

			context.setUser(federatedUser);
			context.getAuthenticationSession().setAuthNote(BROKER_REGISTERED_NEW_USER, "true");
			context.success();
		} else {
			logger.debugf("Duplication detected. There is already existing user with %s '%s' .", duplication.getDuplicateAttributeName(), duplication.getDuplicateAttributeValue());

			// Set duplicated user, so next authenticators can deal with it
			context.getAuthenticationSession().setAuthNote(EXISTING_USER_INFO, duplication.serialize());
			// Only show error message if the authenticator was required
			if (context.getExecution().isRequired()) {
				final Response challengeResponse = context.form().setError(Messages.FEDERATED_IDENTITY_EXISTS, duplication.getDuplicateAttributeName(), duplication.getDuplicateAttributeValue()).createErrorPage(Response.Status.CONFLICT);
				context.challenge(challengeResponse);
				context.getEvent().user(duplication.getExistingUserId()).detail("existing_" + duplication.getDuplicateAttributeName(), duplication.getDuplicateAttributeValue()).removeDetail(Details.AUTH_METHOD)
						.removeDetail(Details.AUTH_TYPE).error(Errors.FEDERATED_IDENTITY_EXISTS);
			} else {
				context.attempted();
			}
		}
	}
}
