package cz.integsoft.keycloak.browser.authenticator.model;

/**
 * Email template model.
 *
 * @author integsoft
 */
public class EmailTemplate {

	private String subject;
	private String textBody;
	private String htmlBody;

	/**
	 * Constructor.
	 *
	 * @param subject email subject
	 * @param textBody email text body
	 * @param htmlBody email html body
	 */
	public EmailTemplate(final String subject, final String textBody, final String htmlBody) {
		this.subject = subject;
		this.textBody = textBody;
		this.htmlBody = htmlBody;
	}

	/**
	 * @return the subject
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * @param subject the subject to set
	 */
	public void setSubject(final String subject) {
		this.subject = subject;
	}

	/**
	 * @return the textBody
	 */
	public String getTextBody() {
		return textBody;
	}

	/**
	 * @param textBody the textBody to set
	 */
	public void setTextBody(final String textBody) {
		this.textBody = textBody;
	}

	/**
	 * @return the htmlBody
	 */
	public String getHtmlBody() {
		return htmlBody;
	}

	/**
	 * @param htmlBody the htmlBody to set
	 */
	public void setHtmlBody(final String htmlBody) {
		this.htmlBody = htmlBody;
	}
}
