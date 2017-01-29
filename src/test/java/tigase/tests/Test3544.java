/*
 * Test3544.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
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
 *
 */
package tigase.tests;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule.RetrieveItemsAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterStore;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class Test3544 extends AbstractTest {
	
	BareJID user1JID;
	BareJID user2JID;
	Jaxmpp jaxmpp1;
	Jaxmpp jaxmpp2;
	
	@BeforeMethod
	@Override
	public void setUp() throws Exception {
		super.setUp();		
		user1JID = createUserAccount("user1");
		user2JID = createUserAccount("user2");
		jaxmpp1 = createJaxmpp("user1", user1JID);
		jaxmpp2 = createJaxmpp("user2", user2JID);
	}
	
	@AfterMethod
	public void cleanUp() throws Exception {
		if (jaxmpp1 != null) {
			removeUserAccount(jaxmpp1);
		}
		if (jaxmpp2 != null) {
			removeUserAccount(jaxmpp2);
		}
	}	
	
	@Test(groups = { "XMPP - PubSub" }, description = "Removal of PEP nodes on user removal")
	public void testRemovalOfPepServiceNodesOnUserRemoval() throws Exception {
		final Mutex mutex = new Mutex();
		
		jaxmpp1.login(true);
		jaxmpp2.login(true);

		final RosterStore roster1 = jaxmpp1.getModule(RosterModule.class).getRosterStore();
		jaxmpp1.getEventBus().addHandler(RosterModule.ItemAddedHandler.ItemAddedEvent.class,
				new RosterModule.ItemAddedHandler() {

			@Override
			public void onItemAdded(SessionObject sessionObject, RosterItem item, Set<String> modifiedGroups) {
				mutex.notify("added:" + item.getJid());
			}
		});
		roster1.add(user2JID, "User2", null);
		mutex.waitFor(1000 * 10, "added:" + user2JID);
		
		jaxmpp1.getEventBus().addHandler(RosterModule.ItemUpdatedHandler.ItemUpdatedEvent.class,
				new RosterModule.ItemUpdatedHandler() {

			@Override
			public void onItemUpdated(SessionObject sessionObject, RosterItem item, RosterModule.Action action,
					Set<String> modifiedGroups) {

				if (action != null)
					mutex.notify(item.getJid() + ":" + action);
				mutex.notify(item.getJid() + ":" + item.isAsk());
				mutex.notify(item.getJid() + ":" + item.getSubscription());
			}
		});
		PresenceModule.SubscribeRequestHandler subscriptionHandler1 = new PresenceModule.SubscribeRequestHandler() {

			@Override
			public void onSubscribeRequest(SessionObject sessionObject, Presence stanza, BareJID jid) {
				try {
					if (stanza.getType() == StanzaType.subscribe) {
						jaxmpp2.getModule(PresenceModule.class).subscribed(JID.jidInstance(jid));
						jaxmpp2.getModule(PresenceModule.class).subscribe(JID.jidInstance(jid));
					}
				} catch (Exception e) {
					e.printStackTrace();
					fail(e);
				}
			}
		};
		jaxmpp2.getEventBus().addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, subscriptionHandler1);
		PresenceModule.SubscribeRequestHandler subscriptionHandler2 = new PresenceModule.SubscribeRequestHandler() {

			@Override
			public void onSubscribeRequest(SessionObject sessionObject, Presence stanza, BareJID jid) {
				try {
					if (stanza.getType() == StanzaType.subscribe)
						jaxmpp1.getModule(PresenceModule.class).subscribed(JID.jidInstance(jid));
				} catch (Exception e) {
					e.printStackTrace();
					fail(e);
				}
			}
		};		
		jaxmpp1.getEventBus().addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, subscriptionHandler2);
		jaxmpp1.getModule(PresenceModule.class).subscribe(JID.jidInstance(user2JID));
		
		mutex.waitFor(10 * 1000, user2JID + ":both");
		assertTrue(mutex.isItemNotified(user2JID + ":both"));
		
		Element payload = ElementFactory.create("geoloc", null, "http://jabber.org/protocol/geoloc");
		Element country = ElementFactory.create("country", "US", null);
		payload.addChild(country);
		jaxmpp1.getModule(PubSubModule.class).publishItem(user1JID, "http://jabber.org/protocol/geoloc", "test1", payload, new PubSubModule.PublishAsyncCallback() {

			@Override
			public void onPublish(String itemId) {
				mutex.notify("published:geoloc");
			}

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
			}
		});
	
		mutex.waitFor(10 * 1000, "published:geoloc");
		assertTrue(mutex.isItemNotified("published:geoloc"));		
		
		jaxmpp2.getModule(PubSubModule.class).retrieveItem(user1JID, "http://jabber.org/protocol/geoloc", new PubSubModule.RetrieveItemsAsyncCallback() {

			@Override
			protected void onRetrieve(IQ responseStanza, String nodeName, Collection<RetrieveItemsAsyncCallback.Item> items) {
				mutex.notify("retrieved:" + nodeName + ":" + items.size());
			}

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
			}
		});
		mutex.waitFor(10 * 1000, "retrieved:http://jabber.org/protocol/geoloc:1");
		assertTrue(mutex.isItemNotified("retrieved:http://jabber.org/protocol/geoloc:1"));		
		
		jaxmpp2.getModule(DiscoveryModule.class).getItems(JID.jidInstance(user1JID), new DiscoveryModule.DiscoItemsAsyncCallback() {

			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				for (DiscoveryModule.Item item : items) {
					mutex.notify("discovered:1:item:" + item.getNode());
				}
				mutex.notify("discovered:1:items:"+items.size());
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("discovered:1:error:" + error);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
			}
		});
		mutex.waitFor(10 * 1000, "discovered:1:items:1", "discovered:1:item:http://jabber.org/protocol/geoloc");
		if (mutex.isItemNotified("discovered:1:error:" + XMPPException.ErrorCondition.resource_constraint)) {
			jaxmpp2.getModule(DiscoveryModule.class).getItems(JID.jidInstance(user1JID), new DiscoveryModule.DiscoItemsAsyncCallback() {

				@Override
				public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
					for (DiscoveryModule.Item item : items) {
						mutex.notify("discovered:1.2:item:" + item.getNode());
					}
					mutex.notify("discovered:1.2:items:"+items.size());
				}

				@Override
				public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
					mutex.notify("discovered:1.2:error:" + error);
				}

				@Override
				public void onTimeout() throws JaxmppException {
					throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
				}
			});
			mutex.waitFor(10 * 1000, "discovered:1.2:items:1", "discovered:1.2:item:http://jabber.org/protocol/geoloc");
			assertTrue(mutex.isItemNotified("discovered:1.2:items:1"));
			assertTrue(mutex.isItemNotified("discovered:1.2:item:http://jabber.org/protocol/geoloc"));
		} else {
			assertTrue(mutex.isItemNotified("discovered:1:items:1"));
			assertTrue(mutex.isItemNotified("discovered:1:item:http://jabber.org/protocol/geoloc"));
		}
		
		removeUserAccount(jaxmpp1);
		jaxmpp1 = null;
				
		Thread.sleep(2000);

		jaxmpp2.getModule(DiscoveryModule.class).getItems(JID.jidInstance(user1JID), new DiscoveryModule.DiscoItemsAsyncCallback() {

			@Override
			public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items) throws XMLException {
				for (DiscoveryModule.Item item : items) {
					mutex.notify("discovered:2:item:" + item.getNode());
				}
				mutex.notify("discovered:2:items:"+items.size());
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("discovered:2:error:" + error);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
			}
		});
		mutex.waitFor(10 * 1000, "discovered:2:items:0");
		assertTrue(mutex.isItemNotified("discovered:2:items:0") || mutex.isItemNotified("discovered:2:error:" +
																								XMPPException.ErrorCondition.resource_constraint));
		assertFalse(mutex.isItemNotified("discovered:2:item:http://jabber.org/protocol/geoloc"));
	}
}
