package cz.integsoft.keycloak.browser.authenticator;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory for authenticator sending and checking the generated code sent by email.
 *
 * @author integsoft
 */
public class EmailCodeAuthenticatorFactory implements AuthenticatorFactory {

	public static final String PROVIDER_ID = "email-code-authenticator";
	public static final String CODE = "emailCode";
	public static final String CODE_LENGTH = "length";
	public static final int DEFAULT_LENGTH = 4;
	public static final String LOGIN_COUNT = "loginCount";
	public static final int DEFAULT_LOGIN_COUNT = 1;
	public static final String CODE_TTL = "ttl";
	public static final int DEFAULT_TTL = 300;

	private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = { AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED };

	private static final Properties PROP = new Properties();

	@Override
	public Authenticator create(final KeycloakSession session) {
		try {
			PROP.load(this.getClass().getClassLoader().getResourceAsStream("META-INF/authenticator.properties"));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		return new EmailCodeAuthenticator(session, PROP);
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
		return true;
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
		return List.of(new ProviderConfigProperty(CODE_LENGTH, "Code length", "The number of digits of the generated code.", ProviderConfigProperty.STRING_TYPE, String.valueOf(DEFAULT_LENGTH)),
				new ProviderConfigProperty(LOGIN_COUNT, "Login count for OTP check", "The number of logins at which the OTP check is triggered.", ProviderConfigProperty.STRING_TYPE, String.valueOf(DEFAULT_LOGIN_COUNT)),
				new ProviderConfigProperty(CODE_TTL, "Time to live", "The time to live in seconds for the code to be valid.", ProviderConfigProperty.STRING_TYPE, String.valueOf(DEFAULT_TTL)));
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
