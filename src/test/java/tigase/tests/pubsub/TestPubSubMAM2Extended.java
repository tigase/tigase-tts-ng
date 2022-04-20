/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.HiddenField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.core.client.xmpp.utils.DateTimeFormat;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;
import tigase.tests.utils.PubSubNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.*;
import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

/**
 * Test is responsible for checking if support for MAM in PubSub component works correctly.
 * <p>
 * Created by andrzej on 26.12.2016.
 */
public class TestPubSubMAM2Extended
		extends AbstractTest {

	private static final String MAM2_XMLNS = "urn:xmpp:mam:2";

	protected final Mutex mutex = new Mutex();
	protected PubSubNode COLLECTION;
	protected PubSubNode LEAF;
	protected PubSubNode ROOT;
	protected Jaxmpp jaxmpp;
	private Timer timer;
	protected List<Item> publishedItems;
	protected JID pubsubJid;
	protected Account user;

	private long timeDrift = 0;

	@BeforeClass
	public void setUp() throws Exception {
		timer = new Timer();
		user = createAccount().setLogPrefix("user1").build();
		jaxmpp = user.createJaxmpp().setConnected(true).build();

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
		publishedItems = updateExpectedItems(LEAF);
	}
	
	@AfterClass
	public void cleanUp() throws Exception {
		timer.cancel();
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
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:mam:2"));
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:mam:2#extended"));
	}

	@Test
	public void testRetriveAllFromLeaf() throws Exception {
		testRetriveAllFrom(LEAF);
	}

	// disabled as MAM is specified only for leaf nodes
	@Test(enabled = false)
	public void testRetriveAllFromCollection() throws Exception {
		testRetriveAllFrom(COLLECTION);
	}

	// disabled as MAM is specified only for leaf nodes
	@Test(enabled = false)
	public void testRetriveAllFromRoot() throws Exception {
		testRetriveAllFrom(ROOT);
	}

	@Test
	public void testRetrieveWithLimitAndAfterFromLeaf() throws Exception {
		testRetrieveWithLimitAndAfterFrom(LEAF);
	}

	// disabled as MAM is specified only for leaf nodes
	@Test(enabled = false)
	public void testRetrieveWithLimitAndAfterFromCollection() throws Exception {
		testRetrieveWithLimitAndAfterFrom(COLLECTION);
	}

	// disabled as MAM is specified only for leaf nodes
	@Test(enabled = false)
	public void testRetrieveWithLimitAndAfterFromRoot() throws Exception {
		testRetrieveWithLimitAndAfterFrom(ROOT);
	}

	@Test
	public void testRetrieveWithAfterIdFromLeaf() throws Exception {
		testRetrieveWithAfterIdFrom(LEAF);
	}

	@Test
	public void testRetrieveWithAfterIdAndBeforeIdFromLeaf() throws Exception {
		testRetrieveWithAfterIdAndBeforeIdFrom(LEAF);
	}

	protected void testRetriveAllFrom(PubSubNode node) throws Exception {
		ExtendedQuery query = new ExtendedQuery();
		RSM rsm = new RSM();
		rsm.setMax(100);

		Item[] expectedItems = publishedItems.toArray(Item[]::new);
		Result result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));
	}

	protected void testRetrieveWithLimitAndAfterFrom(PubSubNode node) throws Exception {
		ExtendedQuery query = new ExtendedQuery();
		RSM rsm = new RSM();
		rsm.setMax(10);

		Item[] expectedItems = publishedItems.stream().limit(10).toArray(Item[]::new);
		Result result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));

		expectedItems = publishedItems.stream().skip(5).limit(10).toArray(Item[]::new);
		rsm.setAfter(publishedItems.get(4).id);
		result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));

		expectedItems = publishedItems.stream().skip(10).limit(10).toArray(Item[]::new);
		rsm.setAfter(publishedItems.get(9).id);
		result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));
	}

	protected void testRetrieveWithAfterIdFrom(PubSubNode node) throws Exception {
		ExtendedQuery query = new ExtendedQuery();
		RSM rsm = new RSM();
		rsm.setMax(10);

		Item[] expectedItems = publishedItems.stream().limit(10).toArray(Item[]::new);
		Result result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));
		
		expectedItems = publishedItems.stream().skip(5).limit(10).toArray(Item[]::new);
		TestLogger.log("afterId:" + publishedItems.get(4).id);
		query.setAfterId(publishedItems.get(4).id);
		result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		TestLogger.log("published items: " + publishedItems.stream().map(item -> item.id).collect(Collectors.toList()));
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));

		expectedItems = publishedItems.stream().skip(10).limit(10).toArray(Item[]::new);
		query.setAfterId(publishedItems.get(9).id);
		result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));
	}

	protected void testRetrieveWithAfterIdAndBeforeIdFrom(PubSubNode node) throws Exception {
		ExtendedQuery query = new ExtendedQuery();
		RSM rsm = new RSM();
		rsm.setMax(10);

		Item[] expectedItems = publishedItems.stream().skip(5).limit(9).toArray(Item[]::new);
		query.setAfterId(publishedItems.get(4).id);
		query.setBeforeId(publishedItems.get(14).id);
		Result result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), query, rsm);
		assertArrayEquals(expectedItems, result.getItems().toArray(Item[]::new));
	}

	protected List<Item> updateExpectedItems(PubSubNode node) throws JaxmppException, InterruptedException {
		Result result = retrieveArchivedItems(mutex, jaxmpp, node.getName(), new ExtendedQuery(), null);

		List<Item> results = result.getItems()
				.stream()
				.sorted(Comparator.comparing(o -> o.timestamp))
				.collect(Collectors.toList());

		assertEquals(publishedItems.size(), results.size());

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
			Thread.sleep(2000);
		}
		return results;
	}

	protected void assertListEquals(List<Item> expected, List<Item> actual) {
		assertNotNull(expected);
		assertNotNull(actual);
		assertEquals(expected.size(), actual.size());
		assertArrayEquals(expected.toArray(), actual.toArray());
	}

	protected Result retrieveArchivedItems(final Mutex mutex, Jaxmpp jaxmpp, String node, ExtendedQuery query, RSM rsm)
			throws JaxmppException, InterruptedException {
		String queryId = UUID.randomUUID().toString();
		Result result = new Result(queryId);
		MessageArchiveItemReceivedEventHandler handler = new MessageArchiveItemReceivedEventHandler() {
			@Override
			public void onArchiveItemReceived(SessionObject sessionObject, String queryid, String messageId,
											  Date timestamp, Message message) throws JaxmppException {
				if (!queryId.equals(queryid)) {
					return;
				}

				Element eventEl = message.getChildrenNS("event", "http://jabber.org/protocol/pubsub#event");
				Element itemsEl = eventEl.getFirstChild("items");
				Element itemEl = itemsEl.getFirstChild("item");
				Item item = new Item(messageId, timestamp, itemEl.getAttribute("id"), itemEl.getFirstChild());
				item.publishedAt = timestamp;
				result.addItem(item);
			}
		};
		jaxmpp.getContext()
				.getEventBus()
				.addHandler(
						MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
						handler);
		AtomicReference<XMPPException> exceptionRef = new AtomicReference<>(null);
		queryItems(jaxmpp, node, query, queryId, rsm,
				   new ResultCallback() {
					   @Override
					   public void onSuccess(String queryid, boolean complete, RSM rsm)
							   throws JaxmppException {
						   result.setCompleted(complete);
						   result.setRsm(rsm);
						   timer.schedule(new TimerTask() {
							   @Override
							   public void run() {
								   mutex.notify("items:" + queryId + ":received");
							   }
						   }, 1000);
					   }

					   @Override
					   public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							   throws JaxmppException {
						   mutex.notify("items:" + queryId + ":received");
						   exceptionRef.set(new XMPPException(error));
					   }

					   @Override
					   public void onTimeout() throws JaxmppException {
						   mutex.notify("items:" + queryId + ":received");
						   exceptionRef.set(new XMPPException(XMPPException.ErrorCondition.remote_server_timeout));
					   }
				   });

		mutex.waitFor(10 * 1000, "items:" + queryId + ":received");

		jaxmpp.getContext()
				.getEventBus()
				.remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
						handler);

		if (exceptionRef.get() != null) {
			throw exceptionRef.get();
		}

		result.items.sort(Comparator.comparing(o -> o.timestamp));

		return result;
	}

	protected void queryItems(Jaxmpp jaxmpp, String node, ExtendedQuery query, String queryid, RSM rsm, ResultCallback callback) throws JaxmppException {
		if (query != null) {
			this.queryItems(jaxmpp, node, query.toJabberDataElement(format), queryid, rsm, callback);
		} else {
			this.queryItems(jaxmpp, node, (JabberDataElement) null, queryid, rsm, callback);
		}
	}

	protected void queryItems(Jaxmpp jaxmpp, String node, JabberDataElement form, String queryid, RSM rsm, ResultCallback callback)
			throws JaxmppException {
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);
		iq.setTo(pubsubJid);

		callback.setQueryid(queryid);
		Element query = ElementFactory.create("query", null, MAM2_XMLNS);
		iq.addChild(query);

		if (queryid != null) {
			query.setAttribute("queryid", queryid);
		}
		if (node != null) {
			query.setAttribute("node", node);
		}

		if (form != null) {
			query.addChild(form);
		}

		if (rsm != null) {
			query.addChild(rsm.toElement());
		}

		jaxmpp.send(iq, callback);
	}

	private static final DateTimeFormat format = new DateTimeFormat();
	protected abstract class MessageArchiveItemReceivedEventHandler
			implements MessageModule.MessageReceivedHandler {

		public void onMessageReceived(SessionObject sessionObject, Chat chat, Message received) {
			try {
				Element resultEl = received.getChildrenNS("result", MAM2_XMLNS);
				if (resultEl == null) {
					return;
				}

				Element forwarded = resultEl.getChildrenNS("forwarded", "urn:xmpp:forward:0");
				if (forwarded == null) {
					return;
				}

				Element timestampEl = forwarded.getChildrenNS("delay", "urn:xmpp:delay");
				Element forwardedMessageEl = forwarded.getFirstChild("message");

				String queryid = resultEl.getAttribute("queryid");
				String messageId = resultEl.getAttribute("id");

				Date timestamp = format.parse(timestampEl.getAttribute("stamp"));
				Message forwarededMessage = (Message) Stanza.create(forwardedMessageEl);


				onArchiveItemReceived(sessionObject, queryid, messageId, timestamp, forwarededMessage);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}

		abstract void onArchiveItemReceived(SessionObject sessionObject, String queryid, String messageId, Date timestamp,
											Message message) throws JaxmppException;

		class MessageArchiveItemReceivedEvent
				extends JaxmppEvent<MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler> {

			private final Message message;
			private final String messageId;
			private final String queryid;
			private final Date timestamp;

			MessageArchiveItemReceivedEvent(SessionObject sessionObject, String queryid, String messageId,
											Date timestamp, Message message) {
				super(sessionObject);
				this.queryid = queryid;
				this.messageId = messageId;
				this.timestamp = timestamp;
				this.message = message;
			}

			@Override
			public void dispatch(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler) throws Exception {
				handler.onArchiveItemReceived(sessionObject, queryid, messageId, timestamp, message);
			}
		}

	}

	public static abstract class ResultCallback
			implements AsyncCallback {

		private String queryid;

		@Override
		public void onSuccess(Stanza responseStanza) throws JaxmppException {
			Element fin = responseStanza.getChildrenNS("fin", MAM2_XMLNS);
			RSM rsm = new RSM();
			rsm.fromElement(fin);
			boolean complete = "true".equals(fin.getAttribute("complete"));

			onSuccess(queryid, complete, rsm);
		}

		public abstract void onSuccess(String queryid, boolean complete, RSM rsm) throws JaxmppException;

		protected void setQueryid(String queryid) {
			this.queryid = queryid;
		}
	}

	private static class Result {
		private final String queryid;
		private RSM rsm;
		private List<Item> items = new ArrayList<>();
		private boolean completed = false;

		public Result(String queryid) {
			this.queryid = queryid;
		}

		public Optional<RSM> getRsm() {
			return Optional.ofNullable(rsm);
		}

		public void setRsm(RSM rsm) {
			this.rsm = rsm;
		}

		public void addItem(Item item) {
			items.add(item);
		}

		public List<Item> getItems() {
			return items;
		}

		public boolean isCompleted() {
			return completed;
		}

		public void setCompleted(boolean completed) {
			this.completed = completed;
		}

	}
	
	private class ExtendedQuery extends MessageArchiveManagementModule.Query {

		private String afterId;
		private String beforeId;
		private List<String> ids;

		public String getAfterId() {
			return afterId;
		}

		public void setAfterId(String afterId) {
			this.afterId = afterId;
		}

		public String getBeforeId() {
			return beforeId;
		}

		public void setBeforeId(String beforeId) {
			this.beforeId = beforeId;
		}

		public List<String> getIds() {
			return ids;
		}

		public void setIds(List<String> ids) {
			this.ids = ids;
		}

		@Override
		public JabberDataElement toJabberDataElement(DateTimeFormat df) throws XMLException {
			JabberDataElement form =  super.toJabberDataElement(df);
			((HiddenField) form.getField("FORM_TYPE")).setFieldValue("urn:xmpp:mam:2");
			if (beforeId != null) {
				form.addTextSingleField("before-id", beforeId);
			}
			if (afterId != null) {
				form.addTextSingleField("after-id", getAfterId());
			}
			if (ids != null && !ids.isEmpty()) {
				form.addListMultiField("ids", ids.toArray(String[]::new));
			}
			return form;
		}
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
								System.out.println("got error from 1: " + i11 + " : " + i12 + " : " + i2);
							}
							return i11 <= i2 && i2 <= i12;
						} else if (i.publishedAt != null) {
							double i11 = Math.floor(((double) i.timestamp.getTime()) / 10000) * 10000 - ALLOWED_TIME_DRIFT;
							double i12 = Math.ceil(((double) i.publishedAt.getTime()) / 10000) * 10000 + ALLOWED_TIME_DRIFT;
							double i2 = Math.floor(((double) timestamp.getTime()) / 10000) * 10000;
							if (!(i11 <= i2 && i2 <= i12)) {
								System.out.println("got error from 2: " + i11 + " : " + i12 + " : " + i2);
							}
							return i11 <= i2 && i2 <= i12;
						} else {
							if (timestamp.getTime() != i.timestamp.getTime()) {
								System.out.println("got error from 3: " + timestamp.getTime() + " : " + i.timestamp.getTime());
							}

							return timestamp.getTime() == i.timestamp.getTime();
						}
					} else {
						System.out.println("wrong id or payload!");
					}
				} catch (XMLException e) {
					return false;
				}
			}  else {
				System.out.println("wrong type!");
			}
			return false;
		}

		@Override
		public String toString() {
			try {
				String result = "[stable-id: " + id + ", item-id: " + itemId + ", payload: " + payload.getAsString() + ", timestamp: " +
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
