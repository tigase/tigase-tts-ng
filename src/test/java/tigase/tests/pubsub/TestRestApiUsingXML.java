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

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.xml.XMLUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.*;

/**
 * Test is responsible for testing PubSub component node creation manipulation and removal including publication and
 * retraction of PubSub node items using PubSub protocol and AdHoc commands.
 * <p>
 * This test is executed on one or many cluster nodes and during execution checks propagation of changes between cluster
 * nodes.
 * <p>
 * Created by andrzej on 10.07.2016.
 */
public class TestRestApiUsingXML
		extends TestRestApiAbstract {

	protected static void assertCDataEquals(String expected, Element result, String[] path) throws XMLException {
		assertNotNull(result);
		Element node = result.findChild(path);
		assertNotNull(node);
		assertEquals(expected, node.getValue());
	}

	// HTTP API based implementation
	public void createNode(String hostname, BareJID owner, String nodeName, String name, boolean collection)
			throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("owner", owner.toString(), null));

		Element pubsub = ElementFactory.create("pubsub");
		pubsub.setAttribute("prefix", "true");
		pubsub.addChild(ElementFactory.create("node_type", collection ? "collection" : "leaf", null));
		pubsub.addChild(ElementFactory.create("title", name, null));
		command.addChild(pubsub);

		Element result = executeHttpApiRequest(hostname, "create-node", command);
		assertNotNull(result);

		assertCDataEquals("Operation successful", result, new String[]{"result", "Note", "value"});
	}

	public void deleteNode(String hostname, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element result = executeHttpApiRequest(hostname, "delete-node", command);
		assertNotNull(result);

		assertCDataEquals("Operation successful", result, new String[]{"result", "Note", "value"});
	}

	public void subscribeNode(String hostname, BareJID jid, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element jids = ElementFactory.create("jids");
		jids.addChild(ElementFactory.create("value", jid.toString(), null));
		command.addChild(jids);

		Element result = executeHttpApiRequest(hostname, "subscribe-node", command);
		assertNotNull(result);

		assertCDataEquals("Operation successful", result, new String[]{"result", "Note", "value"});
	}

	public void unsubscribeNode(String hostname, BareJID jid, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element jids = ElementFactory.create("jids");
		jids.addChild(ElementFactory.create("value", jid.toString(), null));
		command.addChild(jids);

		Element result = executeHttpApiRequest(hostname, "unsubscribe-node", command);
		assertNotNull(result);

		assertCDataEquals("Operation successful", result, new String[]{"result", "Note", "value"});
	}

	public void publishItemToNode(String hostname, BareJID owner, String nodeName, String itemId, Element payload)
			throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));
		Element entry = ElementFactory.create("entry");
		entry.addChild(payload);
		command.addChild(entry);

		Element result = executeHttpApiRequest(hostname, "publish-item", command);
		assertNotNull(result);

		assertCDataEquals("Operation successful", result, new String[]{"result", "Note", "value"});
	}

	public void retractItemFromNode(String hostname, String nodeName, String itemId) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));

		Element result = executeHttpApiRequest(hostname, "delete-item", command);
		assertNotNull(result);

		assertCDataEquals("Operation successful", result, new String[]{"result", "Note", "value"});
	}

	/** This is not available in HTTP API - we are doing this using PubSub protocol */
	public void configureNode(String hostname, String nodeName, String parentNode)
			throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		JabberDataElement nodeCfg = new JabberDataElement(XDataType.submit);
		nodeCfg.addTextSingleField("pubsub#collection", parentNode);
		jaxmpp.getModule(PubSubModule.class)
				.configureNode(pubsubJid.getBareJid(), nodeName, nodeCfg, new PubSubAsyncCallback() {
					@Override
					public void onSuccess(Stanza stanza) throws JaxmppException {
						mutex.notify("configured:node:" + nodeName + ":" + parentNode);
					}

					@Override
					public void onTimeout() throws JaxmppException {

					}

					@Override
					protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

					}
				});
		mutex.waitFor(10 * 1000, "configured:node:" + nodeName + ":" + parentNode);
		assertTrue("Configuration of node " + nodeName + " on " +
						   jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed",
				   mutex.isItemNotified("configured:node:" + nodeName + ":" + parentNode));
	}

	@Override
	protected void retrieveItemFromNode(String hostname, String nodeName, String itemId,
										ResultCallback<Element> callback) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));

		Element result = executeHttpApiRequest(hostname, "retrieve-item", command);
		assertCDataEquals(nodeName, result, new String[]{"result", "node", "value"});
		assertCDataEquals(itemId, result, new String[]{"result", "item-id", "value"});

		Element pubsubItem = result.findChild(new String[]{"result", "item", "value", "item"});
		assertNotNull(pubsubItem);

		Element payload = pubsubItem.getFirstChild();
		assertNotNull(payload);

		callback.finished(payload);
	}

	@Override
	protected void retrieveUserSubscriptions(String hostname, BareJID user, String nodePattern,
											 ResultCallback<List<String>> callback) throws Exception {
		Element command = ElementFactory.create("data");
		command.addChild(ElementFactory.create("jid", user.toString(), null));
		if (nodePattern != null) {
			command.addChild(ElementFactory.create("node-pattern", nodePattern, null));
		}

		Element result = executeHttpApiRequest(hostname, "retrieve-user-subscriptions", command);
		assertNotNull(result);

		callback.finished(result.getFirstChild("nodes").getChildren().stream().map(valueEl -> {
			try {
				return XMLUtils.unescape(valueEl.getValue());
			} catch (XMLException ex) {
				return null;
			}
		}).collect(Collectors.toList()));
	}

	protected Element executeHttpApiRequest(String hostname, String action, Element command)
			throws XMLException, IOException {
		String result = executeHttpApiRequest(hostname, action, command.getAsString(), "application/xml");
		Element response = parseXML(result);
		assertNotNull(response);
		assertEquals("result", response.getName());

		return response;
	}

}
