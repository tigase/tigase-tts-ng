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
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.*;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

/**
 * Test is responsible for checking if support for MAM in PubSub component
 * works correctly.
 *
 * Created by andrzej on 26.12.2016.
 */
public class TestPubSubMAM
		extends AbstractTest {

	protected Jaxmpp jaxmpp;
	protected JID pubsubJid;
	protected BareJID userJid;

	protected final Mutex mutex = new Mutex();

	protected List<Item> publishedItems;

	protected String LEAF;
	protected String COLLECTION;
	protected String ROOT;

	@BeforeClass
	public void setUp() throws Exception {
		super.setUp();
		pubsubJid = JID.jidInstance("pubsub." + getDomain(0));
		userJid = createUserAccount("user1_");
		jaxmpp = createJaxmpp("user1_", userJid);
		jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
		jaxmpp.login(true);

		LEAF = "leaf-" + UUID.randomUUID().toString();
		COLLECTION = "collection-" + UUID.randomUUID().toString();
		ROOT = "root-" + UUID.randomUUID().toString();

		createNode(ROOT, true, null);
		createNode(COLLECTION, true, ROOT);
		createNode(LEAF, false, COLLECTION);

		publishedItems = Collections.unmodifiableList(publishItems(20));
	}

	@AfterClass
	public void tearDown() throws Exception {
		deleteNode(LEAF);
		deleteNode(COLLECTION);
		deleteNode(ROOT);

		removeUserAccount(jaxmpp);
	}

	@Test
	public void testRetriveAllFromLeaf() throws Exception {
		testRetriveAllFrom(LEAF);
	}

	@Test
	public void testRetriveAllFromCollection() throws Exception {
		testRetriveAllFrom(COLLECTION);
	}

	@Test
	public void testRetriveAllFromRoot() throws Exception {
		testRetriveAllFrom(ROOT);
	}

	@Test
	public void testRetrieveWithLimitAndAfterFromLeaf() throws Exception {
		testRetrieveWithLimitAndAfterFrom(LEAF);
	}

	@Test
	public void testRetrieveWithLimitAndAfterFromCollection() throws Exception {
		testRetrieveWithLimitAndAfterFrom(COLLECTION);
	}

	@Test
	public void testRetrieveWithLimitAndAfterFromRoot() throws Exception {
		testRetrieveWithLimitAndAfterFrom(ROOT);

	}

	@Test
	public void testRetrieveWithTimestampsLimitAndAfterFromRoom() throws Exception {
		testRetrieveWithTimestampsLimitAndAfterFrom(ROOT);
	}

	@Test
	public void testRetrieveWithTimestampsLimitAndAfterFromCollection() throws Exception {
		testRetrieveWithTimestampsLimitAndAfterFrom(COLLECTION);
	}

	@Test
	public void testRetrieveWithTimestampsLimitAndAfterFromLeaf() throws Exception {
		testRetrieveWithTimestampsLimitAndAfterFrom(LEAF);
	}

	protected void testRetriveAllFrom(String node) throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(100);

		List<Item> expectedItems = new ArrayList<>(publishedItems);
		queryNode(node, query, rsm, expectedItems, true);
	}

	protected void testRetrieveWithLimitAndAfterFrom(String node) throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(10);

		List<Item> expectedItems = publishedItems.stream().limit(10).collect(Collectors.toList());
		Item item = queryNode(node, query, rsm, expectedItems, false).get(4);

		expectedItems = publishedItems.stream().skip(5).limit(10).collect(Collectors.toList());
		rsm.setAfter(item.id);

		item = queryNode(node, query, rsm, expectedItems, false).get(4);

		expectedItems = publishedItems.stream().skip(10).limit(10).collect(Collectors.toList());
		rsm.setAfter(item.id);

		queryNode(node, query, rsm, expectedItems, true);
	}

	protected void testRetrieveWithTimestampsLimitAndAfterFrom(String node) throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(10);

		query.setEnd(new Date());
		query.setStart(new Date());
		queryNode(node, query, rsm, Collections.emptyList(), true);

		List<Item> expectedItems = publishedItems.stream().limit(10).collect(Collectors.toList());
		query.setStart(new Date(((long) (expectedItems.get(0).timestamp.getTime() / 1000)) * 1000));
		query.setEnd(
				new Date(((long) (expectedItems.get(expectedItems.size() - 1).timestamp.getTime() / 1000) + 1) * 1000));
		queryNode(node, query, rsm, expectedItems, true);

		expectedItems = publishedItems.stream().skip(5).limit(10).collect(Collectors.toList());
		query.setStart(new Date(((long) (expectedItems.get(0).timestamp.getTime() / 1000)) * 1000));
		query.setEnd(
				new Date(((long) (expectedItems.get(expectedItems.size() - 1).timestamp.getTime() / 1000) + 1) * 1000));

		queryNode(node, query, rsm, expectedItems, true);

		expectedItems = publishedItems.stream().skip(10).limit(10).collect(Collectors.toList());
		query.setStart(new Date(((long) (expectedItems.get(0).timestamp.getTime() / 1000)) * 1000));
		query.setEnd(
				new Date(((long) (expectedItems.get(expectedItems.size() - 1).timestamp.getTime() / 1000) + 1) * 1000));
		queryNode(node, query, rsm, expectedItems, true);
	}

	protected List<Item> queryNode(String nodeName, MessageArchiveManagementModule.Query query, RSM rsm,
								   List<Item> expectedItems, boolean complete) throws Exception {
		List<Item> results = new ArrayList<>();
		String queryId = UUID.randomUUID().toString();
		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = (queryid, messageId, timestamp, message) -> {
			if (!queryId.equals(queryid)) {
				return;
			}

			Element eventEl = message.getChildrenNS("event", "http://jabber.org/protocol/pubsub#event");
			Element itemsEl = eventEl.getFirstChild("items");
			Element itemEl = itemsEl.getFirstChild("item");
			results.add(new Item(messageId, timestamp, itemEl.getAttribute("id"), itemEl.getFirstChild()));
		};

		jaxmpp.getEventBus()
				.addHandler(
						MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		MessageArchiveManagementModule mamModule = jaxmpp.getModule(MessageArchiveManagementModule.class);

		mamModule.queryItems(query, pubsubJid, nodeName, queryId, rsm,
							 new MessageArchiveManagementModule.ResultCallback() {
								 @Override
								 public void onSuccess(String queryid, boolean complete, RSM rsm)
										 throws JaxmppException {
									 mutex.notify("mam:queryId:" + queryid + ":complete:" + complete);
								 }

								 @Override
								 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										 throws JaxmppException {

								 }

								 @Override
								 public void onTimeout() throws JaxmppException {

								 }
							 });

		mutex.waitFor(20 * 1000, "mam:queryId:" + queryId + ":complete:" + complete);
		assertTrue(mutex.isItemNotified("mam:queryId:" + queryId + ":complete:" + complete));

		Thread.sleep(200);

		jaxmpp.getEventBus()
				.remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		results.sort(Comparator.comparing(o -> o.timestamp));

		assertEquals(expectedItems.size(), results.size());
		assertListEquals(expectedItems, results);

		return results;
	}

	protected void createNode(String nodeName, boolean collection, String collectionName) throws Exception {
		JabberDataElement nodeCfg = new JabberDataElement(XDataType.submit);
		nodeCfg.addTextSingleField("pubsub#title", nodeName);
		if (collection) {
			nodeCfg.addTextSingleField("pubsub#node_type", "collection");
		} else {
			nodeCfg.addTextSingleField("pubsub#max_items", "100");
		}
		if (collectionName != null) {
			nodeCfg.addTextSingleField("pubsub#collection", collectionName);
		}
		jaxmpp.getModule(PubSubModule.class)
				.createNode(pubsubJid.getBareJid(), nodeName, nodeCfg, new PubSubAsyncCallback() {
					@Override
					protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

					}

					@Override
					public void onSuccess(Stanza stanza) throws JaxmppException {
						mutex.notify("created:node:" + nodeName + ":" + nodeName);
					}

					@Override
					public void onTimeout() throws JaxmppException {

					}
				});
		mutex.waitFor(10 * 1000, "created:node:" + nodeName + ":" + nodeName);

		assertTrue(
				"Creation of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") +
						" failed", mutex.isItemNotified("created:node:" + nodeName + ":" + nodeName));
	}

	protected void deleteNode(String nodeName) throws Exception {
		jaxmpp.getModule(PubSubModule.class).deleteNode(pubsubJid.getBareJid(), nodeName, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("deleted:node:" + nodeName);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "deleted:node:" + nodeName);
		assertTrue("Removal of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") +
						   " failed", mutex.isItemNotified("deleted:node:" + nodeName));
	}

	protected List<Item> publishItems(int count) throws Exception {
		List<Item> results = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Item item = new Item();
			jaxmpp.getModule(PubSubModule.class)
					.publishItem(pubsubJid.getBareJid(), LEAF, item.itemId, item.payload, new PubSubAsyncCallback() {
						@Override
						protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
											  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

						}

						@Override
						public void onSuccess(Stanza responseStanza) throws JaxmppException {
							mutex.notify("publish:node:root:item-id:" + item.itemId);
						}

						@Override
						public void onTimeout() throws JaxmppException {

						}
					});

			mutex.waitFor(20 * 1000, "publish:node:root:item-id:" + item.itemId);
			assertTrue(mutex.isItemNotified("publish:node:root:item-id:" + item.itemId));

			results.add(item);
			Thread.sleep(1500);
		}
		return results;
	}

	protected void assertListEquals(List<Item> expected, List<Item> actual) {
		assertNotNull(expected);
		assertNotNull(actual);
		assertEquals(expected.size(), actual.size());
		assertArrayEquals(expected.toArray(), actual.toArray());
	}

	protected class Item {

		private final String id;
		private final Date timestamp;
		private final String itemId;
		private final Element payload;

		public Item(String id, Date timestamp, String itemId, Element payload) {
			this.id = id;
			this.timestamp = timestamp;
			this.itemId = itemId;
			this.payload = payload;
		}

		public Item() throws XMLException {
			id = null;
			timestamp = new Date();
			itemId = UUID.randomUUID().toString();
			payload = ElementFactory.create("item", "Item: " + itemId, "http://tigase.org/pubsub#test");
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Item) {
				Item i = (Item) obj;
				return itemId.equals(i.itemId) && payload.equals(i.payload);
			}
			return false;
		}

		@Override
		public String toString() {
			try {
				return "[id: " + itemId + ", payload: " + payload.getAsString() + "]";
			} catch (XMLException e) {
				e.printStackTrace();
				return e.getMessage();
			}
		}
	}
}
