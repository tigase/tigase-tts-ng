package tigase.tests.server;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.forms.FixedField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.TextMultiField;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.streammng.StreamManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Created by andrzej on 29.06.2017.
 */
public class TestWelcomeMessage extends AbstractTest {

	private static final String DELETE_MESSAGE = "http://jabber.org/protocol/admin#delete-welcome";
	private static final String SET_MESSAGE = "http://jabber.org/protocol/admin#set-welcome";

	private Jaxmpp adminJaxmpp;
	private JID sessManJid;
	private Jaxmpp userJaxmpp;

	private String body;
	private boolean configured = false;

	@BeforeClass
	public void setUp() throws JaxmppException, InterruptedException {
		adminJaxmpp = getAdminAccount().createJaxmpp().setConnected(true).build();
		sessManJid = JID.jidInstance("sess-man", getDomain());
		body = "Wecome to " + UUID.randomUUID() + "\nThis message was set at: " + new Date();
	}

	@Test
	public void testSetWelcomeMessage() throws JaxmppException, InterruptedException {
		AtomicReference<JabberDataElement> form = new AtomicReference<>();
		Mutex mutex = new Mutex();

		adminJaxmpp.getModule(AdHocCommansModule.class).execute(sessManJid, SET_MESSAGE, null, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				form.set(data);
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":form:success");
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":form");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":form:error:" + error);
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":form");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":form:timeout");
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":form");
			}
		});

		mutex.waitFor(20 * 1000, "adhoc:response:" + SET_MESSAGE + ":form");
		assertTrue(mutex.isItemNotified("adhoc:response:" + SET_MESSAGE + ":form:success"));

		FixedField field = form.get().getField("Error");
		if (field != null) {
			assertEquals("JabberIqRegister is disabled", field.getFieldValue());
			return;
		}

		configured = true;

		form.set(new JabberDataElement(ElementFactory.create(form.get())));
		((TextMultiField) form.get().getField("welcome")).setFieldValue(body.split("\n"));

		adminJaxmpp.getModule(AdHocCommansModule.class).execute(sessManJid, SET_MESSAGE, Action.execute, form.get(), new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				if (data != null && data.getField("Note") != null && ((FixedField)data.getField("Note")).getFieldValue().contains("error")) {
					mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit:error:exception");
				} else {
					mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit:success");
				}
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit:error:" + error);
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit:timeout");
				mutex.notify("adhoc:response:" + SET_MESSAGE + ":submit");
			}
		});

		mutex.waitFor(20 * 1000, "adhoc:response:" + SET_MESSAGE + ":submit");
		assertTrue(mutex.isItemNotified("adhoc:response:" + SET_MESSAGE + ":submit:success"));
	}

	@Test(dependsOnMethods = {"testSetWelcomeMessage"})
	public void testWelcomeMessageDelivery() throws JaxmppException, InterruptedException {
		if (!configured) {
			return;
		}

		final Mutex mutex = new Mutex();

		AtomicInteger counter = new AtomicInteger(1);
		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("welcome:message:" + counter.get() + ":" + stanza.getBody());
				} catch (Exception ex) {
					assertNull(ex);
				}
			}
		};

		userJaxmpp = createAccount().build().createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			return jaxmpp;
		}).setConnected(true).build();

		mutex.waitFor(20 * 1000,"welcome:message:" + counter.get() + ":" + body);
		assertTrue(mutex.isItemNotified("welcome:message:" + counter.get() + ":" + body));

		Thread.sleep(500);

		userJaxmpp.getModule(StreamManagementModule.class).sendAck();
		userJaxmpp.disconnect(true);

		counter.incrementAndGet();

		userJaxmpp.login(true);

		mutex.waitFor(20 * 1000,"welcome:message:" + counter.get() + ":" + body);
		assertFalse(mutex.isItemNotified("welcome:message:" + counter.get() + ":" + body));
	}

	@Test(dependsOnMethods = {"testWelcomeMessageDelivery"})
	public void testDeleteWelcomeMessage() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();

		adminJaxmpp.getModule(AdHocCommansModule.class).execute(sessManJid, DELETE_MESSAGE, Action.execute, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				if (data != null && data.getField("Note") != null && ((FixedField)data.getField("Note")).getFieldValue().contains("error")) {
					mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit:exception");
				} else {
					mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit:success");
				}
				mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit:error:" + error);
				mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit:timeout");
				mutex.notify("adhoc:response:" + DELETE_MESSAGE + ":submit");
			}
		});

		mutex.waitFor(20 * 1000, "adhoc:response:" + DELETE_MESSAGE + ":submit");
		assertTrue(mutex.isItemNotified("adhoc:response:" + DELETE_MESSAGE + ":submit:success"));
	}


}