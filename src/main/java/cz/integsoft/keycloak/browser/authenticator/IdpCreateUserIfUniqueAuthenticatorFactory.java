package cz.integsoft.keycloak.browser.authenticator;

import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;

/**
 * IDP create user authenticator factory.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 * @author Integsoft
 */
public class IdpCreateUserIfUniqueAuthenticatorFactory extends org.keycloak.authentication.authenticators.broker.IdpCreateUserIfUniqueAuthenticatorFactory {

	public static final String PROVIDER_ID = "mbta-idp-create-user-if-unique";
	static IdpCreateUserIfUniqueAuthenticator SINGLETON = new IdpCreateUserIfUniqueAuthenticator();

	@Override
	public Authenticator create(final KeycloakSession session) {
		return SINGLETON;
	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}

	@Override
	public String getDisplayType() {
		return "MBTA Create User If Unique";
	}
}
