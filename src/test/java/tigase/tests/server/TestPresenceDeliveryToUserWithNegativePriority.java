package tigase.tests.server;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 21.06.2017.
 */
public class TestPresenceDeliveryToUserWithNegativePriority extends AbstractTest {

	private Account userA;
	private Account userB;

	private Jaxmpp userAjaxmpp;
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

		PresenceModule.SubscribeRequestHandler handler = new PresenceModule.SubscribeRequestHandler() {
			@Override
			public void onSubscribeRequest(SessionObject sessionObject, Presence stanza, BareJID jid) {
				try {
					mutex.notify("received:presence:" + stanza.getId());
				} catch (Exception ex) {
					assertNull(ex);
				}
			}
		};

		userAjaxmpp.getEventBus().addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, handler);

		Presence p = Presence.createPresence();
		p.setTo(JID.jidInstance(userA.getJid()));
		p.setType(StanzaType.subscribe);
		String id = nextRnd();
		p.setId(id);
		userBjaxmpp.send(p);
		
		mutex.waitFor(20 * 1000, "received:presence:" + id);
		assertTrue(mutex.isItemNotified("received:presence:" + id));

		mutex.clear();

		assertFalse(mutex.isItemNotified("received:presence:" + id));

		userAjaxmpp.disconnect(true);

		userAjaxmpp.login(true);

		mutex.waitFor(20 * 1000, "received:presence:" + id);
		assertFalse(mutex.isItemNotified("received:presence:" + id));
	}
	
}
