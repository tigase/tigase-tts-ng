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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.*;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 13.07.2016.
 */
public abstract class TestPubSubAbstract extends AbstractTest {

	protected Map<String,Jaxmpp> jaxmpps = new HashMap<>();
	protected JID pubsubJid;
	protected Mutex mutex = new Mutex();
	protected Map<String,NodeInfo> createdNodes = new HashMap<>();
	protected Map<String,NodeInfo> parentNodes = new HashMap<>();

	protected abstract void createNode(String hostname, BareJID owner, String node, String name, boolean collection) throws Exception;
	protected abstract void configureNode(String hostname, String node, String parentNode) throws Exception;
	protected abstract void subscribeNode(String hostname, BareJID subscriber, String node) throws Exception;
	protected abstract void unsubscribeNode(String hostname, BareJID subscriber, String node) throws Exception;
	protected abstract void deleteNode(String hostname, String node) throws Exception;

	protected abstract void publishItemToNode(String hostname, BareJID publisher, String node, String itemId, Element payload) throws Exception;
	protected abstract void retrieveItemFromNode(String hostname, String node, String itemId, ResultCallback<Element> callback) throws Exception;
	protected abstract void retractItemFromNode(String hostname, String node, String itemId) throws Exception;
	protected abstract void retrieveUserSubscriptions(String hostname, BareJID userJid, String nodePattern, ResultCallback<List<String>> callback) throws Exception;

	@BeforeClass
	public void setUp() throws Exception {
		super.setUp();
		pubsubJid = JID.jidInstance("pubsub." + getDomain(0));
		initConnections();
		ensureNodeItemExists(null, null, null, false);
	}

	@AfterClass
	public void tearDown() throws Exception {
		closeConnections();
	}

	@Test
	public void createNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni  = new NodeInfo();
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			createNode(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getName(), false);
			createdNodes.put(hostname, ni);
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, true);
		}
	}

	@Test(dependsOnMethods = { "createNodes" })
	public void createSubnodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni  = new NodeInfo();
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			createNode(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getName(), true);
			parentNodes.put(hostname, ni);
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, true);
		}
	}

	@Test(dependsOnMethods = { "createSubnodes" })
	public void configureNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);
			NodeInfo pni = parentNodes.get(hostname);
			configureNode(hostname, ni.getNode(), pni.getNode());
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), pni.getNode(), true);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
		}
	}

	@Test(dependsOnMethods = { "configureNodes" })
	public void subscribeNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			for (NodeInfo ni : createdNodes.values()) {
				Jaxmpp jaxmpp = jaxmpps.get(hostname);
				subscribeNode(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode());
				Thread.sleep(1000);
			}
		}
	}

	@Test(dependsOnMethods = { "retractItemsFromNodes" })
	public void unsubscribeNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			for (NodeInfo ni : createdNodes.values()) {
				Jaxmpp jaxmpp = jaxmpps.get(hostname);
				unsubscribeNode(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode());
				Thread.sleep(1000);
			}
		}
	}

	@Test(dependsOnMethods = { "unsubscribeNodes" })
	public void deleteSubnodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);
			NodeInfo pni = parentNodes.get(hostname);
			deleteNode(hostname, ni.getNode());
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
			ensureNodeItemExists(ni.getNode(), ni.getName(), pni.getNode(), false);
		}
	}

	@Test(dependsOnMethods = { "deleteSubnodes" })
	public void deleteNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = parentNodes.get(hostname);
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			deleteNode(hostname, ni.getNode());
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
		}
	}

	@Test(dependsOnMethods = { "subscribeNodes" })
	public void publishItemsToNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);

			String itemId = ni.getItemId();
			Element payload = ElementFactory.create("test", itemId, null);
			ni.setItemPayload(payload);
			String[] waitFor = jaxmpps.values().stream().map(jaxmpp1 -> "published:item:notified" + itemId + ":" + jaxmpp1.getSessionObject().getUserBareJid()).toArray(i -> new String[i]);
			PubSubModule.NotificationReceivedHandler handler = new PubSubModule.NotificationReceivedHandler() {
				@Override
				public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node, String itemId, Element element, Date date, String s2) {
					mutex.notify("published:item:notified" + itemId + ":" + sessionObject.getUserBareJid());
				}
			};
			jaxmpps.values().forEach( jaxmpp1 -> jaxmpp1.getEventBus().addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler));

			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			publishItemToNode(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getItemId(), payload);

			mutex.waitFor(10 * 1000, waitFor);
			for (String waitedFor : waitFor) {
				assertTrue(mutex.isItemNotified(waitedFor));
			}
			jaxmpps.values().forEach(jaxmpp1 -> jaxmpp1.getEventBus().remove(handler) );
		}
	}


	@Test(dependsOnMethods = { "publishItemsToNodes" })
	public void retrieveItemsFromNodes() throws Exception {
		createdNodes.values().forEach((NodeInfo ni) -> {
			for (String hostname : getInstanceHostnames()) {
				try {
					String waitFor = "retrieved:item:" + ni.getItemId() + ":payload-matches:true:" + hostname;
					retrieveItemFromNode(hostname, ni.getNode(), ni.getItemId(), (Element payload) -> {
						mutex.notify("retrieved:item:" + ni.getItemId() + ":payload-matches:" + ni.getItemPayload().equals(payload) + ":" + hostname);
					});

					mutex.waitFor(10 * 1000, waitFor);
					assertTrue(mutex.isItemNotified(waitFor));
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		});
	}

	@Test(dependsOnMethods = { "retrieveItemsFromNodes" })
	public void retractItemsFromNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);

			String itemId =  ni.getItemId();
			String[] waitFor = jaxmpps.values().stream().map(jaxmpp1 -> "retracted:item:notified" + itemId + ":" + jaxmpp1.getSessionObject().getUserBareJid()).toArray(i -> new String[i]);
			PubSubModule.NotificationReceivedHandler handler = new PubSubModule.NotificationReceivedHandler() {
				@Override
				public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node, String itemId, Element element, Date date, String s2) {
					try {
						String id = message.getFirstChild("event").getFirstChild("items").getFirstChild("retract").getAttribute("id");
						mutex.notify("retracted:item:notified" + id + ":" + sessionObject.getUserBareJid());
					} catch (Exception ex) {
						assertTrue(false);
					}
				}
			};
			jaxmpps.values().forEach( jaxmpp1 -> jaxmpp1.getEventBus().addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler));

			retractItemFromNode(hostname, ni.getNode(), ni.getItemId());

			mutex.waitFor(10 * 1000, waitFor);
			for (String waitedFor : waitFor) {
				assertTrue(mutex.isItemNotified(waitedFor));
			}
			jaxmpps.values().forEach(jaxmpp1 -> jaxmpp1.getEventBus().remove(handler) );
		}
	}

	@Test(dependsOnMethods = { "publishItemsToNodes" })
	public void retrieveUserSubscriptions() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);
			BareJID user = jaxmpps.get(hostname).getSessionObject().getUserBareJid();
			retrieveUserSubscriptions(hostname, user, null, (List<String> result) -> {
				assertTrue(result.contains(ni.getNode()));
			});
		}
	}

	@Test(dependsOnMethods = { "publishItemsToNodes" })
	public void retrieveUserSubscriptionsWithRegex() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);
			BareJID user = jaxmpps.get(hostname).getSessionObject().getUserBareJid();
			retrieveUserSubscriptions(hostname, user, "(?!" + ni.getNode() + ")", (List<String> result) -> {
				assertTrue(!result.contains(ni.getNode()));
			});
		}
	}

	private void ensureNodeItemExists(String nodeName, String name, String parentNode, boolean exists) throws JaxmppException, InterruptedException {
		String id = UUID.randomUUID().toString();
		List<String> awaitFor = new ArrayList<>();
		for (String hostname : this.getInstanceHostnames()) {
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			jaxmpp.getModule(DiscoveryModule.class).getItems(pubsubJid, parentNode, new DiscoveryModule.DiscoItemsAsyncCallback() {
				@Override
				public void onInfoReceived(String node, ArrayList<DiscoveryModule.Item> items) throws XMLException {
					items.forEach((it) -> {
						mutex.notify("received:node:" + id + ":" + hostname + ":" + it.getNode() + ":" + it.getName());
					});
					mutex.notify("received:nodes:" + id + ":" + hostname);
				}

				@Override
				public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
					assertTrue(false);
				}

				@Override
				public void onTimeout() throws JaxmppException {
					assertTrue(false);
				}
			});
			awaitFor.add("received:nodes:" + id + ":" + hostname);
		}
		mutex.waitFor(10 * 1000, awaitFor.toArray(new String[awaitFor.size()]));
		if (nodeName != null) {
			for (String hostname : getInstanceHostnames()) {
				boolean val = mutex.isItemNotified("received:node:" + id + ":" + hostname + ":" + nodeName + ":" + name);

				assertEquals((exists ? "Not found" : "Found") + " node " + nodeName + " on cluster node " + hostname, exists, val);
			}
		}
	}


	private void initConnections() throws JaxmppException {
		for (String hostname : this.getInstanceHostnames()) {
			Jaxmpp jaxmpp = createJaxmppAdmin(hostname);
			jaxmpp.getModulesManager().register(new DiscoveryModule());
			jaxmpp.login(true);
			jaxmpps.put(hostname, jaxmpp);
		}
	}

	private void closeConnections() throws JaxmppException {
		for (String hostname : this.getInstanceHostnames()) {
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (jaxmpp != null && jaxmpp.isConnected())
				jaxmpp.disconnect(true);
		}
	}

	private class NodeInfo {
		private String id = UUID.randomUUID().toString();
		private String itemId = UUID.randomUUID().toString();
		private Element payload = null;

		public String getName() {
			return "Node " + id;
		}

		public String getNode() {
			return "node-" + id;
		}

		public String getItemId() { return "item-" + itemId; }

		public Element getItemPayload() {
			return payload;
		}

		protected void setItemPayload(Element payload) {
			this.payload = payload;
		}
	}

	public interface ResultCallback<T> {

		void finished(T result);

	}
}