/*
 * TestPrivacyList.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2018 "Tigase, Inc." <office@tigase.com>
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

package tigase.tests.server;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.streammng.StreamManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.UUID;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;

public class TestPrivacyList extends AbstractJaxmppTest {

	private Jaxmpp user1jaxmpp1;
	private Jaxmpp user1jaxmpp2;
	private Jaxmpp user2jaxmpp;
	private Jaxmpp user3jaxmpp;
	private Account user1;
	private Account user2;
	private Account user3;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user1 = createAccount().setLogPrefix("privacyList_").build();
		user1jaxmpp1 = user1.createJaxmpp().setConfigurator(this::configureJaxmpp).setResource("conn-1").setConnected(true).build();
		user1jaxmpp2 = user1.createJaxmpp().setConfigurator(this::configureJaxmpp).setResource("conn-2").setConnected(false).build();
		user2 = createAccount().setLogPrefix("privacyList_").build();
		user2jaxmpp = user2.createJaxmpp().setConnected(true).build();
		user3 = createAccount().setLogPrefix("privacyList_").build();
		user3jaxmpp = user3.createJaxmpp().setConnected(true).build();
	}

	private Jaxmpp configureJaxmpp(Jaxmpp jaxmpp) {
		jaxmpp.getModulesManager().register(new StreamManagementModule(jaxmpp));
		return jaxmpp;
	}

	@Test
	public void testDefaultWithSubscription() throws Exception {
		Mutex mutex = new Mutex();
		createMutualSubscriptions(mutex, user1jaxmpp1, user2jaxmpp);

		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp1, "u2-u1j1-after-sub1-" + UUID.randomUUID().toString()));
		assertNotNull(sendAndWait(user3jaxmpp, user1jaxmpp1, "u3-u1j1-after-sub1-" + UUID.randomUUID().toString()));

		Element list = ElementFactory.create("list");
		list.setAttribute("name", "blocked");

		// block users without subscription
		Element item = createItem("subscription", "none", "deny", 100);
		item.addChild(ElementFactory.create("message"));
		list.addChild(item);

		list.addChild(createItem(null, null, "allow", 110));

		setPrivacyList(user1jaxmpp1, mutex, "privacy-list:set:1", list);
		setDefaultPrivacyList(user1jaxmpp1, mutex, "privacy-list:default:set:1", "blocked");

		user1jaxmpp1.disconnect(true);
		TestLogger.log("disconnection of user1jaxmpp1 finished!", true);

		user1jaxmpp1.getEventBus().addHandler(Connector.DisconnectedHandler.DisconnectedEvent.class, sessionObject -> {
			TestLogger.log("received disconnected event for user1jaxmpp1", true);
		});

		user1jaxmpp1 = user1.createJaxmpp().setConfigurator(this::configureJaxmpp).setResource("conn-1").setConnected(true).build();

		assertTrue(user1jaxmpp1.isConnected());

		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp1, "u2-u1j1-reconnect1-" + UUID.randomUUID().toString()));
		assertNull(sendAndWait(user3jaxmpp, user1jaxmpp1, "u3-u1j1-reconnect1-" + UUID.randomUUID().toString()));

		user1jaxmpp2.login(true);

		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp1, "u2-u1j1-connect_u1j2-" + UUID.randomUUID().toString()));
		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp2, "u2-u1j2-connect_u1j2-" + UUID.randomUUID().toString()));
		assertNull(sendAndWait(user3jaxmpp, user1jaxmpp1, "u3-u1j1-connect_u1j2-" + UUID.randomUUID().toString()));

		user1jaxmpp1.disconnect(true);

		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp2, "u2-u1j1-disconnect_u1j1-" + UUID.randomUUID().toString()));
		assertNull(sendAndWait(user3jaxmpp, user1jaxmpp2, "u2-u1j1-disconnect_u1j1-" + UUID.randomUUID().toString()));


		user1jaxmpp2.disconnect(true);
		user1jaxmpp1.login(true);
		user1jaxmpp1.getConnector().stop(true);

		user1jaxmpp1.login(true);
		Thread.sleep(500);
		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp1, "u2-u1j1-reconnect2_u1j1-" + UUID.randomUUID().toString()));
		assertNull(sendAndWait(user3jaxmpp, user1jaxmpp1, "u3-u1j1-reconnect2_u1j1-" + UUID.randomUUID().toString()));


		user1jaxmpp1.getConnector().stop(true);
		StreamManagementModule.reset(user1jaxmpp1.getSessionObject());
		user1jaxmpp1.getSessionObject().setProperty(ResourceBinderModule.BINDED_RESOURCE_JID, null);

		Thread.sleep(500);

		user1jaxmpp1.login(true);
		Thread.sleep(500);
		assertTrue(user1jaxmpp1.isConnected());
		assertNotNull(sendAndWait(user2jaxmpp, user1jaxmpp1, "u2-u1j1-stream_management_u1j1-" + UUID.randomUUID().toString()));
		assertNull(sendAndWait(user3jaxmpp, user1jaxmpp1, "u2-u1j1-stream_management_u1j1-" + UUID.randomUUID().toString()));
	}

	private void createMutualSubscriptions(Mutex mutex, Jaxmpp jaxmpp1, Jaxmpp jaxmpp2)
			throws JaxmppException, InterruptedException {
		System.out.println("creating mutual subscriptions between: " + jaxmpp1.getSessionObject().getUserBareJid() + " <-> " + jaxmpp2.getSessionObject().getUserBareJid());
		String id = UUID.randomUUID().toString();
		PresenceModule.SubscribeRequestHandler handler2 = (SessionObject sessionObject, Presence stanza, BareJID jid) -> {
			try {
				jaxmpp2.getModule(PresenceModule.class).subscribed(JID.jidInstance(jid));
				jaxmpp2.getModule(PresenceModule.class).subscribe(JID.jidInstance(jid));
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		};
		jaxmpp2.getModule(PresenceModule.class).addSubscribeRequestHandler(handler2);
		PresenceModule.SubscribeRequestHandler handler1 = (SessionObject sessionObject, Presence stanza, BareJID jid) -> {
			try {
				jaxmpp1.getModule(PresenceModule.class).subscribed(JID.jidInstance(jid));
				mutex.notify("subscription:" + id + ":success");
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		};
		jaxmpp1.getModule(PresenceModule.class).addSubscribeRequestHandler(handler1);
		jaxmpp1.getModule(PresenceModule.class).subscribe(JID.jidInstance(jaxmpp2.getSessionObject().getUserBareJid()));

		mutex.waitFor(20 * 1000, "subscription:" + id + ":success");
		assertTrue(mutex.isItemNotified("subscription:" + id + ":success"));

		jaxmpp1.getModule(PresenceModule.class).removeSubscribeRequestHandler(handler1);
		jaxmpp2.getModule(PresenceModule.class).removeSubscribeRequestHandler(handler2);
	}
	
	private void setDefaultPrivacyList(Jaxmpp jaxmpp, Mutex mutex, String prefix, String name)
			throws JaxmppException, InterruptedException {
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setId(UUID.randomUUID().toString());

		Element query = ElementFactory.create("query", null, "jabber:iq:privacy");
		iq.addChild(query);

		Element el = ElementFactory.create("default");
		if (name != null) {
			el.setAttribute("name", name);
		}
		query.addChild(el);

		jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify(prefix + ":error:" + error, prefix);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify(prefix + ":success", prefix);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(prefix + ":error:timeout", prefix);
			}
		});
		mutex.waitFor(20 * 1000, prefix);
		assertTrue(mutex.isItemNotified(prefix + ":success"));
	}

	private void setPrivacyList(Jaxmpp jaxmpp, Mutex mutex, String prefix, Element list)
			throws JaxmppException, InterruptedException {
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setId(UUID.randomUUID().toString());

		Element query = ElementFactory.create("query", null, "jabber:iq:privacy");
		iq.addChild(query);

		query.addChild(list);

		jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify(prefix + ":error:" + error, prefix);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify(prefix + ":success", prefix);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(prefix + ":error:timeout", prefix);
		}
		});
		mutex.waitFor(20 * 1000, prefix);
		assertTrue(mutex.isItemNotified(prefix + ":success"));
	}

	private Element createItem(String type, String value, String action, int order) throws XMLException {
		Element el = ElementFactory.create("item");
		if (type != null) {
			el.setAttribute("type", type);
		}
		if (value != null) {
			el.setAttribute("value", value);
		}
		el.setAttribute("action", action);
		el.setAttribute("order", String.valueOf(order));
		return el;
	}

}
