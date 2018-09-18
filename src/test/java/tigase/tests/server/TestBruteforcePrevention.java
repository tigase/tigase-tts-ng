package tigase.tests.server;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.AbstractField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslMechanism;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.saslmechanisms.PlainMechanism;
import tigase.jaxmpp.core.client.xmpp.modules.auth.scram.ScramMechanism;
import tigase.jaxmpp.core.client.xmpp.modules.auth.scram.ScramSHA256Mechanism;
import tigase.jaxmpp.core.client.xmpp.modules.streammng.StreamManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.security.SecureRandom;
import java.util.Random;

import static org.testng.AssertJUnit.assertTrue;

public class TestBruteforcePrevention
		extends AbstractJaxmppTest {

	private void checkUserStatus(final BareJID jid, final boolean expectedEnabledStatus) throws Exception {
		final Mutex mutex = new Mutex();

		assert getJaxmppAdmin().isConnected();
		final AdHocCommansModule adHoc = getJaxmppAdmin().getModule(AdHocCommansModule.class);
		final String domain = ResourceBinderModule.getBindedJID(getJaxmppAdmin().getSessionObject()).getDomain();

		final JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addJidSingleField("accountjid", JID.jidInstance(jid));

		final JID sessManJID = JID.jidInstance("sess-man", domain);
		adHoc.execute(sessManJID, "modify-user", null, form, new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("user", "user:error");

			}

			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {

				AbstractField<Boolean> field = data.getField("Account enabled");
				Boolean accountEnabled = field.getFieldValue();

				mutex.notify("user", "user:ok:" + accountEnabled);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("user", "user:timeout");
			}
		});

		mutex.waitFor(30_000, "user");
		Assert.assertTrue(mutex.isItemNotified("user:ok:" + expectedEnabledStatus),
						  "User enable status is different " + "than " + expectedEnabledStatus);

	}

	private void makeInvalidLogin(Jaxmpp jaxmpp) throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();
		final EventListener eventListener = new EventListener() {
			@Override
			public void onEvent(Event<? extends EventHandler> event) {
				if (event instanceof AuthModule.AuthFailedHandler.AuthFailedEvent) {
					mutex.notify("event", "authFailed");
				} else if (event instanceof ResourceBinderModule.ResourceBindSuccessHandler.ResourceBindSuccessEvent ||
						event instanceof StreamManagementModule.StreamResumedHandler.StreamResumedEvent) {
					mutex.notify("event", "loggedIn");
				} else if (event instanceof Connector.DisconnectedHandler.DisconnectedEvent) {
					mutex.notify("event", "disconnected");
				}
			}
		};
		jaxmpp.getConnectionConfiguration().setUserPassword(" - - - - -");
		try {
			jaxmpp.getEventBus().addListener(eventListener);
			jaxmpp.login();
			mutex.waitFor(30_000, "event");
			assertTrue(mutex.isItemNotified("authFailed"));
		} finally {
			jaxmpp.getEventBus().remove(eventListener);
		}
	}

	@Test(description = "Test disabling user (21 invalid login)")
	public void testDisableUser() throws Exception {
		final Random random = SecureRandom.getInstanceStrong();

		Account user = createAccount().setLogPrefix("user").build();

		Jaxmpp jaxmppOk = user.createJaxmpp().setConnected(true).build();
		assertTrue(jaxmppOk.isConnected());
		jaxmppOk.disconnect(true);

		for (int i = 0; i < 21; i++) {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(false).build();
			makeInvalidLogin(jaxmpp);
		}

		try {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(true).build();
			AssertJUnit.fail("It should not happen!");
		} catch (Exception ignored) {
		}

		checkUserStatus(user.getJid(), false);
	}

	@Test(description = "Test softban (4 invalid login)")
	public void testOneInvalidLoginTooMuchDefaultSasl() throws Exception {
		Account user = createAccount().setLogPrefix("user").build();

		Jaxmpp jaxmppOk = user.createJaxmpp().setConnected(true).build();
		assertTrue(jaxmppOk.isConnected());
		jaxmppOk.disconnect(true);

		for (int i = 0; i < 4; i++) {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(false).build();
			makeInvalidLogin(jaxmpp);
		}

		try {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(true).build();
			AssertJUnit.fail("It should not happen!");
		} catch (Exception ignored) {
		}

		checkUserStatus(user.getJid(), true);
	}

	@Test(description = "Test softban (4 invalid login) with random SASL mechanisms")
	public void testOneInvalidLoginTooMuchRandomSasl() throws Exception {
		final SaslMechanism[] mechanisms = {new PlainMechanism(), new ScramSHA256Mechanism(), new ScramMechanism()};
		final Random random = SecureRandom.getInstanceStrong();

		Account user = createAccount().setLogPrefix("user").build();

		Jaxmpp jaxmppOk = user.createJaxmpp().setConnected(true).build();
		assertTrue(jaxmppOk.isConnected());
		jaxmppOk.disconnect(true);

		for (int i = 0; i < 4; i++) {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(false).build();
			SaslModule module = jaxmpp.getModule(SaslModule.class);
			module.removeAllMechanisms();
			module.addMechanism(mechanisms[random.nextInt(mechanisms.length)]);
			makeInvalidLogin(jaxmpp);
		}

		try {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(true).build();
			AssertJUnit.fail("It should not happen!");
		} catch (Exception ignored) {
		}

		checkUserStatus(user.getJid(), true);
	}

	@Test(description = "Test correct login after 3 invalid attempts")
	public void testThreeInvalidLogins() throws Exception {
		Account user = createAccount().setLogPrefix("user").build();

		Jaxmpp jaxmppOk = user.createJaxmpp().setConnected(true).build();
		assertTrue(jaxmppOk.isConnected());
		jaxmppOk.disconnect(true);

		for (int i = 0; i < 3; i++) {
			Jaxmpp jaxmpp = user.createJaxmpp().setConnected(false).build();
			makeInvalidLogin(jaxmpp);
		}

		jaxmppOk = user.createJaxmpp().setConnected(true).build();
		assertTrue(jaxmppOk.isConnected());
		jaxmppOk.disconnect(true);

		checkUserStatus(user.getJid(), true);
	}

}
