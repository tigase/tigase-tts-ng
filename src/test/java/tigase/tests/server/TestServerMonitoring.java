/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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
package tigase.tests.server;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException.ErrorCondition;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class TestServerMonitoring
		extends AbstractTest {

	@Test(groups = {"Monitor"}, description = "Tigase XMPP server monitoring")
	public void testTigaseXMPPServerMonitoring() throws Exception {
		final Mutex mutex = new Mutex();
		final String msgUID = "test-" + nextRnd();

		purgeEmailMailboxes();

		Properties mailProps = new Properties();

		mailProps.put("mail.imaps.ssl.checkserveridentity", "false");
		mailProps.put("mail.imaps.ssl.trust", "*");

		Session session = Session.getDefaultInstance(mailProps, null);

		EmailAccount emailAccount = getGlobalServerReceiverAccount();
		Store store = session.getStore("imaps");
		store.connect(emailAccount.server, emailAccount.imapsServerPort, emailAccount.username,
		              emailAccount.password);

		Folder inbox = store.getFolder("inbox");
		inbox.open(Folder.READ_WRITE);

		inbox.addMessageCountListener(new MessageCountAdapter() {
			@Override
			public void messagesAdded(MessageCountEvent e) {
//				super.messagesAdded(e);

				Arrays.stream(e.getMessages()).filter(msg -> {
					try {
						TestLogger.log("Received message: " + msg.getSubject() + ", " + msg.getContent());
						return msg.getSubject().contains(msgUID) ||
								(msg.isMimeType("text/plain")) && msg.getContent().toString().contains(msgUID);
					} catch (MessagingException | IOException ex) {
						throw new RuntimeException(ex);
					}
				}).findAny().ifPresent(message -> {
					mutex.notify("mail:" + msgUID);
				});

			}
		});

		Jaxmpp jaxmpp = getJaxmppAdmin();

		AdHocCommansModule adHoc = jaxmpp.getModule(AdHocCommansModule.class);

		final JID monitorTaskJID = JID.jidInstance("monitor",
												   ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject())
														   .getDomain(), "sample-task");

		disableTaskConfig(monitorTaskJID, adHoc);

		TestLogger.log("Turning on task");
		final JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addBooleanField("x-task#enabled", Boolean.TRUE);
		form.addTextSingleField("message", msgUID);
		adHoc.execute(monitorTaskJID, "x-config", null, form, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
				TestLogger.log("Error! " + error);
				mutex.notify("enableTaskConfig", "enableTaskConfig:error");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("enableTaskConfig", "enableTaskConfig:success");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("enableTaskConfig", "enableTaskConfig:timeout");
			}
		});

		mutex.waitFor(60 * 1000, "enableTaskConfig");
		Assert.assertTrue(mutex.isItemNotified("enableTaskConfig:success"), "SampleTask is not enabled!");

		TimeUnit.SECONDS.sleep(5);

		TestLogger.log("Msg count: " + inbox.getMessageCount());

		mutex.waitFor(TimeUnit.SECONDS.toMillis(10), "mail:" + msgUID);
		Assert.assertTrue(mutex.isItemNotified("mail:" + msgUID), "Mail with notification not received");
		inbox.close(true);
		store.close();

		disableTaskConfig(monitorTaskJID, adHoc);
	}

	private void disableTaskConfig(JID monitorTaskJID, AdHocCommansModule adHoc)
			throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addBooleanField("x-task#enabled", Boolean.FALSE);
		adHoc.execute(monitorTaskJID, "x-config", null, form, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
				TestLogger.log("Error! " + error);
				mutex.notify("disableTaskConfig", "disableTaskConfig:error");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("disableTaskConfig", "disableTaskConfig:success");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disableTaskConfig", "disableTaskConfig:timeout");
			}
		});

		mutex.waitFor(30 * 10000, "disableTaskConfig");
		Assert.assertTrue(mutex.isItemNotified("disableTaskConfig:success"), "SampleTask can't be disabled!");

	}

//	private boolean read(final String expected, final long timeout) throws Exception {
//		TestLogger.log("Waiting for e-mail...: " + expected);
//		Properties props = new Properties();
//
//		props.put("mail.imaps.ssl.checkserveridentity", "false");
//		props.put("mail.imaps.ssl.trust", "*");
//		try {
//
//			Session session = Session.getDefaultInstance(props, null);
//
//			EmailAccount emailAccount = getGlobalServerReceiverAccount();
//			Store store = session.getStore("imaps");
//			store.connect(emailAccount.server, emailAccount.imapsServerPort, emailAccount.username,
//						  emailAccount.password);
//
//			Folder inbox = store.getFolder("inbox");
//			inbox.open(Folder.READ_WRITE);
//
//			TestLogger.log("Mail receiver connected (I hope so!), message count: " + inbox.getMessageCount());
//
//			final int startMessageCount = inbox.getMessageCount();
//			if (startMessageCount > 0) {
//				Message msg = inbox.getMessage(startMessageCount);
//				TestLogger.log("Last message subject: " + msg.getSubject());
//				TestLogger.log("Last message content: " + msg.getContent());
//				if (msg.getSubject().contains(expected) ||
//						(msg.isMimeType("text/plain")) && msg.getContent().toString().contains(expected)) {
//					TestLogger.log("Found expected pattern!");
//					msg.setFlag(Flags.Flag.DELETED, true);
//					return true;
//				} else {
//					TestLogger.log("Expected pattern + '" + expected + "' NOT found!");
//				}
//			}
//
//			final long startTime = System.currentTimeMillis();
//
//			boolean result = false;
//			int lastCheckedMessagesCount = startMessageCount;
//			while (true) {
//				int messageCount = inbox.getMessageCount();
//				if (messageCount != lastCheckedMessagesCount) {
//					lastCheckedMessagesCount = messageCount;
//					Message msg = inbox.getMessage(messageCount);
//					TestLogger.log("New message subject: " + msg.getSubject());
//					if (msg.getSubject().contains(expected)) {
//						TestLogger.log("Found expected pattern!");
//						result = true;
//						msg.setFlag(Flags.Flag.DELETED, true);
//						break;
//					} else {
//						TestLogger.log("Expected pattern + '" + expected + "' NOT found!");
//					}
//				}
//				TimeUnit.SECONDS.sleep(2);
//
//				final long now = System.currentTimeMillis();
//				if (startTime + timeout < now) {
//					TestLogger.log("No expected mail! We can't wait longer!");
//					break;
//				}
//			}
//
//			inbox.close(true);
//			store.close();
//			return result;
//		} catch (Exception e) {
//			TestLogger.log(e.getMessage());
//			return false;
//		}
//	}
}
