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
package tigase.tests.muc;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class TestModeration extends AbstractTest {

	final Mutex mutex = new Mutex();
	private MucModule muc1Module;
	private MucModule muc2Module;
	private List<MucModule> mucModules;
	private BareJID roomJID;
	private List<TestModeration.Item> sentMessages;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	private int messageToModerate = 0;
	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(
				JID.jidInstance(roomJID), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						if (identities != null) {
							identities.forEach(identity -> mutex.notify("discovery:identity:" + identity.getCategory() + ":" + identity.getType()));
						}
						if (features != null) {
							features.stream().forEach(feature -> mutex.notify("discovery:feature:" + feature));
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
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:message-moderate:0"));
	}
	
	@BeforeClass
	protected void setUp() throws Exception {
		user1 = createAccount().setLogPrefix("user1").build();
		user2 = createAccount().setLogPrefix("user2").build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(this::configureJaxmpp).setConnected(true).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(this::configureJaxmpp).setConnected(true).build();

		muc1Module = user1Jaxmpp.getModule(MucModule.class);
		muc2Module = user2Jaxmpp.getModule(MucModule.class);
		mucModules = Arrays.asList(muc1Module, muc2Module);

		roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1.getJid().getDomain());

		joinAll();
		sentMessages = sendRandomMessages(10);
		messageToModerate = new Random().nextInt(sentMessages.size()-1);
	}

	protected Jaxmpp configureJaxmpp(Jaxmpp jaxmpp) {
		jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
		return jaxmpp;
	}

	protected List<Item> sendRandomMessages(int messages) throws Exception {
		Mutex mutex1 = new Mutex();
		Random random = new Random();
		//List<Item> sentMessages = new ArrayList<Item>();
		List<Item> receivedMessage = new ArrayList<>();
		List<String> sentBodies = new ArrayList<>();
		AtomicInteger counter = new AtomicInteger(0);
		MucModule.MucMessageReceivedHandler handler = (sessionObject, message, room, s, date) -> {
			try {
				Element stanzaId = message.getFirstChild("stanza-id");
				receivedMessage.add(new Item(message.getFrom().getResource(), null, message.getBody(), stanzaId == null ? null : stanzaId.getAttribute("id")));
				mutex1.notify("muc:messages:sent:count:" + counter.incrementAndGet());
			} catch (XMLException e) {
				throw new RuntimeException(e);
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, handler);
		for (int i = 0; i < messages; i++) {
			int j = Math.abs(random.nextInt()) % mucModules.size();
			String body = "Message " + UUID.randomUUID().toString();
			sentBodies.add(body);
			mucModules.get(j).getRoom(roomJID).sendMessage(body);

//			sentMessages.add(item);

			Thread.sleep(1500);
		}
		mutex1.waitFor(messages * 2000, "muc:messages:sent:count:" + messages);
		user1Jaxmpp.getEventBus().remove(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, handler);

		assertEquals(sentBodies, receivedMessage.stream().map(item -> item.body).collect(Collectors.toList()));

		return Collections.unmodifiableList(receivedMessage);
	}

	protected void joinAll() throws Exception {
		user2Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
					@Override
					public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
						mutex.notify("2:joinAs:" + asNickname);
					}
				});

		final MutableObject<JabberDataElement> roomConfig = new MutableObject<JabberDataElement>();

		user1Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
					@Override
					public void onYouJoined(SessionObject sessionObject, final Room room, final String asXNickname) {
						mutex.notify("joinAs:user1");
					}
				});
		Room join = muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user1");

		mutex.waitFor(1000 * 20, "joinAs:user1");

		muc1Module.getRoomConfiguration(muc1Module.getRoom(roomJID), new MucModule.RoomConfgurationAsyncCallback() {
			@Override
			public void onConfigurationReceived(JabberDataElement jabberDataElement) throws XMLException {
				roomConfig.setValue(jabberDataElement);
				try {
					ElementBuilder b = ElementBuilder.create("iq");
					b.setAttribute("id", nextRnd())
							.setAttribute("to", roomJID.toString())
							.setAttribute("type", "set")
							.child("query")
							.setXMLNS("http://jabber.org/protocol/muc#owner")
							.child("x")
							.setXMLNS("jabber:x:data")
							.setAttribute("type", "submit");

					user1Jaxmpp.send(Stanza.create(b.getElement()));
				} catch (JaxmppException e) {
					fail(e);
				}
				mutex.notify("getConfig:success", "getConfig");
			}

			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("getConfig:error", "getConfig");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("getConfig:timeout", "getConfig");
			}
		});

		mutex.waitFor(1000 * 20, "getConfig");
		Assert.assertTrue(mutex.isItemNotified("joinAs:user1"));
		Assert.assertTrue(mutex.isItemNotified("getConfig:success"));

		Thread.sleep(1000);

		joinAs(user2Jaxmpp, roomJID, "user2", "joinAs:user2");
	}

	private void joinAs(final Jaxmpp jaxmpp, final BareJID roomJID, final String nick, String expectedEvent)
			throws InterruptedException {
		final Mutex mutex = new Mutex();

		final MucModule.YouJoinedHandler handlerJoined = new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("resp", "joinAs:" + asNickname);
			}
		};
		final MucModule.PresenceErrorHandler handlerError = new MucModule.PresenceErrorHandler() {
			@Override
			public void onPresenceError(SessionObject sessionObject, Room room, Presence presence, String asNickname) {
				mutex.notify("resp", "notJoinAs:" + asNickname);
			}
		};

		final tigase.jaxmpp.core.client.eventbus.EventListener listener = new EventListener() {
			@Override
			public void onEvent(Event<? extends EventHandler> event) {
			}
		};
		try {
			jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
			jaxmpp.getEventBus().addHandler(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
			jaxmpp.getEventBus().addListener(listener);
			final MucModule mucModule = jaxmpp.getModule(MucModule.class);

			mucModule.join(roomJID.getLocalpart(), roomJID.getDomain(), nick);

			mutex.waitFor(1000 * 20, "resp");

			Assert.assertTrue(mutex.isItemNotified(expectedEvent),
							  "Expected event '" + expectedEvent + "' not received.");
		} catch (JaxmppException e) {
			fail(e);
			e.printStackTrace();
		} finally {
			jaxmpp.getEventBus().remove(listener);
			jaxmpp.getEventBus().remove(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
			jaxmpp.getEventBus().remove(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
		}
	}
	
	@Test(dependsOnMethods = "testSupportAdvertisement")
	public void test_moderationNotification() throws JaxmppException, InterruptedException {
		Item item = sentMessages.get(messageToModerate);
		AtomicReference<Item> moderationItem = new AtomicReference<>();
		MucModule.MucMessageReceivedHandler handler = (sessionObject, message, room, s, date) -> {
			try {
				Element applyTo = message.getFirstChild("apply-to");
				if (applyTo != null &&
						item.msgId.equals(applyTo.getAttribute("id"))) {
					Element moderatedEl = applyTo.getFirstChild("moderated");
					if (moderatedEl != null) {
						Element stanzaId = message.getFirstChild("stanza-id");
						if (stanzaId != null) {
							moderationItem.set(
									new Item(message.getFrom().getResource(), null,
											 message.getBody(),
											 stanzaId.getAttribute("id")));
							mutex.notify("muc:moderate:notification:success");
							mutex.notify("muc:moderate:notification:received");
							return;
						}
					}
				}
				mutex.notify("muc:moderate:notification:invalid");
				mutex.notify("muc:moderate:notification:received");
			} catch (Throwable ex) {
				throw new RuntimeException(ex);
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class,
											 handler);

		IQ iq = IQ.create();
		iq.setTo(JID.jidInstance(roomJID));
		iq.setType(StanzaType.set);
		Element applyTo = ElementFactory.create("apply-to", null, "urn:xmpp:fasten:0");
		applyTo.setAttribute("id", item.msgId);
		iq.addChild(applyTo);
		Element moderate = ElementFactory.create("moderate", null, "urn:xmpp:message-moderate:0");
		applyTo.addChild(moderate);
		moderate.addChild(ElementFactory.create("retract", null, "urn:xmpp:message-retract:0"));
		moderate.addChild(ElementFactory.create("reason", "This message was inapropriate", null));

		user1Jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("muc:moderation:error:" + errorCondition);
				mutex.notify("muc:moderation:completed");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("muc:moderation:success");
				mutex.notify("muc:moderation:completed");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("muc:moderation:error:timeout");
				mutex.notify("muc:moderation:completed");
			}
		});
		mutex.waitFor(10000, "muc:moderation:completed");
		assertTrue(mutex.isItemNotified("muc:moderation:success"));
		mutex.waitFor(10000, "muc:moderate:notification:received");
		user1Jaxmpp.getEventBus().remove(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, handler);
		assertTrue(mutex.isItemNotified("muc:moderate:notification:success"));

		sentMessages = Stream.concat(sentMessages.stream(), Optional.ofNullable(moderationItem.get()).stream())
				.collect(Collectors.toUnmodifiableList());
	}

	@Test(dependsOnMethods = "test_moderationNotification")
	public void test_moderationInArchive() throws Exception {
		Item item = sentMessages.get(messageToModerate);
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = null;
		
		Item moderatedItem = new Item(item.nickname, item.ts, null, item.msgId);
		sentMessages = Stream.concat(Stream.concat(sentMessages.stream().limit(messageToModerate), Stream.of(moderatedItem)),
									  sentMessages.stream().skip(messageToModerate + 1)).collect(Collectors.toUnmodifiableList());


		assertRetrievedMessages(mutex, roomJID, sentMessages, sentMessages.size(), user1Jaxmpp, query, rsm, (complete, rsm1) -> {
			assertEquals(true, complete);
			assertEquals(sentMessages.size(), rsm1.getCount().intValue());
			assertEquals(0, rsm1.getIndex().intValue());
		}, true);
	}

	private List<Item> assertRetrievedMessages(Mutex mutex, BareJID roomJID, List<Item> expMessages, Integer expMessagesCount, Jaxmpp user1Jaxmpp,
																			MessageArchiveManagementModule.Query query, RSM rsm, Verifier verifier, boolean updateTimestamps)
			throws Exception {
		List<Item> receivedMessages = new ArrayList<>();
		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = (SessionObject sessionObject, String queryid, String messageId, Date timestamp, Message message) -> {
			receivedMessages.add(new Item(message.getFrom().getResource(), timestamp, message.getBody(), messageId));
		};
		user1Jaxmpp.getEventBus()
				.addHandler(
						MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		String queryId = UUID.randomUUID().toString();
		user1Jaxmpp.getModule(MessageArchiveManagementModule.class)
				.queryItems(query, JID.jidInstance(roomJID), null, queryId, rsm,
							new MessageArchiveManagementModule.ResultCallback() {
								@Override
								public void onSuccess(String queryid, boolean complete, RSM rsm)
										throws JaxmppException {
									try {
										verifier.check(complete, rsm);
										mutex.notify("mam:success:" + queryid + ":count=" + rsm.getCount(),
													 "mam:success:" + queryid);
									} catch (Throwable ex) {
										mutex.notify("mam:success:" + queryId);
									}
								}

								@Override
								public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										throws JaxmppException {
									// error
									mutex.notify("mam:success:" + queryId);
								}

								@Override
								public void onTimeout() throws JaxmppException {
									mutex.notify("mam:success:" + queryId);
								}
							});
		mutex.waitFor(30 * 1000, "mam:success:" + queryId);
		mutex.isItemNotified("mam:success:" + queryId + ":count=" + expMessagesCount);

		Thread.sleep(500);

		if (expMessages.size() != receivedMessages.size()) {
			System.out.println(expMessages);
			System.out.println(receivedMessages);
		}
		assertEquals(expMessages.size(), receivedMessages.size());
		expMessages.forEach(sent -> {
			Optional<Item> recv = receivedMessages.stream().filter(sent::equals).findFirst();
			if (!recv.isPresent()) {
				System.out.println(expMessages);
				System.out.println(receivedMessages);
			}
			assertTrue(recv.isPresent());
			if (updateTimestamps) {
				sent.updateTimestamp(recv.get().ts);
			}
		});

		user1Jaxmpp.getEventBus()
				.remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		return receivedMessages;
	}

	private interface Verifier {

		void check(boolean complete, RSM rsm);

	}

	private class Item {

		public final String body;
		public final String msgId;
		public final String nickname;
		public Date ts;

//		private Item(String nickname, Date ts, String body) {
//			this(nickname, ts, body, null);
//		}

		private Item(String nickname, Date ts, String body, String msgId) {
			this.nickname = nickname;
			this.ts = ts;
			this.body = body;
			this.msgId = msgId;
		}

		public void updateTimestamp(Date timestamp) {
			this.ts = timestamp;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Item) {
				Item o = (Item) obj;
				return Objects.equals(body, o.body) && Objects.equals(nickname, o.nickname) && msgId.equals(o.msgId) &&
						(ts == null || ((ts.getTime() / 1000) == (o.ts.getTime() / 1000)));
			}
			return false;
		}

		@Override
		public String toString() {
			return "Item{" + "body='" + body + '\'' + ", msgId='" + msgId + '\'' + ", nickname='" + nickname + '\'' +
					", ts=" + ts + '}';
		}
	}
}
