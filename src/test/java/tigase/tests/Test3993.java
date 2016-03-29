package tigase.tests;

import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.util.*;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 27.03.2016.
 */
public class Test3993 extends AbstractTest {

	private Map<String,Jaxmpp> jaxmpps = new HashMap<>();
	private JID pubsubJid;
	private Mutex mutex = new Mutex();
	private Map<String,NodeInfo> createdNodes = new HashMap<>();
	private Map<String,NodeInfo> parentNodes = new HashMap<>();

	@Test
	public void test_nodeCreationModificationAndRemoval() throws Exception {
		try {
			pubsubJid = JID.jidInstance("pubsub." + getDomain(0));
			initConnections();
			ensureNodeItemExists(null, null, null, false);
			for (String hostname : getInstanceHostnames()) {
				NodeInfo ni  = new NodeInfo();
				createNode(jaxmpps.get(hostname), ni.getNode(), ni.getName(), false);
				createdNodes.put(hostname, ni);
				// on old version we need to wait
				//Thread.sleep(5000);
				Thread.sleep(1000);
				ensureNodeItemExists(ni.getNode(), ni.getName(), null, true);
			}
			for (String hostname : getInstanceHostnames()) {
				NodeInfo ni  = new NodeInfo();
				createNode(jaxmpps.get(hostname), ni.getNode(), ni.getName(), true);
				parentNodes.put(hostname, ni);
				// on old version we need to wait
				//Thread.sleep(5000);
				Thread.sleep(1000);
				ensureNodeItemExists(ni.getNode(), ni.getName(), null, true);
			}
			for (String hostname : getInstanceHostnames()) {
				NodeInfo ni = createdNodes.get(hostname);
				NodeInfo pni = parentNodes.get(hostname);
				configureNode(jaxmpps.get(hostname), ni.getNode(), pni.getNode());
				Thread.sleep(1000);
				ensureNodeItemExists(ni.getNode(), ni.getName(), pni.getNode(), true);
				ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
			}
			for (String hostname : getInstanceHostnames()) {
				NodeInfo ni = createdNodes.get(hostname);
				NodeInfo pni = parentNodes.get(hostname);
				deleteNode(jaxmpps.get(hostname), ni.getNode(), ni.getName());
				// on old version we need to wait
				//Thread.sleep(5000);
				Thread.sleep(1000);
				ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
				ensureNodeItemExists(ni.getNode(), ni.getName(), pni.getNode(), false);
			}
			for (String hostname : getInstanceHostnames()) {
				NodeInfo ni = parentNodes.get(hostname);
				deleteNode(jaxmpps.get(hostname), ni.getNode(), ni.getName());
				// on old version we need to wait
				//Thread.sleep(5000);
				Thread.sleep(1000);
				ensureNodeItemExists(ni.getNode(), ni.getName(), null, false);
			}
		} finally {
			closeConnections();
		}
	}

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

		public String getName() {
			return "Node " + id;
		}

		public String getNode() {
			return "node-" + id;
		}

	}
}
