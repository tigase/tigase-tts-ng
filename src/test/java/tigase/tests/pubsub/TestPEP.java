/*
 * TestPEP.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2019 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests.pubsub;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractIQModule;
import tigase.jaxmpp.core.client.xmpp.modules.capabilities.CapabilitiesModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import static tigase.TestLogger.log;

import java.util.Collection;
import java.util.Date;
import java.util.UUID;

import static org.testng.AssertJUnit.*;
import static tigase.jaxmpp.core.client.JID.jidInstance;

/**
 * Test is responsible for testing PEP support.
 */
public class TestPEP extends AbstractTest {

	private Jaxmpp jaxmpp2;
	private Account user;
	private Jaxmpp jaxmpp;
	private Account user2;

	@BeforeMethod
	public void setUp() throws Exception {
		user = this.createAccount().setLogPrefix("pep1-").build();
		jaxmpp = user.createJaxmpp().setConfigurator(this::configure).setConnected(true).build();
		user2 = this.createAccount().setLogPrefix("pep2-").build();
		jaxmpp2 = user2.createJaxmpp().setConfigurator(this::configure).setConnected(true).build();
	}


	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(jidInstance(jaxmpp.getSessionObject().getUserBareJid()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						if (identities != null) {
							identities.forEach(identity -> mutex.notify("discovery:identity:" + identity.getCategory() + ":" + identity.getType()));
						}
						if (features != null) {
							features.forEach(feature -> mutex.notify("discovery:feature:" + feature));
						}
						mutex.notify("discovery:completed:success", "discovery:completed");
					}

					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("discovery:completed:error", "discovery:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovery:completed:timeout", "discovery:completed");
					}
				});

		mutex.waitFor(10 * 1000, "discovery:completed");
		assertTrue(mutex.isItemNotified("discovery:completed:success"));
		assertTrue(mutex.isItemNotified("discovery:identity:pubsub:pep"));
		assertTrue(mutex.isItemNotified("discovery:feature:http://jabber.org/protocol/pubsub#publish"));
		assertTrue(mutex.isItemNotified("discovery:feature:http://jabber.org/protocol/pubsub#subscribe"));
		assertTrue(mutex.isItemNotified("discovery:feature:http://jabber.org/protocol/pubsub#auto-subscribe"));
		assertTrue(mutex.isItemNotified("discovery:feature:http://jabber.org/protocol/pubsub#auto-create"));
		assertTrue(mutex.isItemNotified("discovery:feature:http://jabber.org/protocol/pubsub#access-presence"));
	}

	@Test
	public void testAccessModel_PEP_default() throws JaxmppException, InterruptedException {
		testAccessModel(null, true, true);
	}

	@Test
	public void testAccessModel_Presence() throws JaxmppException, InterruptedException {
		testAccessModel("presence", true, true);
	}

	@Test
	public void testAccessModel_WhiteList() throws JaxmppException, InterruptedException {
		testAccessModel("whitelist", true, false);
	}

	private void testAccessModel(String model, boolean expectedUser1, boolean expectedUser2)
			throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		createMutualSubscriptions(mutex, jaxmpp, jaxmpp2);

		jaxmpp.disconnect(true);
		jaxmpp2.disconnect(true);

		mutex.clear();
		Thread.sleep(500);

		jaxmpp.login(true);
		assertTrue(jaxmpp.isConnected());
		jaxmpp2.login(true);
		assertTrue(jaxmpp2.isConnected());

		JabberDataElement config = null;
		if (model != null) {
			config = new JabberDataElement(XDataType.submit);
			config.addFORM_TYPE("http://jabber.org/protocol/pubsub#node_config");
			config.addFixedField("pubsub#persist_items", "true");
			config.addFixedField("pubsub#access_model", model);
		}

		jaxmpp.getModulesManager().getModule(PubSubModule.class).createNode(user.getJid(),
																			"storage:bookmarks", config, new PubSubAsyncCallback() {
					@Override
					protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("node:create:error:" + errorCondition, "node:create");
					}

					@Override
					public void onSuccess(Stanza stanza) throws JaxmppException {
						mutex.notify("node:create:success", "node:create");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("node:create:timeout", "node:create");
					}
				});

		mutex.waitFor(10 * 1000, "node:create");
		assertTrue(mutex.isItemNotified("node:create:success"));

		jaxmpp2.getEventBus().addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, new PubSubModule.NotificationReceivedHandler() {
			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node,
											   String s1, Element element, Date date, String s2) {
				if (node.equals("storage:bookmarks") && jid.getBareJid().equals(user.getJid())) {
					mutex.notify("user2:node:item:received");
				}
			}
		});
		jaxmpp.getEventBus().addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, new PubSubModule.NotificationReceivedHandler() {
			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node,
											   String s1, Element element, Date date, String s2) {
				if (node.equals("storage:bookmarks") && jid.getBareJid().equals(user.getJid())) {
					mutex.notify("user:node:item:received");
				}
			}
		});

		jaxmpp2.getModule(PresenceModule.class).sendInitialPresence();

		jaxmpp.getModule(PubSubModule.class).publishItem(user.getJid(), "storage:bookmarks", "current",
														 ElementFactory.create("storage", null, "storage:bookmarks"),
														 new PubSubModule.PublishAsyncCallback() {
															 @Override
															 public void onPublish(String s) {
																 mutex.notify("node:item:publish:success", "node:item:publish");
															 }

															 @Override
															 protected void onEror(IQ iq,
																				   XMPPException.ErrorCondition errorCondition,
																				   PubSubErrorCondition pubSubErrorCondition)
																	 throws JaxmppException {
																 mutex.notify("node:item:publish:error:" + errorCondition, "node:item:publish");
															 }

															 @Override
															 public void onTimeout() throws JaxmppException {
																 mutex.notify("node:item:publish:timeout", "node:item:publish");
															 }
														 });

		mutex.waitFor(10 * 1000, "node:item:publish");
		assertTrue(mutex.isItemNotified("node:item:publish:success"));
		mutex.waitFor(10 * 1000, "user:node:item:received");
		assertEquals(expectedUser1, mutex.isItemNotified("user:node:item:received"));
		if (expectedUser2) {
			mutex.waitFor(10 * 1000, "user2:node:item:received");
			assertTrue(mutex.isItemNotified("user2:node:item:received"));
		} else {
			Thread.sleep(1000);
			assertFalse(mutex.isItemNotified("user2:node:item:received"));
		}

		jaxmpp.disconnect(true);
		jaxmpp2.disconnect(true);

		mutex.clear();
		Thread.sleep(500);

		jaxmpp.login(true);
		assertTrue(jaxmpp.isConnected());
		jaxmpp2.login(true);
		assertTrue(jaxmpp2.isConnected());

		mutex.waitFor(10 * 1000, "user:node:item:received");
		assertEquals(expectedUser1, mutex.isItemNotified("user:node:item:received"));
		if (expectedUser2) {
			mutex.waitFor(10 * 1000, "user2:node:item:received");
			assertTrue(mutex.isItemNotified("user2:node:item:received"));
		} else {
			Thread.sleep(1000);
			assertFalse(mutex.isItemNotified("user2:node:item:received"));
		}
	}

	private Jaxmpp configure(Jaxmpp jaxmpp) {
		jaxmpp.getModulesManager().register(new DummyModule());
		jaxmpp.getModulesManager().register(new CapabilitiesModule());
		return jaxmpp;
	}

	private void createMutualSubscriptions(Mutex mutex, Jaxmpp jaxmpp1, Jaxmpp jaxmpp2)
			throws JaxmppException, InterruptedException {
		log("creating mutual subscriptions between: " + jaxmpp1.getSessionObject().getUserBareJid() + " <-> " + jaxmpp2.getSessionObject().getUserBareJid());
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
		Assert.assertTrue(mutex.isItemNotified("subscription:" + id + ":success"));

		jaxmpp1.getModule(PresenceModule.class).removeSubscribeRequestHandler(handler1);
		jaxmpp2.getModule(PresenceModule.class).removeSubscribeRequestHandler(handler2);
	}

	private class DummyModule extends AbstractIQModule {

		@Override
		public Criteria getCriteria() {
			return ElementCriteria.empty();
		}

		@Override
		public String[] getFeatures() {
			return new String[] { "storage:bookmarks+notify" };
		}

		@Override
		protected void processGet(IQ iq) throws JaxmppException {

		}

		@Override
		protected void processSet(IQ iq) throws JaxmppException {

		}
	}
}
