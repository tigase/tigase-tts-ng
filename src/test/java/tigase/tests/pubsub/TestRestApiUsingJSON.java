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

import tigase.http.coders.JsonCoder;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.*;

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
public class TestRestApiUsingJSON
		extends TestRestApiAbstract {

	// HTTP API based implementation
	public void createNode(String hostname, BareJID owner, String nodeName, String name, boolean collection) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);
		data.put("owner", owner.toString());
		data.put("pubsub#node_type", collection ? "collection" : "leaf");
		data.put("pubsub#title", name);

		Map<String, Object> result = executeHttpApiRequest(hostname, "create-node", data);
		assertNotNull(result);
		assertValueEquals("Operation successful", result, new String[] { "Note" });
	}

	public void deleteNode(String hostname, String nodeName) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);

		Map<String, Object> result = executeHttpApiRequest(hostname, "delete-node", data);
		assertNotNull(result);
		assertValueEquals("Operation successful", result, new String[] { "Note" });
	}

	public void subscribeNode(String hostname, BareJID jid, String nodeName) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);
		data.put("jids", Arrays.asList(jid.toString()));

		Map<String, Object> result = executeHttpApiRequest(hostname, "subscribe-node", data);
		assertNotNull(result);
		assertValueEquals("Operation successful", result, new String[] { "Note" });
	}

	public void unsubscribeNode(String hostname, BareJID jid, String nodeName) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);
		data.put("jids", Arrays.asList(jid.toString()));

		Map<String, Object> result = executeHttpApiRequest(hostname, "unsubscribe-node", data);
		assertNotNull(result);
		assertValueEquals("Operation successful", result, new String[] { "Note" });
	}

	public void publishItemToNode(String hostname, BareJID owner, String nodeName, String itemId, Element payload) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);
		if (itemId != null) {
			data.put("item-id", itemId);
		}
		data.put("entry", payload.getAsString());

		Map<String, Object> result = executeHttpApiRequest(hostname, "publish-item", data);
		assertNotNull(result);
		assertValueEquals("Operation successful", result, new String[] { "Note" });
	}

	@Override
	protected void retrieveItemFromNode(String hostname, String nodeName, String itemId, ResultCallback<Element> callback)
			throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);
		data.put("item-id", itemId);

		Map<String, Object> result = executeHttpApiRequest(hostname, "retrieve-item", data);

		assertValueEquals(nodeName, result, new String[] { "node" });
		assertValueEquals(itemId, result, new String[] { "item-id" });

		List<String> itemElemStrList = getValueAtPath(result, new String [] {"item" });
		assertNotNull(itemElemStrList);
		assertEquals(1, itemElemStrList.size());
		String itemElemStr = itemElemStrList.get(0);

		assertNotNull(itemElemStr);
		Element pubsubItem = parseXML(itemElemStr);
		assertNotNull(pubsubItem);
		assertEquals("item", pubsubItem.getName());

		Element payload = pubsubItem.getFirstChild();
		assertNotNull(payload);

		callback.finished(payload);
	}

	public void retractItemFromNode(String hostname, String nodeName, String itemId) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("node", nodeName);
		data.put("item-id", itemId);

		Map<String, Object> result = executeHttpApiRequest(hostname, "delete-item", data);
		assertNotNull(result);
		assertValueEquals("Operation successful", result, new String[] { "Note" });
	}

	@Override
	protected void retrieveUserSubscriptions(String hostname, BareJID userJid, String nodePattern,
											 ResultCallback<List<String>> callback) throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("jid", userJid.toString());
		if (nodePattern != null) {
			data.put("node-pattern", nodePattern);
		}

		Map<String, Object> result = executeHttpApiRequest(hostname, "retrieve-user-subscriptions", data);
		assertNotNull(result);

		callback.finished((List<String>) result.get("nodes"));
	}

	/** This is not available in HTTP API - we are doing this using PubSub protocol */
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

	protected Map<String, Object> executeHttpApiRequest(String hostname, String action, Map<String, Object> command)
			throws XMLException, IOException {
		String result = executeHttpApiRequest(hostname, action, new JsonCoder().encode(command), "application/json");
		assertNotNull(result);

		return (Map<String, Object>) new JsonCoder().decode(result);
	}

	protected static void assertValueEquals(Object expected, Map<String, Object> map, String[] path) {
		assertNotNull(map);

		Object value = map;
		for (String part : path) {
			if (value != null) {
				value = map.get(part);
				assertNotNull("Failed to found value for " + part, value);
			}
		}

		assertEquals(expected, value);
	}

	protected static <T> T getValueAtPath(Map<String, Object> map, String[] path) {
		Object value = map;
		for (String part : path) {
			if (value != null) {
				value = map.get(part);
			}
		}
		return (T) value;
	}
}
