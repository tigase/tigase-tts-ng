/*
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
package tigase.tests.pubsub;

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
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestRemovalOfPepNodeOnUserRemoval
		extends AbstractTest {

	Jaxmpp jaxmpp1;
	Jaxmpp jaxmpp2;
	Account user1;
	Account user2;

	@BeforeMethod
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix("user1").build();
		user2 = createAccount().setLogPrefix("user2").build();
		jaxmpp1 = user1.createJaxmpp().setConnected(true).build();
		jaxmpp2 = user2.createJaxmpp().setConnected(true).build();
	}

	@Test(groups = {"XMPP - PubSub"}, description = "Removal of PEP nodes on user removal")
	public void testRemovalOfPepServiceNodesOnUserRemoval() throws Exception {
		final Mutex mutex = new Mutex();

		final RosterStore roster1 = jaxmpp1.getModule(RosterModule.class).getRosterStore();
		jaxmpp1.getEventBus()
				.addHandler(RosterModule.ItemAddedHandler.ItemAddedEvent.class, new RosterModule.ItemAddedHandler() {

					@Override
					public void onItemAdded(SessionObject sessionObject, RosterItem item, Set<String> modifiedGroups) {
						mutex.notify("added:" + item.getJid());
					}
				});
		roster1.add(user2.getJid(), "User2", null);
		mutex.waitFor(1000 * 10, "added:" + user2.getJid());

		jaxmpp1.getEventBus()
				.addHandler(RosterModule.ItemUpdatedHandler.ItemUpdatedEvent.class,
							new RosterModule.ItemUpdatedHandler() {

								@Override
								public void onItemUpdated(SessionObject sessionObject, RosterItem item,
														  RosterModule.Action action, Set<String> modifiedGroups) {

									if (action != null) {
										mutex.notify(item.getJid() + ":" + action);
									}
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
		jaxmpp2.getEventBus()
				.addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, subscriptionHandler1);
		PresenceModule.SubscribeRequestHandler subscriptionHandler2 = new PresenceModule.SubscribeRequestHandler() {

			@Override
			public void onSubscribeRequest(SessionObject sessionObject, Presence stanza, BareJID jid) {
				try {
					if (stanza.getType() == StanzaType.subscribe) {
						jaxmpp1.getModule(PresenceModule.class).subscribed(JID.jidInstance(jid));
					}
				} catch (Exception e) {
					e.printStackTrace();
					fail(e);
				}
			}
		};
		jaxmpp1.getEventBus()
				.addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, subscriptionHandler2);
		jaxmpp1.getModule(PresenceModule.class).subscribe(JID.jidInstance(user2.getJid()));

		mutex.waitFor(10 * 1000, user2.getJid() + ":both");
		assertTrue(mutex.isItemNotified(user2.getJid() + ":both"));

		Element payload = ElementFactory.create("geoloc", null, "http://jabber.org/protocol/geoloc");
		Element country = ElementFactory.create("country", "US", null);
		payload.addChild(country);
		jaxmpp1.getModule(PubSubModule.class)
				.publishItem(user1.getJid(), "http://jabber.org/protocol/geoloc", "test1", payload,
							 new PubSubModule.PublishAsyncCallback() {

								 @Override
								 public void onPublish(String itemId) {
									 mutex.notify("published:geoloc");
								 }

								 @Override
								 public void onTimeout() throws JaxmppException {
									 throw new UnsupportedOperationException(
											 "Not supported yet."); //To change body of generated methods, choose Tools | Templates.
								 }

								 @Override
								 protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
													   PubSubErrorCondition pubSubErrorCondition)
										 throws JaxmppException {
									 throw new UnsupportedOperationException(
											 "Not supported yet."); //To change body of generated methods, choose Tools | Templates.
								 }
							 });

		mutex.waitFor(10 * 1000, "published:geoloc");
		assertTrue(mutex.isItemNotified("published:geoloc"));

		jaxmpp2.getModule(PubSubModule.class)
				.retrieveItem(user1.getJid(), "http://jabber.org/protocol/geoloc", new RetrieveItemsAsyncCallback() {

					@Override
					public void onTimeout() throws JaxmppException {
						throw new UnsupportedOperationException(
								"Not supported yet."); //To change body of generated methods, choose Tools | Templates.
					}

					@Override
					protected void onRetrieve(IQ responseStanza, String nodeName, Collection<Item> items) {
						mutex.notify("retrieved:" + nodeName + ":" + items.size());
					}

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						throw new UnsupportedOperationException(
								"Not supported yet."); //To change body of generated methods, choose Tools | Templates.
					}
				});
		mutex.waitFor(10 * 1000, "retrieved:http://jabber.org/protocol/geoloc:1");
		assertTrue(mutex.isItemNotified("retrieved:http://jabber.org/protocol/geoloc:1"));

		jaxmpp2.getModule(DiscoveryModule.class)
				.getItems(JID.jidInstance(user1.getJid()), new DiscoveryModule.DiscoItemsAsyncCallback() {

					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						for (DiscoveryModule.Item item : items) {
							mutex.notify("discovered:1:item:" + item.getNode());
						}
						mutex.notify("discovered:1:items:" + items.size());
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						throw new UnsupportedOperationException(
								"Not supported yet."); //To change body of generated methods, choose Tools | Templates.
					}

					@Override
					public void onTimeout() throws JaxmppException {
						throw new UnsupportedOperationException(
								"Not supported yet."); //To change body of generated methods, choose Tools | Templates.
					}
				});
		mutex.waitFor(10 * 1000, "discovered:1:items:1", "discovered:1:item:http://jabber.org/protocol/geoloc");
		assertTrue(mutex.isItemNotified("discovered:1:items:1"));
		assertTrue(mutex.isItemNotified("discovered:1:item:http://jabber.org/protocol/geoloc"));

		user1.unregister();
		jaxmpp1 = null;

		Thread.sleep(2000);

		jaxmpp2.getModule(DiscoveryModule.class)
				.getItems(JID.jidInstance(user1.getJid()), new DiscoveryModule.DiscoItemsAsyncCallback() {

					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						for (DiscoveryModule.Item item : items) {
							mutex.notify("discovered:2:item:" + item.getNode());
						}
						mutex.notify("discovered:2:items:" + items.size());
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						throw new UnsupportedOperationException(
								"Not supported yet."); //To change body of generated methods, choose Tools | Templates.
					}

					@Override
					public void onTimeout() throws JaxmppException {
						throw new UnsupportedOperationException(
								"Not supported yet."); //To change body of generated methods, choose Tools | Templates.
					}
				});
		mutex.waitFor(10 * 1000, "discovered:2:items:0");
		assertTrue(mutex.isItemNotified("discovered:2:items:0"));
		assertFalse(mutex.isItemNotified("discovered:2:item:http://jabber.org/protocol/geoloc"));
	}
}
