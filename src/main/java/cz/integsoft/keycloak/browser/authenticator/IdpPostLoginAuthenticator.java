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

	@Override
	public void authenticate(final AuthenticationFlowContext context) {
		final UserModel user = context.getUser();

		if (user == null) {
			logger.errorf("Post process IDP login failed, username not found in context");
			context.resetFlow();
			return;
		}

		if (user.getFirstAttribute("mbta_uuid") == null) {
			final String uuid = UUID.randomUUID().toString();
			user.setSingleAttribute("mbta_uuid", uuid);
			logger.infof("Added uuid %s to user %s", uuid, user.getUsername());
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
