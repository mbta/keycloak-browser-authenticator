package cz.integsoft.keycloak.browser.authenticator;

import java.util.UUID;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Post login IDP authenticator.
 *
 * @author integsoft
 */
public class IdpPostLoginAuthenticator implements Authenticator {

	private static Logger logger = Logger.getLogger(IdpPostLoginAuthenticator.class);

	private static final String USER_ATTRIBUTE_PHONE_NAME = "phone_number";
	private static final String USER_ATTRIBUTE_PHONE_AREA_CODE = "phoneAreaCode";
	private static final String USER_ATTRIBUTE_PHONE_COMP_NAME = "phone_comp";

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
		final UserModel user = context.getUser();

		if (user == null) {
			logger.errorf("Post process IDP login failed, username not found in context");
			context.resetFlow();
			return;
		}

		if (user.getFirstAttribute(USER_ATTRIBUTE_PHONE_AREA_CODE) != null && user.getFirstAttribute(USER_ATTRIBUTE_PHONE_NAME) != null) {
			final String phoneComp = user.getFirstAttribute(USER_ATTRIBUTE_PHONE_AREA_CODE) + user.getFirstAttribute(USER_ATTRIBUTE_PHONE_NAME);
			user.setSingleAttribute(USER_ATTRIBUTE_PHONE_COMP_NAME, phoneComp);
			logger.infof("Added phone_comp %s to user %s", phoneComp, user.getUsername());
		}

		context.success();
	}

	@Override
	public void action(final AuthenticationFlowContext context) {
		context.success();
	}

	protected String getUsername(final AuthenticationFlowContext context, final SerializedBrokeredIdentityContext serializedCtx, final BrokeredIdentityContext brokerContext) {
		final RealmModel realm = context.getRealm();
		return realm.isRegistrationEmailAsUsername() ? brokerContext.getEmail() : brokerContext.getModelUsername();
	}

	@Override
	public boolean requiresUser() {
		return true;
	}

	@Override
	public boolean configuredFor(final KeycloakSession session, final RealmModel realm, final UserModel user) {
		return true;
	}

	@Override
	public void close() {

	}

	@Override
	public void setRequiredActions(final KeycloakSession session, final RealmModel realm, final UserModel user) {

	}
}
