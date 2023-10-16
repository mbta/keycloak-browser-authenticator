package cz.integsoft.keycloak.browser.authenticator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.infinispan.Cache;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import cz.integsoft.keycloak.browser.authenticator.model.cache.CachedCode;

/**
 * Factory for authenticator sending and checking the generated code sent by email.
 *
 * @author integsoft
 */
public class EmailCodeAuthenticatorFactory implements AuthenticatorFactory {

	public static final String PROVIDER_ID = "email-code-authenticator";
	private static final String CODES_CACHE_NAME = "codes";

	private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = { AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED };

	private Cache<String, CachedCode> secondFactorCodesCache;

	private static final Properties PROP = new Properties();

	@Override
	public Authenticator create(final KeycloakSession session) {
		try {
			PROP.load(this.getClass().getClassLoader().getResourceAsStream("META-INF/authenticator.properties"));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		final InfinispanConnectionProvider icp = session.getProvider(InfinispanConnectionProvider.class);
		secondFactorCodesCache = icp.getCache(CODES_CACHE_NAME);
		if (secondFactorCodesCache == null) {
			throw new RuntimeException("Can not found second factor cache");
		}
		return new EmailCodeAuthenticator(session, secondFactorCodesCache, PROP);
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
	public boolean isConfigurable() {
		return false;
	}

	@Override
	public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
		return REQUIREMENT_CHOICES;
	}

	@Override
	public boolean isUserSetupAllowed() {
		return false;
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return new ArrayList<ProviderConfigProperty>();
	}

	@Override
	public String getHelpText() {
		return "MBTA email OTP";
	}

	@Override
	public String getDisplayType() {
		return "MBTA email OTP";
	}

	@Override
	public String getReferenceCategory() {
		return "MBTA email OTP";
	}
}
