package cz.integsoft.keycloak.browser.authenticator;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory for username password authenticator.
 *
 * @author integsoft
 */
public class UsernamePasswordFormFactory implements AuthenticatorFactory {

	public static final String PROVIDER_ID = "mbta-auth-username-password-form";
	public static final UsernamePasswordForm SINGLETON = new UsernamePasswordForm();

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
		return PasswordCredentialModel.TYPE;
	}

	@Override
	public boolean isConfigurable() {
		return false;
	}

	public static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = { AuthenticationExecutionModel.Requirement.REQUIRED };

	@Override
	public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
		return REQUIREMENT_CHOICES;
	}

	@Override
	public String getDisplayType() {
		return "MBTA Username Password Form";
	}

	@Override
	public String getHelpText() {
		return "Validates a username and password from login form.";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return null;
	}

	@Override
	public boolean isUserSetupAllowed() {
		return false;
	}

}
