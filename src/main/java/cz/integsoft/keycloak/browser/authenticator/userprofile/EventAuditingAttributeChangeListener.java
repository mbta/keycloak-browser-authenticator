package cz.integsoft.keycloak.browser.authenticator.userprofile;

import java.util.List;
import java.util.Map;

import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.UserModel;
import org.keycloak.userprofile.AttributeChangeListener;
import org.keycloak.userprofile.UserProfile;

/**
 * {@link AttributeChangeListener} to audit user profile attribute changes into {@link Event}. Adds info about user profile attribute change into {@link Event}'s detail field.
 *
 * @author Vlastimil Elias <velias@redhat.com>
 * @author Integsoft
 * @see UserProfile#update(AttributeChangeListener...)
 */
public class EventAuditingAttributeChangeListener extends org.keycloak.userprofile.EventAuditingAttributeChangeListener {

	private final EventBuilder event;
	private final Map<String, String> updatedUserData;

	/**
	 * @param profile used to read attribute configuration from
	 * @param event to add detail info into
	 * @param updatedUserData updated user data
	 */
	public EventAuditingAttributeChangeListener(final UserProfile profile, final EventBuilder event, final Map<String, String> updatedUserData) {
		super(profile, event);
		this.event = event;
		this.updatedUserData = updatedUserData;
	}

	@Override
	public void onChange(final String attributeName, final UserModel userModel, final List<String> oldValue) {
		if (attributeName.equals(UserModel.FIRST_NAME)) {
			event.detail(Details.PREVIOUS_FIRST_NAME, oldValue).detail(Details.UPDATED_FIRST_NAME, userModel.getFirstName());
			updatedUserData.put(attributeName, userModel.getFirstName());
		} else if (attributeName.equals(UserModel.LAST_NAME)) {
			event.detail(Details.PREVIOUS_LAST_NAME, oldValue).detail(Details.UPDATED_LAST_NAME, userModel.getLastName());
			updatedUserData.put(attributeName, userModel.getLastName());
		} else if (attributeName.equals(UserModel.EMAIL)) {
			event.detail(Details.PREVIOUS_EMAIL, oldValue).detail(Details.UPDATED_EMAIL, userModel.getEmail());
			updatedUserData.put(attributeName, userModel.getEmail());
		} else {
			event.detail(Details.PREF_PREVIOUS + attributeName, oldValue).detail(Details.PREF_UPDATED + attributeName, userModel.getAttributeStream(attributeName));
		}
	}

	/**
	 * @return the updatedUserData
	 */
	public final Map<String, String> getUpdatedUserData() {
		return updatedUserData;
	}
}
