/*
 * TestMessageArchiveManagement.java
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
package tigase.tests.archive;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.AbstractField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
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

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule.mapChildrenToListOfJids;

/**
 * Created by andrzej on 24.07.2016.
 */
public class TestMessageArchiveManagementTigaseFasteningCollation
		extends AbstractTest {

	private static final String MAM2_XMLNS = "urn:xmpp:mam:2";
	private static final String MAMFC_XMLNS = "tigase:mamfc:0";
	private static final String USER_PREFIX = "mam-";
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;
	private Date testDate;

	private List<Item> archivedItems = new ArrayList<>();

	@BeforeClass
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			return jaxmpp;
		}).setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(jaxmpp -> {
			return jaxmpp;
		}).setConnected(true).build();
	}

	@AfterClass
	public void cleanUp() throws Exception {
	}

	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(
				JID.jidInstance(user1Jaxmpp.getSessionObject().getUserBareJid()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
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
		assertTrue(mutex.isItemNotified("discovery:feature:" + MAMFC_XMLNS));
	}

	@Test(dependsOnMethods = {"testSupportAdvertisement"})
	public void testRetrievalOfForm() throws Exception {
		final Mutex mutex = new Mutex();
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.get);

		Element query = ElementFactory.create("query", null, MAM2_XMLNS);
		iq.addChild(query);

		user1Jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("form:fields");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				Element queryEl = responseStanza.getChildrenNS("query", MAM2_XMLNS);
				if (queryEl != null) {
					Element x = queryEl.getChildrenNS("x", "jabber:x:data");
					if (x != null) {
						JabberDataElement form = new JabberDataElement(x);
						for (AbstractField f : form.getFields()) {
							Element val = f.getFirstChild("value");
							mutex.notify("form:field:" + f.getVar() + ":" + (val == null ? null : val.getValue()));
							List<Element> options = f.getChildren("option");
							if (options != null) {
								for (Element opt : options) {
									mutex.notify("form:field:" + f.getVar() + ":option:" +
														 opt.getFirstChild("value").getValue());
								}
							}
						}
					}
				}
				mutex.notify("form:fields");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("form:fields");
			}
		});

		mutex.waitFor(10 * 1000, "form:fields");

		assertTrue(mutex.isItemNotified("form:field:FORM_TYPE:" + MAM2_XMLNS));
		assertTrue(mutex.isItemNotified("form:field:with:null"));
		assertTrue(mutex.isItemNotified("form:field:start:null"));
		assertTrue(mutex.isItemNotified("form:field:end:null"));
		assertTrue(mutex.isItemNotified("form:field:{tigase:mamfc:0}collation:full"));
		for (String opt :  Arrays.asList("simplified", "full", "collate", "fastenings")) {
			assertTrue(mutex.isItemNotified("form:field:{tigase:mamfc:0}collation:option:" + opt));
		}
	}

	@Test(dependsOnMethods = {"testRetrievalOfForm"})
	public void testSettingsChange() throws Exception {
		final Mutex mutex = new Mutex();

		retrieveSettings(user1Jaxmpp, new SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
								  List<JID> never) throws JaxmppException {
				mutex.notify("settings:1:default:" + defValue.name());
				mutex.notify("settings:1");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("settings:1");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("settings:1");
			}
		});
		mutex.waitFor(10 * 1000, "settings:1");

		assertTrue(mutex.isItemNotified("settings:1:default:never"));

		changeSettings(mutex, user1Jaxmpp, MessageArchiveManagementModule.DefaultValue.roster);
		changeSettings(mutex, user1Jaxmpp, MessageArchiveManagementModule.DefaultValue.always);
	}

	@Test(dependsOnMethods = {"testSettingsChange"})
	public void archiveMessages() throws Exception {
		final Mutex mutex = new Mutex();
		sendTestMessage(mutex, user1Jaxmpp, user2Jaxmpp, "Test message " + UUID.randomUUID().toString(),
						archivedItems::add);
		sendTestMessageReceipt(mutex, user2Jaxmpp, user1Jaxmpp, archivedItems.get(archivedItems.size() - 1).getOriginId(), archivedItems::add);
		sendTestMessage(mutex, user1Jaxmpp, user2Jaxmpp, "Test message " + UUID.randomUUID().toString(),
						archivedItems::add);
		sendTestMessage(mutex, user2Jaxmpp, user1Jaxmpp, "Test message " + UUID.randomUUID().toString(),
						archivedItems::add);
		Thread.sleep(100);
		testDate = new Date();
		sendTestMessage(mutex, user2Jaxmpp, user1Jaxmpp, "Test message " + UUID.randomUUID().toString(),
						archivedItems::add);
		sendTestMessageReceipt(mutex, user1Jaxmpp, user2Jaxmpp, archivedItems.get(archivedItems.size() - 2).getOriginId(), archivedItems::add);
		sendTestMessageReceipt(mutex, user2Jaxmpp, user1Jaxmpp, archivedItems.get(archivedItems.size() - 3).getOriginId(), archivedItems::add);
		sendTestMessage(mutex, user1Jaxmpp, user2Jaxmpp, "Test message " + UUID.randomUUID().toString(),
						archivedItems::add);
		sendTestMessageReceipt(mutex, user2Jaxmpp, user1Jaxmpp, archivedItems.get(archivedItems.size() - 1).getOriginId(), archivedItems::add);
		sendTestMessage(mutex, user2Jaxmpp, user1Jaxmpp, "Test message " + UUID.randomUUID().toString(),
						archivedItems::add);
		Thread.sleep(100);

		// sync to have original stable-id
		Query query = new Query();
		query.setCollation(Collation.full);
		List<Item> items = retrieveMessages(mutex, user1Jaxmpp, query, null);
		assertEquals(items, archivedItems);
		archivedItems = Collections.unmodifiableList(new ArrayList<>(items));
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_Simplified() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.simplified);
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query);
		List<Item> expectedItems = this.archivedItems.stream().filter(it -> it.getReferencedId() == null).collect(
				Collectors.toList());
		assertEquals(actualItems, expectedItems);
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_SimplifiedAfter() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.simplified);
		RSM rsm = new RSM();
		rsm.setAfter(this.archivedItems.get(3).getStableId());
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query, rsm);
		List<Item> expectedItems = this.archivedItems.stream().skip(4).filter(it -> it.getReferencedId() == null).collect(
				Collectors.toList());
		assertEquals(actualItems, expectedItems);
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_Full() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.full);
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query);
		List<Item> expectedItems = this.archivedItems.stream().collect(
				Collectors.toList());
		assertEquals(actualItems, expectedItems);
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_FullAfter() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.full);
		RSM rsm = new RSM();
		rsm.setAfter(this.archivedItems.get(3).getStableId());
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query, rsm);
		List<Item> expectedItems = this.archivedItems.stream().skip(4).collect(
				Collectors.toList());
		assertEquals(actualItems, expectedItems);
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_Collate() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.collate);
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query);
		List<Item> expectedItems = this.archivedItems.stream()
				.filter(it -> it.getReferencedId() == null)
				.map(Item::clone)
				.collect(Collectors.toList());

		for (Item item: this.archivedItems) {
			if (item.getReferencedId() == null) {
				continue;
			}
			Stream<Applied> appliedStream = item.getElement().getChildren().stream().filter(it1 -> {
				try {
					return "urn:xmpp:chat-markers:0".equals(it1.getXMLNS()) ||
							"urn:xmpp:receipts".equals(it1.getXMLNS());
				} catch (Throwable ex) {
					throw new RuntimeException(ex);
				}
			}).map(it1 -> new Applied(item.getSender(), it1));
			expectedItems.stream().filter(it -> it.getOriginId().equals(item.getReferencedId())).findFirst().ifPresent(it -> {
				appliedStream.forEach(it::addApplied);
			});
		}

		assertEquals(actualItems, expectedItems);
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_CollateAfter() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.collate);
		RSM rsm = new RSM();
		rsm.setAfter(this.archivedItems.get(3).getStableId());
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query, rsm);
		List<Item> expectedItems = this.archivedItems.stream()
				.skip(4)
				.filter(it -> it.getReferencedId() == null)
				.map(Item::clone)
				.collect(Collectors.toList());

		for (Item item: this.archivedItems) {
			if (item.getReferencedId() == null) {
				continue;
			}
			Stream<Applied> appliedStream = item.getElement().getChildren().stream().filter(it1 -> {
				try {
					return "urn:xmpp:chat-markers:0".equals(it1.getXMLNS()) ||
							"urn:xmpp:receipts".equals(it1.getXMLNS());
				} catch (Throwable ex) {
					throw new RuntimeException(ex);
				}
			}).map(it1 -> new Applied(item.getSender(), it1));
			expectedItems.stream().filter(it -> it.getOriginId().equals(item.getReferencedId())).findFirst().ifPresent(it -> {
				appliedStream.forEach(it::addApplied);
			});
		}
		
		assertEquals(actualItems, expectedItems);
	}

	@Test(dependsOnMethods = {"archiveMessages"})
	public void checkFastening_CollateStart() throws Exception {
		Mutex mutex = new Mutex();
		Query query = new Query();
		query.setCollation(Collation.collate);
		query.setStart(testDate);
		List<Item> actualItems = retrieveMessages(mutex, user1Jaxmpp, query);
		List<Item> expectedItems = this.archivedItems.stream().skip(4)
				.filter(it -> it.getReferencedId() == null)
				.map(Item::clone)
				.collect(Collectors.toList());

		int i=0;
		for (Item item: this.archivedItems) {
			if (i<3) {
				i++;
				continue;
			}
			i++;
			if (item.getReferencedId() == null) {
				continue;
			}
			Stream<Applied> appliedStream = item.getElement().getChildren().stream().filter(it1 -> {
				try {
					return "urn:xmpp:chat-markers:0".equals(it1.getXMLNS()) ||
							"urn:xmpp:receipts".equals(it1.getXMLNS());
				} catch (Throwable ex) {
					throw new RuntimeException(ex);
				}
			}).map(it1 -> new Applied(item.getSender(), it1));
			Optional<Item> it = expectedItems.stream().filter(it1 -> it1.getOriginId().equals(item.getReferencedId())).findFirst();
			if (it.isPresent()) {
				appliedStream.forEach(it.get()::addApplied);
			} else {
				Item item1 = new Item(null, null, item.getReferencedId(), archivedItems.stream().filter(it2 -> it2.getOriginId().equals(item.getReferencedId())).findFirst().map(Item::getStableId).get(), null, null,
									  new ArrayList<>(appliedStream.collect(Collectors.toList())));
				expectedItems.add(1, item1);
			}
		}

		System.out.println(expectedItems);
		System.out.println(actualItems);
		assertEquals(actualItems, expectedItems);
	}

//	@Test(dependsOnMethods = {"testMessageRetrievalFromEmpty"})
//	public void testMessageArchival() throws Exception {
//		final Mutex mutex = new Mutex();
//		for (int i = 0; i < 20; i++) {
//			Jaxmpp sender = (i % 2 == 0) ? user2Jaxmpp : user1Jaxmpp;
//			Jaxmpp recipient = (i % 2 == 1) ? user2Jaxmpp : user1Jaxmpp;
//			expDates.add(new Date());
//			sendTestMessage(mutex, sender, recipient, i == 19, id -> {
//				if (user1Jaxmpp.equals(recipient)) {
//					expStableIds.add(id);
//				}
//			});
//			Thread.sleep(2000);
//		}
//	}

	public void changeSettings(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.DefaultValue value)
			throws Exception {
		String id = UUID.randomUUID().toString();
		updateSetttings(jaxmpp, value, null, null, new SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
								  List<JID> never) throws JaxmppException {
				mutex.notify("settings:" + id + ":default:" + defValue.name());
				mutex.notify("settings:" + id);
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("settings:" + id);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("settings:" + id);
			}
		});
		mutex.waitFor(10 * 1000, "settings:" + id);

		assertTrue(mutex.isItemNotified("settings:" + id + ":default:" + value.name()));
	}
	
	protected void retrieveSettings(Jaxmpp jaxmpp, SettingsCallback callback) throws JaxmppException {
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.get);
		iq.addChild(ElementFactory.create("prefs", null, MAM2_XMLNS));
		jaxmpp.send(iq, callback);
	}

	protected void updateSetttings(Jaxmpp jaxmpp, MessageArchiveManagementModule.DefaultValue defValue, List<JID> always, List<JID> never, SettingsCallback callback)
			throws JaxmppException {
		if (defValue == null) {
			throw new JaxmppException("Default value may not be NULL!");
		}
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);
		Element prefs = ElementFactory.create("prefs", null, MAM2_XMLNS);
		prefs.setAttribute("default", defValue.name());
		iq.addChild(prefs);

		if (always != null) {
			Element alwaysEl = ElementFactory.create("always");
			for (JID jid : always) {
				alwaysEl.addChild(ElementFactory.create("jid", jid.toString(), null));
			}
			prefs.addChild(alwaysEl);
		}
		if (never != null) {
			Element neverEl = ElementFactory.create("never");
			for (JID jid : never) {
				neverEl.addChild(ElementFactory.create("jid", jid.toString(), null));
			}
			prefs.addChild(neverEl);
		}

		jaxmpp.send(iq, callback);
	}

	public List<Item> retrieveMessages(final Mutex mutex, Jaxmpp jaxmpp, Query query) throws Exception {
		return retrieveMessages(mutex,jaxmpp, query, null);
	}

	public List<Item> retrieveMessages(final Mutex mutex, Jaxmpp jaxmpp, Query query, RSM rsm) throws Exception {
		String queryId = UUID.randomUUID().toString();
		List<Item> results = new ArrayList<>();
		Connector.StanzaReceivedHandler handler = (sessionObject, streamPacket) -> {
			try {
				if (Message.class.isAssignableFrom(streamPacket.getClass())) {
					Message msg = (Message) streamPacket;
					Element result = msg.getFirstChild("result");
					if (result == null) {
						return;
					}

					BareJID sender = null;
					BareJID recipient = null;
					String stableId = result.getAttribute("id");
					String originId = null;
					String referencedId = null;
					Element element = null;
					Element forwarded = result.getFirstChild("forwarded");
					if (forwarded != null) {
						Element m = forwarded.getFirstChild("message");
						if (m != null) {
							sender = JID.jidInstance(m.getAttribute("from")).getBareJid();
							recipient = JID.jidInstance(m.getAttribute("to")).getBareJid();
							originId = m.getAttribute("id");
							element = m;
							Element receivedEl = m.getFirstChild("received");
							referencedId = receivedEl == null ? null : receivedEl.getAttribute("id");
						}
					}
					List<Applied> appliedList = result.getChildren("applied").stream().flatMap(it -> {
						try {
							JID from = JID.jidInstance(it.getAttribute("from"));
							return it.getChildren()
									.stream()
									.filter(it1 -> {
										try {
											return "urn:xmpp:chat-markers:0".equals(it1.getXMLNS()) || "urn:xmpp:receipts".equals(it1.getXMLNS());
										} catch (Throwable ex) {
											throw new RuntimeException(ex);
										}
									})
									.map(it1 -> new Applied(from.getBareJid(), it1));
						} catch (Throwable ex) {
							throw new RuntimeException(ex);
						}
					}).collect(Collectors.toList());
					Item item = new Item(sender, recipient, originId, stableId, element, referencedId, appliedList);
					results.add(item);
				}
			} catch (JaxmppException ex) {
				assertNull(ex);
			}
		};
		jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);

		queryItems(user1Jaxmpp, query.toJabberDataElement(format), queryId, rsm, new ResultCallback() {

			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("query:" + queryId + ":error:" + errorCondition, "query:" + queryId);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("query:" + queryId + ":timeout", "query:" + queryId);
			}

			@Override
			public void onSuccess(String queryid, boolean complete, RSM rsm) throws JaxmppException {
				mutex.notify("query:" + queryId + ":success", "query:" + queryId);
			}
		});

		mutex.waitFor(3 * 1000, "query:" + queryId);

		assertTrue(mutex.isItemNotified("query:" + queryId + ":success"));

		return results;
	}
	
//	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query,
//									RSM rsm, List<String> expectedMessageTags, List<String> expStableIds, boolean complete, boolean updateExpDates) throws Exception {
//		String queryid = UUID.randomUUID().toString();
//
//		final AtomicInteger count = new AtomicInteger(0);
//
//		if (updateExpDates) {
//			expDates.clear();
//		}
//
//		List<String> stableIds = new ArrayList<>();
//
//		MessageArchiveItemReceivedEventHandler handler = new MessageArchiveItemReceivedEventHandler() {
//			@Override
//			public void onArchiveItemReceived(SessionObject sessionObject, String queryid, String messageId,
//											  Date timestamp, Message message) throws JaxmppException {
//				if (message.getTo().getBareJid().equals(jaxmpp.getSessionObject().getUserBareJid())) {
//					stableIds.add(messageId);
//				}
//				mutex.notify("item:" + message.getFrom() + ":" + message.getTo() + ":" + message.getBody());
//				if (updateExpDates) {
//					expDates.add(timestamp);
//				}
//				count.incrementAndGet();
//			}
//		};
//		jaxmpp.getContext()
//				.getEventBus()
//				.addHandler(
//						MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
//						handler);
//
//		queryItems(jaxmpp, query, queryid, rsm,
//							  new ResultCallback() {
//								  @Override
//								  public void onSuccess(String queryid, boolean complete, RSM rsm)
//										  throws JaxmppException {
//									  mutex.notify("items:received:" + queryid + ":" + complete);
//									  timer.schedule(new TimerTask() {
//										  @Override
//										  public void run() {
//											  mutex.notify("items:received");
//										  }
//									  }, 1000);
//
//								  }
//
//								  @Override
//								  public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
//										  throws JaxmppException {
//									  mutex.notify("items:received");
//								  }
//
//								  @Override
//								  public void onTimeout() throws JaxmppException {
//									  mutex.notify("items:received");
//								  }
//							  });
//
//		mutex.waitFor(10 * 1000, "items:received");
//
//		assertTrue(mutex.isItemNotified("items:received:" + queryid + ":" + complete));
//
//		jaxmpp.getContext()
//				.getEventBus()
//				.remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
//						handler);
//
//		for (String tag : expectedMessageTags) {
//			assertTrue("Not returned message: " + tag, mutex.isItemNotified("item:" + tag));
//		}
//
//		assertEquals(expectedMessageTags.size(), count.get());
//		assertEquals(expStableIds, stableIds);
//	}
//

	protected void queryItems(Jaxmpp jaxmpp, JabberDataElement form, String queryid, RSM rsm, ResultCallback callback)
			throws JaxmppException {
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);

		callback.setQueryid(queryid);
		Element query = ElementFactory.create("query", null, MAM2_XMLNS);
		iq.addChild(query);

		if (queryid != null) {
			query.setAttribute("queryid", queryid);
		}

		if (form != null) {
			query.addChild(form);
		}

		if (rsm != null) {
			query.addChild(rsm.toElement());
		}

		jaxmpp.send(iq, callback);
	}

	public void sendTestMessage(final Mutex mutex, Jaxmpp sender, Jaxmpp recipient, String body, Consumer<Item> consumer) throws Exception {
		Message message = Message.create();
		message.setBody(body);
		sendTestMessage(mutex, sender, recipient, message, consumer);
	}

	public void sendTestMessageReceipt(final Mutex mutex, Jaxmpp sender, Jaxmpp recipient, String receivedMessageId, Consumer<Item> consumer) throws Exception {
		Message message = Message.create();
		Element receipt = ElementFactory.create("received");
		receipt.setXMLNS("urn:xmpp:receipts");
		receipt.setAttribute("id", receivedMessageId);
		message.addChild(receipt);
		sendTestMessage(mutex, sender, recipient, message, consumer);
	}

	public void sendTestMessage(final Mutex mutex, Jaxmpp sender, Jaxmpp recipient, Message m, Consumer<Item> consumer) throws Exception {
		m.setId(UUID.randomUUID().toString());
		JID to = JID.jidInstance(recipient.getSessionObject().getUserBareJid());
		m.setType(StanzaType.chat);
		m.setTo(to);

		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					Element stanzaId = stanza.getChildrenNS("stanza-id", "urn:xmpp:sid:0");
					Element receipt = stanza.getChildrenNS("received", "urn:xmpp:receipts");
					consumer.accept(new Item(stanza.getFrom().getBareJid(), stanza.getTo().getBareJid(), stanza.getId(),
											 stanzaId == null ? null : stanzaId.getAttribute("id"), stanza.getWrappedElement(),
											 receipt == null ? null : receipt.getAttribute("id"), Collections.emptyList()));
					mutex.notify(stanza.getId());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}
		};

		recipient.getContext()
				.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		sender.getContext().getWriter().write(m);

		mutex.waitFor(10 * 1000, "" + m.getId());
		
		recipient.getContext()
				.getEventBus()
				.remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
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

	protected abstract class SettingsCallback
			implements AsyncCallback {
		@Override
		public void onSuccess(Stanza responseStanza) throws JaxmppException {
			Element prefs = responseStanza.getWrappedElement().getChildrenNS("prefs", MAM2_XMLNS);
			MessageArchiveManagementModule.DefaultValue defValue = MessageArchiveManagementModule.DefaultValue.valueOf(prefs.getAttribute("default"));
			List<JID> always = mapChildrenToListOfJids(prefs.getFirstChild("always"));
			List<JID> never = mapChildrenToListOfJids(prefs.getFirstChild("never"));
			onSuccess(defValue, always, never);
		}

		public abstract void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always, List<JID> never) throws JaxmppException;

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

	public static class Query extends MessageArchiveManagementModule.Query {

		private Collation collation = Collation.full;

		public void setCollation(Collation collation) {
			this.collation = collation;
		}

		@Override
		public JabberDataElement toJabberDataElement(DateTimeFormat df) throws XMLException {
			JabberDataElement data = super.toJabberDataElement(df);
			data.addFixedField("{"+MAMFC_XMLNS+"}collation", collation.name());
			return data;
		}
	}

	protected static enum Collation {
		simplified, full, collate, fastenings
	}

	protected class Item {

		private final List<Applied> appliedList;
		private final String originId;
		private final String stableId;
		private final Element element;
		private final String referencedId;

		private final BareJID sender;
		private final BareJID recipient;

		public Item(BareJID sender, BareJID recipient, String originId, String stableId, Element element, String referencedId, List<Applied> appliedList) {
			this.sender = sender;
			this.recipient = recipient;
			this.originId = originId;
			this.stableId = stableId;
			this.element = element;
			this.referencedId = referencedId;
			this.appliedList = appliedList;
		}

		public String getOriginId() {
			return originId;
		}

		public String getStableId() {
			return stableId;
		}

		public String getReferencedId() {
			return referencedId;
		}

		public BareJID getRecipient() {
			return recipient;
		}

		public BareJID getSender() {
			return sender;
		}

		public Element getElement() {
			return element;
		}

		public boolean equals(Object o) {
			if (o instanceof Item) {
				Item i = (Item) o;
				if ((Objects.equals(stableId, i.stableId) || (stableId == null || i.stableId == null))) {
					return equalsElements(element, i.element) && appliedList.equals(i.appliedList);
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return "Item{" + "originId='" + originId + '\'' + ", stableId='" + stableId + '\'' + ", element='" + element +
					'\'' + ", referencedId='" + referencedId + '\'' + ", sender=" + sender + ", recipient=" +
					recipient + "', applied=" + appliedList + "}'";
		}

		public Item clone() {
			return new Item(sender, recipient, originId, stableId, element, referencedId, new ArrayList<>(appliedList));
		}

		public void addApplied(Applied applied) {
			appliedList.add(applied);
		}

		public List<Applied> getAppliedList() {
			return appliedList;
		}

	}

	private static class Applied {

		private final BareJID sender;
		private final Element elem;

		public Applied(BareJID sender, Element elem) {
			this.sender = sender;
			this.elem = elem;
		}

		public BareJID getSender() {
			return sender;
		}

		public Element getElem() {
			return elem;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Applied)) {
				return false;
			}
			Applied applied = (Applied) o;
			return Objects.equals(sender, applied.sender) && equalsElements(elem, applied.elem);
		}

		@Override
		public String toString() {
			try {
				return "Applied{" + "sender=" + sender + ", elem=" + (elem == null ? elem : elem.getAsString()) + '}';
			} catch (XMLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static boolean equalsElements(Element e1, Element e2) {
		if (e1 == null || e2 == null) {
			return (e1 == null && e2 == null);
		}
		try {
			if (!Objects.equals(e1.getName(), e2.getName()) && Objects.equals(e1.getXMLNS(), e2.getXMLNS())) {
				return false;
			}
			if (!Objects.equals(e1.getAttributes().keySet(), e2.getAttributes().keySet())) {
				return false;
			}
			for (String key : e1.getAttributes().keySet()) {
				if (!Objects.equals(e1.getAttribute(key), e2.getAttribute(key))) {
					return false;
				}
			}
			return true;
		} catch (XMLException ex) {
			throw new RuntimeException(ex);
		}
	}
}