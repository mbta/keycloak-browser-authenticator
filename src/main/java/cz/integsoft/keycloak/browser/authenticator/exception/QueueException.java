package cz.integsoft.keycloak.browser.authenticator.exception;

/**
 * Exception in case of a problem with communication with the queue.
 *
 * @author integsoft
 */
public class QueueException extends Exception {

	private static final long serialVersionUID = -8661350107089994243L;

	/**
	 * Constructor.
	 */
	public QueueException() {
		super();
	}

	/**
	 * Constructor.
	 *
	 * @param message message
	 * @param cause cause
	 */
	public QueueException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor.
	 *
	 * @param message message
	 */
	public QueueException(final String message) {
		super(message);
	}

	/**
	 * Constructor.
	 *
	 * @param cause cause
	 */
	public QueueException(final Throwable cause) {
		super(cause);
	}
}
