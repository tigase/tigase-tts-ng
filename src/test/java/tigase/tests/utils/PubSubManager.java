/*
 * PubSubManager.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests.utils;

import org.testng.Assert;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.TestLogger.log;

import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 22.04.2017.
 */
public class PubSubManager extends AbstractManager {

	private final ConcurrentHashMap<Object, Set<PubSubNode>> nodes = new ConcurrentHashMap<>();

	public PubSubManager(AbstractTest test) {
		super(test);
	}

	public PubSubNodeBuilder createNode(String node) {
		return new PubSubNodeBuilder(this, node);
	}

	public void deleteNode(PubSubNode node) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = test.getJaxmppAdmin();
		deleteNode(node, jaxmpp);
		jaxmpp.disconnect(true);
	}

	public void deleteNode(PubSubNode node, Jaxmpp jaxmpp) throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		jaxmpp.getModule(PubSubModule.class).deleteNode(node.getPubsubJid(), node.getName(), new PubSubAsyncCallback() {
			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("deleted:node:" + node.getName());
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "deleted:node:" + node.getName());
		assertTrue("Removal of node " + node.getName() + " on " +
						   jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed",
				   mutex.isItemNotified("deleted:node:" + node.getName()));

		remove(node);
	}

	public void deleteNode(BareJID pubSubJid, String name, Jaxmpp jaxmpp) throws JaxmppException, InterruptedException {
		PubSubNode node = new PubSubNode(this, pubSubJid, name);
		deleteNode(node, jaxmpp);
	}

	public void add(PubSubNode node) {
		Object key = getScopeKey();
		add(node, key);
	}

	public void add(PubSubNode node, Object scopeKey) {
		if (nodes.computeIfAbsent(scopeKey, (k) -> new CopyOnWriteArraySet<>()).add(node)) {
			log("created pubsub node = " + node);
		}
	}

	public void remove(PubSubNode node) {
		Object key = getScopeKey();
		remove(node, key);
	}

	public void remove(PubSubNode node, Object key) {
		if (nodes.computeIfAbsent(key, (k) -> new CopyOnWriteArraySet<>()).remove(node)) {
			log("deleted pubsub node = " + node);
		}
	}

	protected void scopeFinished(Object key) {
		nodes.getOrDefault(key, new HashSet<>()).forEach(node -> {
			try {
				deleteNode(node);
			} catch (JaxmppException | InterruptedException e) {
				Logger.getLogger("tigase").log(Level.WARNING, "failed to remove node " + node, e);
			}
		});
	}

	protected PubSubNode createNode(PubSubNodeBuilder builder, JabberDataElement nodeCfg)
			throws JaxmppException, InterruptedException {
		BareJID pubsubJid = builder.getPubSubJid();
		String nodeName = builder.getName();
		final Mutex mutex = new Mutex();

		if (builder.getIfNotExists() || builder.getReplaceIfExists()) {
			builder.getJaxmpp()
					.getModule(DiscoveryModule.class)
					.getItems(JID.jidInstance(pubsubJid), new DiscoveryModule.DiscoItemsAsyncCallback() {
						@Override
						public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
								throws XMLException {
							items.stream().forEach(item -> mutex.isItemNotified(item.getNode() + ":exists"));
							mutex.notify("discovery:finished");
						}

						@Override
						public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
								throws JaxmppException {
							mutex.notify("discovery:finished");
						}

						@Override
						public void onTimeout() throws JaxmppException {
							mutex.notify("discovery:finished");
						}
					});
			mutex.waitFor(30 * 1000, "discovery:finished");
		}
		boolean exists = mutex.isItemNotified(nodeName + ":exists");
		if (exists) {
			if (builder.getIfNotExists()) {
				return null;
			}
			if (builder.getReplaceIfExists()) {
				builder.getJaxmpp()
						.getModule(PubSubModule.class)
						.deleteNode(pubsubJid, nodeName, new PubSubAsyncCallback() {
							@Override
							public void onSuccess(Stanza responseStanza) throws JaxmppException {
								mutex.notify(nodeName + ":node_removed:success");
								mutex.notify(nodeName + ":node_removed");
							}

							@Override
							public void onTimeout() throws JaxmppException {
								mutex.notify(nodeName + ":node_removed:timeout");
								mutex.notify(nodeName + ":node_removed");
							}

							@Override
							protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
												  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
								mutex.notify(nodeName + ":node_removed:error");
								mutex.notify(nodeName + ":node_removed");
							}
						});
				mutex.waitFor(30 * 1000, nodeName + ":node_removed");
				Assert.assertTrue(mutex.isItemNotified(nodeName + ":node_removed:success"));
			}
		}

		builder.getJaxmpp()
				.getModule(PubSubModule.class)
				.createNode(pubsubJid, nodeName, nodeCfg, new PubSubAsyncCallback() {
					@Override
					public void onSuccess(Stanza stanza) throws JaxmppException {
						mutex.notify(nodeName + ":create_node:success");
						mutex.notify(nodeName + ":create_node");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify(nodeName + ":create_node");
					}

					@Override
					protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						if (errorCondition == XMPPException.ErrorCondition.conflict &&
								(builder.getIfNotExists() || builder.getReplaceIfExists())) {
							mutex.notify(nodeName + ":create_node:success");
						}
						mutex.notify(nodeName + ":create_node");
					}
				});
		mutex.waitFor(30 * 1000, nodeName + ":create_node");
		Assert.assertTrue(mutex.isItemNotified(nodeName + ":create_node:success"),
						  "PubSub node " + nodeName + " not created");

		PubSubNode node = new PubSubNode(this, pubsubJid, nodeName);
		add(node);
		return node;
	}
	
}
