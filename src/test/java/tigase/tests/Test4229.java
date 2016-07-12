/*
 * Test4229.java
 *
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
package tigase.tests;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.testng.Assert;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;

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
public class Test4229 extends AbstractTest {

	private boolean rest = false;

	private Map<String,Jaxmpp> jaxmpps = new HashMap<>();
	private JID pubsubJid;
	private Mutex mutex = new Mutex();
	private Map<String,NodeInfo> createdNodes = new HashMap<>();
	private Map<String,NodeInfo> parentNodes = new HashMap<>();

	private static final int SECOND = 1000;
	private CloseableHttpClient httpClient;
	private HttpClientContext localContext;
	private BareJID adminBareJid;

	@Test
	public void test_nodeCreationModificationAndRemoval() throws Exception {
		try {
			rest = false;
			pubsubJid = JID.jidInstance("pubsub." + getDomain(0));
			initConnections();
			ensureNodeItemExists(null, null, null, false);

			createNodes();
			createSubnodes();
			configureNodes();
			subscribeNodes();
			publishItemsToNodes();
			retractItemsFromNodes();
			unsubscribeNodes();
			deleteSubnodes();
			deleteNodes();
		} finally {
			closeConnections();
		}
	}

	@Test
	public void test_nodeCreationModificationAndRemovalByHttpApi() throws Exception {
		try {
			rest = true;
			// initialize HTTP client
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			localContext = HttpClientContext.create();
			int timeout = 15;
			httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
					.setDefaultRequestConfig(RequestConfig.custom()
							.setSocketTimeout(timeout * SECOND)
							.setConnectTimeout(timeout * SECOND)
							.setConnectionRequestTimeout(timeout * SECOND)
							.build()).build();

			pubsubJid = JID.jidInstance("pubsub." + getDomain(0));
			initConnections();
			ensureNodeItemExists(null, null, null, false);

			createNodes();
			createSubnodes();
			configureNodes();
			subscribeNodes();
			publishItemsToNodes();
			retractItemsFromNodes();
			unsubscribeNodes();
			deleteSubnodes();
			deleteNodes();
		} finally {
			closeConnections();
			httpClient.close();
			httpClient = null;
			localContext = null;
		}
	}

	private void createNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni  = new NodeInfo();
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (rest) {
				createNodeHttp(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getName(), false);
			} else {
				createNode(jaxmpp, ni.getNode(), ni.getName(), false);
			}
			createdNodes.put(hostname, ni);
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, true);
		}
	}

	private void createSubnodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni  = new NodeInfo();
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (rest) {
				createNodeHttp(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getName(), true);
			} else {
				createNode(jaxmpp, ni.getNode(), ni.getName(), true);
			}
			parentNodes.put(hostname, ni);
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, true);
		}
	}

	private void configureNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);
			NodeInfo pni = parentNodes.get(hostname);
			configureNode(jaxmpps.get(hostname), ni.getNode(), pni.getNode());
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), pni.getNode(), true);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
		}
	}

	private void subscribeNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			for (NodeInfo ni : createdNodes.values()) {
				Jaxmpp jaxmpp = jaxmpps.get(hostname);
				if (rest) {
					subscribeNodeHttp(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode());
				} else {
					subscribeNode(jaxmpp, ni.getNode());
				}
				Thread.sleep(1000);
			}
		}
	}

	private void unsubscribeNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			for (NodeInfo ni : createdNodes.values()) {
				Jaxmpp jaxmpp = jaxmpps.get(hostname);
				if (rest) {
					unsubscribeNodeHttp(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode());
				} else {
					unsubscribeNode(jaxmpp, ni.getNode());
				}
				Thread.sleep(1000);
			}
		}
	}

	private void deleteSubnodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);
			NodeInfo pni = parentNodes.get(hostname);
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (rest) {
				deleteNodeHttp(hostname, ni.getNode());
			} else {
				deleteNode(jaxmpp, ni.getNode(), ni.getName());
			}
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
			ensureNodeItemExists(ni.getNode(), ni.getName(), pni.getNode(), false);
		}
	}

	private void deleteNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = parentNodes.get(hostname);
			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (rest) {
				deleteNodeHttp(hostname, ni.getNode());
			} else {
				deleteNode(jaxmpp, ni.getNode(), ni.getName());
			}
			// on old version we need to wait
			//Thread.sleep(5000);
			Thread.sleep(1000);
			ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
		}
	}

	private void publishItemsToNodes() throws Exception {
		for (String hostname : getInstanceHostnames()) {
			NodeInfo ni = createdNodes.get(hostname);

			String itemId = ni.getItemId();
			Element payload = ElementFactory.create("test", itemId, null);
			String[] waitFor = jaxmpps.values().stream().map(jaxmpp1 -> "published:item:notified" + itemId + ":" + jaxmpp1.getSessionObject().getUserBareJid()).toArray(i -> new String[i]);
			PubSubModule.NotificationReceivedHandler handler = new PubSubModule.NotificationReceivedHandler() {
				@Override
				public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node, String itemId, Element element, Date date, String s2) {
					mutex.notify("published:item:notified" + itemId + ":" + sessionObject.getUserBareJid());
				}
			};
			jaxmpps.values().forEach( jaxmpp1 -> jaxmpp1.getEventBus().addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler));

			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (rest) {
				publishItemToNodeHttp(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getItemId(), payload);
			} else {
				publishItemToNode(jaxmpp, ni.getNode(), ni.getItemId(), payload);
			}

			mutex.waitFor(10 * 1000, waitFor);
			for (String waitedFor : waitFor) {
				assertTrue(mutex.isItemNotified(waitedFor));
			}
			jaxmpps.values().forEach(jaxmpp1 -> jaxmpp1.getEventBus().remove(handler) );
		}
	}

	private void retractItemsFromNodes() throws Exception {
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

			Jaxmpp jaxmpp = jaxmpps.get(hostname);
			if (rest) {
				retractItemFromNodeHttp(hostname, jaxmpp.getSessionObject().getUserBareJid(), ni.getNode(), ni.getItemId());
			} else {
				retractItemFromNode(jaxmpp, ni.getNode(), ni.getItemId());
			}


			mutex.waitFor(10 * 1000, waitFor);
			for (String waitedFor : waitFor) {
				assertTrue(mutex.isItemNotified(waitedFor));
			}
			jaxmpps.values().forEach(jaxmpp1 -> jaxmpp1.getEventBus().remove(handler) );
		}
	}

	// HTTP API based implementation
	private void createNodeHttp(String hostname, BareJID owner, String nodeName, String name, boolean collection) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("owner", owner.toString(), null));

		Element pubsub = ElementFactory.create("pubsub");
		pubsub.setAttribute("prefix", "true");
		pubsub.addChild(ElementFactory.create("node_type", collection ? "collection" : "leaf", null));
		pubsub.addChild(ElementFactory.create("title", name, null));
		command.addChild(pubsub);

		executeHttpApiRequest(hostname, "create-node", command);
	}

	private void deleteNodeHttp(String hostname, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		executeHttpApiRequest(hostname, "delete-node", command);
	}

	private void subscribeNodeHttp(String hostname, BareJID jid, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element jids = ElementFactory.create("jids");
		jids.addChild(ElementFactory.create("value", jid.toString(), null));
		command.addChild(jids);

		executeHttpApiRequest(hostname, "subscribe-node", command);
	}

	private void unsubscribeNodeHttp(String hostname, BareJID jid, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element jids = ElementFactory.create("jids");
		jids.addChild(ElementFactory.create("value", jid.toString(), null));
		command.addChild(jids);

		executeHttpApiRequest(hostname, "unsubscribe-node", command);
	}

	private void publishItemToNodeHttp(String hostname, BareJID owner, String nodeName, String itemId, Element payload) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));
		Element entry = ElementFactory.create("entry");
		entry.addChild(payload);
		command.addChild(entry);

		executeHttpApiRequest(hostname, "publish-item", command);
	}

	private void retractItemFromNodeHttp(String hostname, BareJID owner, String nodeName, String itemId) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));

		executeHttpApiRequest(hostname, "delete-item", command);
	}

	private String executeHttpApiRequest(String hostname, String action, Element command) throws IOException, XMLException {
		HttpHost target = new HttpHost( hostname, Integer.parseInt( getHttpPort() ), "http" );
		HttpPost postRequest = new HttpPost( "/rest/pubsub/" + pubsubJid + "/" + action);
		postRequest.addHeader( "Api-Key", getApiKey() );

		StringEntity entity = new StringEntity( command.getAsString() );
		entity.setContentType( "application/xml" );
		postRequest.setEntity( entity );

		HttpResponse response = null;
		try {
			response = httpClient.execute( target, postRequest, localContext );
		} catch ( Exception ex ) {
			fail( ex );
		}

		if ( response == null ){
			Assert.fail( "Request response not received" );
			return null;
		}

		String responseContent = response.getEntity() != null
				? inputStreamToString( response.getEntity().getContent() ) : "";
		Assert.assertEquals( response.getStatusLine().getStatusCode(), 200 );

		boolean responseContains = responseContent.toLowerCase().contains( "Operation successful".toLowerCase() );
		log("got response:" + responseContent);
		Assert.assertTrue( responseContains, "Publishing was successful" );

		return responseContent;
	}

	private String inputStreamToString( InputStream is ) throws IOException {
		Reader reader = new InputStreamReader( is );
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[ 1024 ];
		int read = 0;
		while ( ( read = reader.read( buf ) ) >= 0 ) {
			sb.append( buf, 0, read );
		}
		return sb.toString();
	}

	// Direct PubSub based implementation
	private void createNode(Jaxmpp jaxmpp, String nodeName, String name, boolean collection) throws JaxmppException, InterruptedException {
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

	private void deleteNode(Jaxmpp jaxmpp, String nodeName, String name) throws JaxmppException, InterruptedException {
		jaxmpp.getModule(PubSubModule.class).deleteNode(pubsubJid.getBareJid(), nodeName, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("deleted:node:" + nodeName + ":" + name);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "deleted:node:" + nodeName + ":" + name);
		assertTrue("Removal of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed", mutex.isItemNotified("deleted:node:" + nodeName + ":" + name));
	}

	private void configureNode(Jaxmpp jaxmpp, String nodeName, String parentNode) throws JaxmppException, InterruptedException {
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

	private void subscribeNode(Jaxmpp jaxmpp, String node) throws JaxmppException, InterruptedException {
		String id = UUID.randomUUID().toString();
		jaxmpp.getModule(PubSubModule.class).subscribe(pubsubJid.getBareJid(), node, JID.jidInstance(jaxmpp.getSessionObject().getUserBareJid()),  new PubSubModule.SubscriptionAsyncCallback() {
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

	private void unsubscribeNode(Jaxmpp jaxmpp, String node) throws JaxmppException, InterruptedException {
		String id = UUID.randomUUID().toString();
		jaxmpp.getModule(PubSubModule.class).unsubscribe(pubsubJid.getBareJid(), node, JID.jidInstance(jaxmpp.getSessionObject().getUserBareJid()),  new PubSubAsyncCallback() {
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

	private void publishItemToNode(Jaxmpp jaxmpp, String node, String itemId, Element payload) throws Exception {
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

	private void retractItemFromNode(Jaxmpp jaxmpp, String node, String itemId) throws Exception {
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

		public String getName() {
			return "Node " + id;
		}

		public String getNode() {
			return "node-" + id;
		}

		public String getItemId() { return "item-" + itemId; }
	}

}
