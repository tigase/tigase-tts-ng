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
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Created by andrzej on 29.06.2017.
 */
public class TestMotD extends AbstractTest {

	private static final String EDIT_MOTD = "http://jabber.org/protocol/admin#edit-motd";
	private static final String DELETE_MOTD = "http://jabber.org/protocol/admin#delete-motd";

	private Jaxmpp adminJaxmpp;
	private JID sessManJid;
	private Jaxmpp userJaxmpp;

	private String body;
	private boolean configured = false;

	@BeforeClass
	public void setUp() throws JaxmppException, InterruptedException {
		adminJaxmpp = getAdminAccount().createJaxmpp().setConnected(true).build();
		userJaxmpp = createAccount().build().createJaxmpp().build();
		sessManJid = JID.jidInstance("sess-man", getDomain());
		body = "Wecome to " + UUID.randomUUID() + "\nThis message was set at: " + new Date();
	}

	@Test
	public void testEditMotD() throws JaxmppException, InterruptedException {
		AtomicReference<JabberDataElement> form = new AtomicReference<>();
		Mutex mutex = new Mutex();

		adminJaxmpp.getModule(AdHocCommansModule.class).execute(sessManJid, EDIT_MOTD, null, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				form.set(data);
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":form:success");
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":form");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":form:error:" + error);
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":form");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":form:timeout");
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":form");
			}
		});

		mutex.waitFor(20 * 1000, "adhoc:response:" + EDIT_MOTD + ":form");
		assertTrue(mutex.isItemNotified("adhoc:response:" + EDIT_MOTD + ":form:success"));

		FixedField field = form.get().getField("Error");
		if (field != null) {
			assertEquals("MotDProcessor is disabled", field.getFieldValue());
			return;
		}

		configured = true;

		form.set(new JabberDataElement(ElementFactory.create(form.get())));
		((TextMultiField) form.get().getField("motd")).setFieldValue(body.split("\n"));

		adminJaxmpp.getModule(AdHocCommansModule.class).execute(sessManJid, EDIT_MOTD, Action.execute, form.get(), new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				if (data != null && data.getField("Note") != null && ((FixedField)data.getField("Note")).getFieldValue().contains("error")) {
					mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit:error:exception");
				} else {
					mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit:success");
				}
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit:error:" + error);
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit:timeout");
				mutex.notify("adhoc:response:" + EDIT_MOTD + ":submit");
			}
		});

		mutex.waitFor(20 * 1000, "adhoc:response:" + EDIT_MOTD + ":submit");
		assertTrue(mutex.isItemNotified("adhoc:response:" + EDIT_MOTD + ":submit:success"));
	}

	@Test(dependsOnMethods = {"testEditMotD"})
	public void testMotDDelivery() throws JaxmppException, InterruptedException {
		if (!configured) {
			return;
		}
		
		Mutex mutex = new Mutex();
		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("message:motd:1:" + stanza.getBody());
				} catch (Exception ex) {
					assertNull(ex);
				}
			}
		};
		userJaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		userJaxmpp.login(true);

		mutex.waitFor(20 * 1000, "message:motd:1:" + body);
		userJaxmpp.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
		assertTrue(mutex.isItemNotified("message:motd:1:" + body));

		userJaxmpp.getModule(StreamManagementModule.class).sendAck();
		userJaxmpp.disconnect(true);

		handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("message:motd:2:" + stanza.getBody());
				} catch (Exception ex) {
					assertNull(ex);
				}
			}
		};
		userJaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		userJaxmpp.login(true);

		mutex.waitFor(20 * 1000, "message:motd:2:" + body);
		userJaxmpp.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
		assertFalse(mutex.isItemNotified("message:motd:2:" + body));
	}

	@Test(dependsOnMethods = {"testMotDDelivery"})
	public void testDeleteMotD() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();

		adminJaxmpp.getModule(AdHocCommansModule.class).execute(sessManJid, DELETE_MOTD, Action.execute, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				if (data != null && data.getField("Note") != null && ((FixedField)data.getField("Note")).getFieldValue().contains("error")) {
					mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit:exception");
				} else {
					mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit:success");
				}
				mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit:error:" + error);
				mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit:timeout");
				mutex.notify("adhoc:response:" + DELETE_MOTD + ":submit");
			}
		});

		mutex.waitFor(20 * 1000, "adhoc:response:" + DELETE_MOTD + ":submit");
		assertTrue(mutex.isItemNotified("adhoc:response:" + DELETE_MOTD + ":submit:success"));
	}

}
