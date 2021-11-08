package cz.integsoft.keycloak.browser.authenticator.model.cache;

import java.io.Serializable;

/**
 * Model vygenerovaneho kodu ulozeneho v cache.
 *
 * @author tomas.kozany
 */
public class CachedCode implements Serializable {

	private static final long serialVersionUID = 8190346389930470881L;

	private int code;

	private int accessCount;

	/**
	 * Konstruktor.
	 */
	public CachedCode() {
		super();
	}

	/**
	 * Konstruktor.
	 *
	 * @param code kod
	 */
	public CachedCode(final int code) {
		super();
		this.code = code;
		this.accessCount = 0;
	}

	/**
	 * Zvysi hodnotu pristupu ke kodu.
	 *
	 * @return aktualni vyse prstupu ke kodu
	 */
	public int increaseAccessCount() {
		this.accessCount++;
		return this.accessCount;
	}

	/**
	 * @return the code
	 */
	public int getCode() {
		return code;
	}

	/**
	 * @param code the code to set
	 */
	public void setCode(final int code) {
		this.code = code;
	}

	/**
	 * @return the accessCount
	 */
	public int getAccessCount() {
		return accessCount;
	}
}
