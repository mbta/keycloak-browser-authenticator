package cz.integsoft.keycloak.browser.authenticator.model;

import java.util.List;

import com.amazonaws.regions.Regions;

/**
 * Aws queue configuration model.
 *
 * @author integsoft
 */
public class QueueConfig {

	private List<String> queueAppNames;

	private Regions queueRegion;

	/**
	 * Constructor.
	 *
	 * @param queueAppNames app names
	 * @param queueRegion queue AWS region
	 */
	public QueueConfig(final List<String> queueAppNames, final Regions queueRegion) {
		super();
		this.queueAppNames = queueAppNames;
		this.queueRegion = queueRegion;
	}

	/**
	 * @return the queueAppNames
	 */
	public final List<String> getQueueAppNames() {
		return queueAppNames;
	}

	/**
	 * @param queueAppNames the queueAppNames to set
	 */
	public final void setQueueAppNames(final List<String> queueAppNames) {
		this.queueAppNames = queueAppNames;
	}

	/**
	 * @return the queueRegion
	 */
	public final Regions getQueueRegion() {
		return queueRegion;
	}

	/**
	 * @param queueRegion the queueRegion to set
	 */
	public final void setQueueRegion(final Regions queueRegion) {
		this.queueRegion = queueRegion;
	}
}
