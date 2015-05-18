package tigase.tests;

import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;

import org.testng.Assert;
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

public class Test2951 extends AbstractTest {

	private void disableTaskConfig(JID monitorTaskJID, AdHocCommansModule adHoc) throws JaxmppException, InterruptedException {
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

	private boolean read(final String expected, final long timeout) throws Exception {
		Properties props = new Properties();

		props.put("mail.imaps.ssl.checkserveridentity", "false");
		props.put("mail.imaps.ssl.trust", "*");
		try {

			Session session = Session.getDefaultInstance(props, null);

			Store store = session.getStore("imaps");
			store.connect(this.props.getProperty("imap.server"), this.props.getProperty("imap.username"),
					this.props.getProperty("imap.password"));

			Folder inbox = store.getFolder("inbox");
			inbox.open(Folder.READ_ONLY);

			final int startMessageCount = inbox.getMessageCount();
			if (startMessageCount > 0) {
				Message msg = inbox.getMessage(startMessageCount);
				TestLogger.log("Last message subject: " + msg.getSubject());
				if (msg.getSubject().contains(expected)) {
					TestLogger.log("Found expected pattern!");
					return true;
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
					TestLogger.log("New message subject: " + msg.getSubject());
					if (msg.getSubject().contains(expected)) {
						TestLogger.log("Found expected pattern!");
						result = true;
						break;
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
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	@Test(groups = { "Monitor" }, description = "Tigase XMPP server monitoring")
	public void testTigaseXMPPServerMonitoring() throws Exception {
		final Mutex mutex = new Mutex();
		Jaxmpp jaxmpp = createJaxmppAdmin();
		jaxmpp.login(true);

		AdHocCommansModule adHoc = jaxmpp.getModule(AdHocCommansModule.class);
		final String msgUID = "test-" + nextRnd();

		final JID monitorTaskJID = JID.jidInstance("monitor",
				ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()).getDomain(), "sample-task");

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

		mutex.waitFor(30 * 1000, "enableTaskConfig");
		Assert.assertTrue(mutex.isItemNotified("enableTaskConfig:success"), "SampleTask is not enabled!");

		boolean foundPattern = read(msgUID, 1000 * 120);
		Assert.assertTrue(foundPattern, "Mail with notification not received");

		disableTaskConfig(monitorTaskJID, adHoc);
		jaxmpp.disconnect(true);
	}

}
