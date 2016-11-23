/*
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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

import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.TextMultiField;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Test is responsible for testing PubSub component node creation
 * manipulation and removal including publication and retraction
 * of PubSub node items using PubSub protocol and AdHoc commands.
 *
 * This test is executed on one or many cluster nodes and during
 * execution checks propagation of changes between cluster nodes.
 *
 * Created by andrzej on 10.07.2016.
 */
public class TestPubSub extends TestPubSubAbstract {

	// Direct PubSub based implementation
	public void createNode(String hostname, BareJID owner, String nodeName, String name, boolean collection) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		JabberDataElement nodeCfg = new JabberDataElement(XDataType.submit);
		nodeCfg.addTextSingleField("pubsub#title", name);
		if (collection) {
			nodeCfg.addTextSingleField("pubsub#node_type", "collection");
		}
		jaxmpp.getModule(PubSubModule.class).createNode(pubsubJid.getBareJid(), nodeName, nodeCfg, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("created:node:" + nodeName + ":" + name);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "created:node:" + nodeName + ":" + name);

		assertTrue("Creation of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed", mutex.isItemNotified("created:node:" + nodeName + ":" + name));
	}

	public void deleteNode(String hostname, String nodeName) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		jaxmpp.getModule(PubSubModule.class).deleteNode(pubsubJid.getBareJid(), nodeName, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("deleted:node:" + nodeName );
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "deleted:node:" + nodeName );
		assertTrue("Removal of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed", mutex.isItemNotified("deleted:node:" + nodeName ));
	}

	public void configureNode(String hostname, String nodeName, String parentNode) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		JabberDataElement nodeCfg = new JabberDataElement(XDataType.submit);
		nodeCfg.addTextSingleField("pubsub#collection", parentNode);
		jaxmpp.getModule(PubSubModule.class).configureNode(pubsubJid.getBareJid(), nodeName, nodeCfg, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("configured:node:" + nodeName + ":" + parentNode);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "configured:node:" + nodeName + ":" + parentNode);
		assertTrue("Configuration of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed", mutex.isItemNotified("configured:node:" + nodeName + ":" + parentNode));
	}

	public void subscribeNode(String hostname, BareJID subscriber, String node) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		String id = UUID.randomUUID().toString();
		jaxmpp.getModule(PubSubModule.class).subscribe(pubsubJid.getBareJid(), node, JID.jidInstance(subscriber),  new PubSubModule.SubscriptionAsyncCallback() {
			@Override
			public void onTimeout() throws JaxmppException {
				assertTrue(false);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				assertTrue(false);
			}

			@Override
			protected void onSubscribe(IQ iq, PubSubModule.SubscriptionElement subscriptionElement) {
				mutex.notify("subscribed:nodes:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString());
			}
		});
		mutex.waitFor(10 * 1000, "subscribed:nodes:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString());
		assertTrue(mutex.isItemNotified("subscribed:nodes:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString()));
	}

	public void unsubscribeNode(String hostname, BareJID subcriber, String node) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		String id = UUID.randomUUID().toString();
		jaxmpp.getModule(PubSubModule.class).unsubscribe(pubsubJid.getBareJid(), node, JID.jidInstance(subcriber),  new PubSubAsyncCallback() {
			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("unsubscribed:nodes:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid());
			}

			@Override
			public void onTimeout() throws JaxmppException {
				assertTrue(false);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				assertTrue(false);
			}
		});
		mutex.waitFor(10 * 1000, "unsubscribed:nodes:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid());
		Thread.sleep(200);
		assertTrue(mutex.isItemNotified("unsubscribed:nodes:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid()));
	}

	public void publishItemToNode(String hostname, BareJID publisher, String node, String itemId, Element payload) throws Exception {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		jaxmpp.getModule(PubSubModule.class).publishItem(pubsubJid.getBareJid(), node, itemId, payload, new PubSubModule.PublishAsyncCallback() {
			@Override
			public void onTimeout() throws JaxmppException {
				assertTrue(false);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				assertTrue(false);
			}

			@Override
			public void onPublish(String s) {
				mutex.notify("published:item:" + itemId + ":" + jaxmpp.getSessionObject().getUserBareJid());
			}
		});
		mutex.waitFor(10 * 1000, "published:item:" + itemId + ":" + jaxmpp.getSessionObject().getUserBareJid());
		assertTrue(mutex.isItemNotified("published:item:" + itemId + ":" + jaxmpp.getSessionObject().getUserBareJid()));
	}

	@Override
	protected void retrieveItemFromNode(String hostname, String node, String itemId, ResultCallback<Element> callback)
			throws Exception {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		jaxmpp.getModule(PubSubModule.class).retrieveItem(pubsubJid.getBareJid(), node, itemId, new PubSubModule.RetrieveItemsAsyncCallback() {
			@Override
			protected void onRetrieve(IQ responseStanza, String nodeName, Collection<Item> items) {
				items.forEach(item -> callback.finished(item.getPayload()));
			}

			@Override
			public void onTimeout() throws JaxmppException {
				assertTrue(false);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				assertTrue(false);
			}
		});
	}

	public void retractItemFromNode(String hostname, String node, String itemId) throws Exception {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		jaxmpp.getModule(PubSubModule.class).deleteItem(pubsubJid.getBareJid(), node, itemId, new PubSubAsyncCallback() {
			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("retracted:item:" + itemId + ":" + jaxmpp.getSessionObject().getUserBareJid());
			}

			@Override
			public void onTimeout() throws JaxmppException {
				assertTrue(false);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				assertTrue(false);
			}
		});
		mutex.waitFor(10 * 1000, "retracted:item:" + itemId + ":" + jaxmpp.getSessionObject().getUserBareJid());
		assertTrue(mutex.isItemNotified("retracted:item:" + itemId + ":" + jaxmpp.getSessionObject().getUserBareJid()));
	}

	@Override
	protected void retrieveUserSubscriptions(String hostname, BareJID user, String nodePattern, ResultCallback<List<String>> callback)
			throws Exception {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);

		IQ iq = IQ.create();
		iq.setTo(pubsubJid);

		JabberDataElement x = new JabberDataElement(XDataType.submit);
		x.addJidSingleField("jid", JID.jidInstance(user));
		x.addTextSingleField("node-pattern", nodePattern);

		Element command = ElementFactory.create("command", null, "http://jabber.org/protocol/commands");
		command.setAttribute("node", "retrieve-user-subscriptions");
		command.setAttribute("action", "execute");

		command.addChild(x);

		iq.addChild(command);

		sendAndWait(jaxmpp, iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				JabberDataElement data = new JabberDataElement(responseStanza.findChild(new String[] { "iq", "command", "x" }));
				TextMultiField field = data.getField("nodes");
				assertNotNull(field);
				callback.finished(Arrays.asList(field.getFieldValue()));
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
	}

}
