/*
 * TestPasswordReset.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

package tigase.tests.http;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.icegreen.greenmail.util.DummySSLSocketFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.httpfileupload.HttpFileUploadModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.utils.Account;

import javax.mail.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Security;
import java.util.Properties;
import java.util.UUID;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class TestPasswordReset
		extends AbstractTest {

	private Account user;
	private Jaxmpp userJaxmpp;
	private EmailAccount userEmail;

	@BeforeMethod
	public void setup() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("XX").build();
		userJaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new HttpFileUploadModule());
			return jaxmpp;
		}).setConnected(true).build();
		userEmail = getEmailAccountForUser(user.getJid().getLocalpart());
	}

	@Test
	public void testPasswordReset() throws Exception {
		userJaxmpp.disconnect(true);

		WebClient webClient = new WebClient();
		webClient.addRequestHeader("Accept", "text/html");

		Page p1 = webClient.getPage(getURL());
		HtmlPage p = (HtmlPage) p1;
		HtmlForm form = p.getForms().get(0);
		form.getInputByName("jid").setValueAttribute(user.getJid().toString());
		form.getInputByName("email").setValueAttribute(userEmail.email);

		HtmlButton button = (HtmlButton) form.getElementsByTagName("button").get(0);
		p = button.click();

		assertTrue(p.getForms().isEmpty());

		URL url = read(240 * 1000);
		assertNotNull("Email with link to password change not received!", url);

		p = webClient.getPage(url);
		form = p.getForms().get(0);

		String newPassword = UUID.randomUUID().toString();
		form.getInputByName("password-entry").setValueAttribute(newPassword);
		form.getInputByName("password-reentry").setValueAttribute(newPassword);

		p = ((HtmlButton) form.getElementsByTagName("button").get(0)).click();

		if (!p.getForms().isEmpty()) {
			TestLogger.log(p.asText());
		}
		assertTrue(p.getForms().isEmpty());

		userJaxmpp.getConnectionConfiguration().setUserPassword(newPassword);
		userJaxmpp.login(true);

		removeUserAccount(userJaxmpp);
	}

	private URL read(final long timeout) throws Exception {
		Properties props = new Properties();
		Security.setProperty("ssl.SocketFactory.provider", DummySSLSocketFactory.class.getName());

		props.put("mail.imaps.ssl.checkserveridentity", "false");
		props.put("mail.imaps.ssl.trust", "*");
		try {

			Session session = Session.getDefaultInstance(props, null);

			Store store = session.getStore("imaps");
			store.connect(userEmail.server, userEmail.imapsServerPort, userEmail.username,
						  userEmail.password);

			Folder inbox = store.getFolder("inbox");
			inbox.open(Folder.READ_WRITE);

			final int startMessageCount = inbox.getMessageCount();
			if (startMessageCount > 0) {
				Message msg = inbox.getMessage(startMessageCount);
				URL url = getURLFromMessage(msg);
				if (url != null) {
					msg.setFlag(Flags.Flag.DELETED, true);
					inbox.close(true);
					store.close();
					return url;
				}
			}

			final long startTime = System.currentTimeMillis();

			boolean result = false;
			int lastCheckedMessagesCount = startMessageCount;
			while (true) {
				int messageCount = inbox.getMessageCount();
				if (messageCount != lastCheckedMessagesCount) {
					lastCheckedMessagesCount = messageCount;
					Message msg = inbox.getMessage(messageCount);
					URL url = getURLFromMessage(msg);
					if (url != null) {
						msg.setFlag(Flags.Flag.DELETED, true);
						inbox.close(true);
						store.close();
						return url;
					}
				}
				Thread.sleep(1000);

				final long now = System.currentTimeMillis();
				if (startTime + timeout < now) {
					TestLogger.log("No expected mail! We can't wait longer!");
					break;
				}
			}

			inbox.close(true);
			store.close();
		} catch (Exception e) {
			TestLogger.log(e.getMessage());
		}
		return null;
	}

	private URL getURLFromMessage(Message msg) throws MessagingException, IOException {
		if (msg.getSubject().equals("Password reset") && msg.isMimeType("text/plain")) {
			String content = msg.getContent().toString();
			if (content.contains(user.getJid().toString())) {
				int start = content.indexOf("http:");
				if (start > 0) {
					int end = content.indexOf(' ', start);
					if (end < 0) {
						end = content.indexOf('\n', start);
					}
					if (end < 0) {
						end = content.length();
					}
					return new URL(content.substring(start, end).trim());
				}
			}
		}
		return null;
	}

	private URL getURL() throws MalformedURLException {
		return new URL("http://" + getDomain(0) + ":" + getHttpPort() + "/rest/user/resetPassword");
	}
}
