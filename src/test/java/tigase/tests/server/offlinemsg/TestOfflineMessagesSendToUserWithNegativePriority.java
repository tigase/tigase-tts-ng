package tigase.tests.server.offlinemsg;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 21.06.2017.
 */
public class TestOfflineMessagesSendToUserWithNegativePriority
		extends AbstractTest {

	private Account userA;
	private Jaxmpp userAjaxmpp;
	private Account userB;
	private Jaxmpp userBjaxmpp;

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		userA = createAccount().setLogPrefix("userA").build();
		userB = createAccount().setLogPrefix("userB").build();

		userAjaxmpp = userA.createJaxmpp().setConnected(true).build();
		userBjaxmpp = userB.createJaxmpp().setConnected(true).build();
	}

	@Test
	public void testDelivery1() throws JaxmppException, InterruptedException {
		userAjaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, -10);
		Thread.sleep(2000);

		final Mutex mutex = new Mutex();

		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("received:message:" + stanza.getBody());
				} catch (Exception ex) {
					assertNotNull(ex);
				}
			}
		};

		userAjaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		final String body = "Message - " + UUID.randomUUID().toString();
		final Message msg1 = Message.create();
		msg1.setTo(JID.jidInstance(userA.getJid()));
		msg1.setBody(body);
		msg1.setType(StanzaType.chat);
		msg1.setId(nextRnd());
		userBjaxmpp.send(msg1);

		Thread.sleep(10000);

		assertFalse(mutex.isItemNotified("received:message:" + body));

		userAjaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, 10);

		mutex.waitFor(20 * 1000, "received:message:" + body);
		assertTrue(mutex.isItemNotified("received:message:" + body));

	}

	@Test
	public void testDelivery2() throws JaxmppException, InterruptedException {
		userAjaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, -10);
		Thread.sleep(2000);

		final Mutex mutex = new Mutex();

		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("received:message:" + stanza.getBody());
				} catch (Exception ex) {
					assertNotNull(ex);
				}
			}
		};

		userAjaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		final String body = "Message - " + UUID.randomUUID().toString();
		final Message msg1 = Message.create();
		msg1.setTo(JID.jidInstance(userA.getJid()));
		msg1.setBody(body);
		msg1.setType(StanzaType.chat);
		msg1.setId(nextRnd());
		userBjaxmpp.send(msg1);

		Thread.sleep(10000);

		assertFalse(mutex.isItemNotified("received:message:" + body));

		userAjaxmpp.disconnect(true);

		userAjaxmpp.login(true);

		mutex.waitFor(20 * 1000, "received:message:" + body);
		assertTrue(mutex.isItemNotified("received:message:" + body));

	}

}
