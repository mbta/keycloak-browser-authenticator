package cz.integsoft.keycloak.browser.authenticator;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Post login IDP authenticator factory.
 *
 * @author integsoft
 */
public class IdpPostLoginAuthenticatorFactory implements AuthenticatorFactory {

	public static final String PROVIDER_ID = "mbta-idp-post-login";
	static IdpPostLoginAuthenticator SINGLETON = new IdpPostLoginAuthenticator();

	private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

	@Override
	public Authenticator create(final KeycloakSession session) {
		return SINGLETON;
	}

	@Override
	public void init(final Config.Scope config) {

	}

	@Override
	public void postInit(final KeycloakSessionFactory factory) {

	}

	@Override
	public void close() {

	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}

	@Override
	public String getReferenceCategory() {
		return "idpPostLogin";
	}

	@Override
	public boolean isConfigurable() {
		return false;
	}

	@Override
	public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
		return REQUIREMENT_CHOICES;
	}

	@Override
	public String getDisplayType() {
		return "MBTA create phone_comp";
	}

	@Override
	public String getHelpText() {
		return "Combine phone area code and number into a single attribute.";
	}

	@Override
	public boolean isUserSetupAllowed() {
		return false;
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return configProperties;
	}
}
