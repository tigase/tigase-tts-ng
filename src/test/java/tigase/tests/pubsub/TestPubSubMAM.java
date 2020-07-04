/*
 * TestPubSubMAM.java
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
package tigase.tests.pubsub;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
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
import tigase.tests.utils.Account;
import tigase.tests.utils.PubSubNode;

import static tigase.TestLogger.log;

import java.util.*;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.*;
import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

/**
 * Test is responsible for checking if support for MAM in PubSub component works correctly.
 * <p>
 * Created by andrzej on 26.12.2016.
 */
public class TestPubSubMAM
		extends AbstractTest {

	protected final Mutex mutex = new Mutex();
	protected PubSubNode COLLECTION;
	protected PubSubNode LEAF;
	protected PubSubNode ROOT;
	protected Jaxmpp jaxmpp;
	protected List<Item> publishedItems;
	protected JID pubsubJid;
	protected Account user;

	private long timeDrift = 0;

	@BeforeClass
	public void setUp() throws Exception {
		user = createAccount().setLogPrefix("user1").build();
		jaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			return jaxmpp;
		}).setConnected(true).build();

		ROOT = pubSubManager.createNode("root-" + UUID.randomUUID().toString())
				.setJaxmpp(jaxmpp)
				.setNodeType(PubSubNode.Type.collection)
				.build();
		COLLECTION = pubSubManager.createNode("collection-" + UUID.randomUUID().toString())
				.setJaxmpp(jaxmpp)
				.setNodeType(PubSubNode.Type.collection)
				.setParentCollection(ROOT.getName())
				.build();
		LEAF = pubSubManager.createNode("leaf-" + UUID.randomUUID().toString())
				.setJaxmpp(jaxmpp)
				.setNodeType(PubSubNode.Type.leaf)
				.setParentCollection(COLLECTION.getName())
				.build();

		pubsubJid = JID.jidInstance(LEAF.getPubsubJid());

		publishedItems = Collections.unmodifiableList(publishItems(20));
	}

	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(
				pubsubJid, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
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
		assertTrue(mutex.isItemNotified("discovery:identity:pubsub:service"));
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:mam:1"));
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

	protected void testRetriveAllFrom(PubSubNode node) throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(100);

		List<Item> expectedItems = new ArrayList<>(publishedItems);
		queryNode(node.getName(), query, rsm, expectedItems, true);
	}

	protected void testRetrieveWithLimitAndAfterFrom(PubSubNode node) throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(10);

		List<Item> expectedItems = publishedItems.stream().limit(10).collect(Collectors.toList());
		Item item = queryNode(node.getName(), query, rsm, expectedItems, false).get(4);

		expectedItems = publishedItems.stream().skip(5).limit(10).collect(Collectors.toList());
		rsm.setAfter(item.id);

		item = queryNode(node.getName(), query, rsm, expectedItems, false).get(4);

		expectedItems = publishedItems.stream().skip(10).limit(10).collect(Collectors.toList());
		rsm.setAfter(item.id);

		queryNode(node.getName(), query, rsm, expectedItems, true);
	}

	protected List<Item> updateExpectedItems(PubSubNode node) throws JaxmppException, InterruptedException {
		List<Item> results = new ArrayList<>();
		String queryId = UUID.randomUUID().toString();
		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = (sessionObject, queryid, messageId, timestamp, message) -> {
			if (!queryId.equals(queryid)) {
				return;
			}

			Element eventEl = message.getChildrenNS("event", "http://jabber.org/protocol/pubsub#event");
			Element itemsEl = eventEl.getFirstChild("items");
			Element itemEl = itemsEl.getFirstChild("item");
			Item item = new Item(messageId, timestamp, itemEl.getAttribute("id"), itemEl.getFirstChild());
			item.publishedAt = timestamp;
			results.add(item);
		};

		jaxmpp.getEventBus()
				.addHandler(
						MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		MessageArchiveManagementModule mamModule = jaxmpp.getModule(MessageArchiveManagementModule.class);

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		
		mamModule.queryItems(query, pubsubJid, node.getName(), queryId, null,
							 new MessageArchiveManagementModule.ResultCallback() {
								 @Override
								 public void onSuccess(String queryid, boolean complete, RSM rsm)
										 throws JaxmppException {
									 mutex.notify("mam:queryId:" + queryid + ":complete");
								 }

								 @Override
								 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										 throws JaxmppException {

								 }

								 @Override
								 public void onTimeout() throws JaxmppException {

								 }
							 });

		mutex.waitFor(20 * 1000, "mam:queryId:" + queryId + ":complete");
		assertTrue(mutex.isItemNotified("mam:queryId:" + queryId + ":complete"));

		Thread.sleep(2000);

		jaxmpp.getEventBus()
				.remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		results.sort(Comparator.comparing(o -> o.timestamp));

		assertEquals(publishedItems.size(), results.size());

		return results;
	}

	protected void testRetrieveWithTimestampsLimitAndAfterFrom(PubSubNode node) throws Exception {
		publishedItems = updateExpectedItems(node);

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(10);

		query.setEnd(new Date());
		query.setStart(new Date());
		queryNode(node.getName(), query, rsm, Collections.emptyList(), true);

		long startDrift = Math.min(timeDrift, 0);
		long endDrift = Math.max(timeDrift, 0);

		List<Item> expectedItems = publishedItems.stream().limit(10).collect(Collectors.toList());
		query.setStart(new Date(((long) (expectedItems.get(0).timestamp.getTime() / 1000)) * 1000 + startDrift));
		query.setEnd(new Date(
				((long) (expectedItems.get(expectedItems.size() - 1).publishedAt.getTime() / 1000) + 1) * 1000 + endDrift));
		queryNode(node.getName(), query, rsm, expectedItems, true);

		expectedItems = publishedItems.stream().skip(5).limit(10).collect(Collectors.toList());
		query.setStart(new Date(((long) (expectedItems.get(0).timestamp.getTime() / 1000)) * 1000 + startDrift));
		query.setEnd(new Date(
				((long) (expectedItems.get(expectedItems.size() - 1).publishedAt.getTime() / 1000) + 1) * 1000 + endDrift));

		queryNode(node.getName(), query, rsm, expectedItems, true);

		expectedItems = publishedItems.stream().skip(10).limit(10).collect(Collectors.toList());
		query.setStart(new Date(((long) (expectedItems.get(0).timestamp.getTime() / 1000)) * 1000 + startDrift));
		query.setEnd(new Date(
				((long) (expectedItems.get(expectedItems.size() - 1).publishedAt.getTime() / 1000) + 1) * 1000 + endDrift));
		queryNode(node.getName(), query, rsm, expectedItems, true);
	}

	protected List<Item> queryNode(String nodeName, MessageArchiveManagementModule.Query query, RSM rsm,
								   List<Item> expectedItems, boolean complete) throws Exception {
		List<Item> results = new ArrayList<>();
		String queryId = UUID.randomUUID().toString();
		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = (sessionObject, queryid, messageId, timestamp, message) -> {
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

		Thread.sleep(2000);

		jaxmpp.getEventBus()
				.remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		results.sort(Comparator.comparing(o -> o.timestamp));

		assertEquals(expectedItems.size(), results.size());
		assertListEquals(expectedItems, results);

		if (!expectedItems.isEmpty()) {
			long drift = 0;
			for (int i = 0; i < expectedItems.size(); i++) {
				drift += results.get(i).timestamp.getTime() - expectedItems.get(i).timestamp.getTime();
			}
			this.timeDrift = drift / expectedItems.size();
		}
		return results;
	}

	protected List<Item> publishItems(int count) throws Exception {
		List<Item> results = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Item item = new Item();
			jaxmpp.getModule(PubSubModule.class)
					.publishItem(pubsubJid.getBareJid(), LEAF.getName(), item.itemId, item.payload,
								 new PubSubAsyncCallback() {
									 @Override
									 public void onSuccess(Stanza responseStanza) throws JaxmppException {
										 item.publishedAt(new Date());
										 mutex.notify("publish:node:root:item-id:" + item.itemId);
									 }

									 @Override
									 public void onTimeout() throws JaxmppException {

									 }

									 @Override
									 protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
														   PubSubErrorCondition pubSubErrorCondition)
											 throws JaxmppException {

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

	protected static class Item {

		private static long ALLOWED_TIME_DRIFT = 5000;

		private final String id;
		private final String itemId;
		private final Element payload;
		private final Date timestamp;
		private Date publishedAt = null;

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

		public void publishedAt(Date timestamp) {
			this.publishedAt = timestamp;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Item) {
				Item i = (Item) obj;
				try {
					if (itemId.equals(i.itemId) && payload.getAsString().equals(i.payload.getAsString())) {
						if (publishedAt != null) {
							double i11 = Math.floor(((double) timestamp.getTime()) / 10000) * 10000 - ALLOWED_TIME_DRIFT;
							double i12 = Math.ceil(((double) publishedAt.getTime()) / 10000) * 10000 + ALLOWED_TIME_DRIFT;
							double i2 = Math.floor(((double) i.timestamp.getTime()) / 10000) * 10000;
							if (!(i11 <= i2 && i2 <= i12)) {
								log("got error from 1: " + i11 + " : " + i12 + " : " + i2);
							}
							return i11 <= i2 && i2 <= i12;
						} else if (i.publishedAt != null) {
							double i11 = Math.floor(((double) i.timestamp.getTime()) / 10000) * 10000 - ALLOWED_TIME_DRIFT;
							double i12 = Math.ceil(((double) i.publishedAt.getTime()) / 10000) * 10000 + ALLOWED_TIME_DRIFT;
							double i2 = Math.floor(((double) timestamp.getTime()) / 10000) * 10000;
							if (!(i11 <= i2 && i2 <= i12)) {
								log("got error from 2: " + i11 + " : " + i12 + " : " + i2);
							}
							return i11 <= i2 && i2 <= i12;
						} else {
							if (timestamp.getTime() != i.timestamp.getTime()) {
								log("got error from 3: " + timestamp.getTime() + " : " + i.timestamp.getTime());
							}

							return timestamp.getTime() == i.timestamp.getTime();
						}
					} else {
						log("wrong id or payload!");
					}
				} catch (XMLException e) {
					return false;
				}
			}  else {
				log("wrong type!");
			}
			return false;
		}

		@Override
		public String toString() {
			try {
				String result = "[id: " + itemId + ", payload: " + payload.getAsString() + ", timestamp: " +
						timestamp.getTime();
				if (publishedAt != null) {
					result += ", publishedAt: " + publishedAt.getTime();
				}
				return result + "]";
			} catch (XMLException e) {
				e.printStackTrace();
				return e.getMessage();
			}
		}
	}
}
