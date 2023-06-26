package cz.integsoft.keycloak.browser.authenticator.model;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS update profile event model.
 *
 * @author integsoft
 */
public class ProfileUpdateEvent {

	private String mbtaUuid;

	private Map<String, String> updates;

	/**
	 * Constructor.
	 */
	public ProfileUpdateEvent() {
		super();
		this.updates = new HashMap<>();
	}

	/**
	 * Constructor.
	 *
	 * @param mbtaUuid uuid
	 * @param updates profile updates
	 */
	public ProfileUpdateEvent(final String mbtaUuid, final Map<String, String> updates) {
		super();
		this.mbtaUuid = mbtaUuid;
		this.updates = updates;
	}

	/**
	 * @return the mbtaUuid
	 */
	public final String getMbtaUuid() {
		return mbtaUuid;
	}

	/**
	 * @param mbtaUuid the mbtaUuid to set
	 */
	public final void setMbtaUuid(final String mbtaUuid) {
		this.mbtaUuid = mbtaUuid;
	}

	/**
	 * @return the updates
	 */
	public final Map<String, String> getUpdates() {
		return updates;
	}

	/**
	 * @param updates the updates to set
	 */
	public final void setUpdates(final Map<String, String> updates) {
		this.updates = updates;
	}

}
