/*
 * TestMucHistoryTimestamps.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2018 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests.muc;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.muc.AbstractRoomsManager;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.utils.DateTimeFormat;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class TestMucHistoryTimestamps extends AbstractTest {

	final Mutex mutex = new Mutex();
	private MucModule muc1Module;
	private MucModule muc2Module;
	private List<MucModule> mucModules;
	private BareJID roomJID;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	List<Item> sentMessages;
	List<Item> receivedMessages1;
	List<Item> receivedMessages2;
	List<Item> receivedMessages3;

	@BeforeClass
	protected void setUp() throws Exception {
		user1 = createAccount().setLogPrefix("user1").build();
		user2 = createAccount().setLogPrefix("user2").build();
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();

		muc1Module = user1Jaxmpp.getModule(MucModule.class);
		muc2Module = user2Jaxmpp.getModule(MucModule.class);
		mucModules = Arrays.asList(muc1Module, muc2Module);

		roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1.getJid().getDomain());

		joinAll();
		sentMessages = sendRandomMessages(20);
	}

	@Test
	public void testHistoryFull1() throws JaxmppException, InterruptedException {
		Room room1 = muc1Module.getRoom(roomJID);
		muc1Module.leave(room1);

		receivedMessages1 = new ArrayList<>();
		MucModule.MucMessageReceivedHandler handler = (sessionObject, message, room, nickname, timestamp) -> {
			try {
				receivedMessages1.add(new Item1(nickname, timestamp, message.getBody(), message.getId()));
				if (receivedMessages1.size() == (sentMessages.size() + 1)) {
					mutex.notify("messages:received:1");
				}
			} catch (XMLException ex) {
				Assert.assertTrue(false);
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, handler);

		joinAs(user1Jaxmpp, roomJID, "user1", ":1");

		mutex.waitFor(20*1000,"messages:received:1");

		user1Jaxmpp.getEventBus().remove(handler);
		Assert.assertTrue(mutex.isItemNotified("messages:received:1"));

		Assert.assertEquals(receivedMessages1.stream().filter(item -> item.body != null).collect(Collectors.toList()), sentMessages);
	}

	@Test(dependsOnMethods = {"testHistoryFull1"})
	public void testHistoryFull2() throws JaxmppException, InterruptedException {
		Room room1 = muc1Module.getRoom(roomJID);
		muc1Module.leave(room1);

		receivedMessages2 = new ArrayList<>();
		MucModule.MucMessageReceivedHandler handler = (sessionObject, message, room, nickname, timestamp) -> {
			try {
				receivedMessages2.add(new Item1(nickname, timestamp, message.getBody(), message.getId()));
				if (receivedMessages2.size() == (sentMessages.size() + 1)) {
					mutex.notify("messages:received:2");
				}
			} catch (XMLException ex) {
				Assert.assertTrue(false);
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, handler);

		joinAs(user1Jaxmpp, roomJID, "user1", ":2");

		mutex.waitFor(20*1000,"messages:received:2");

		user1Jaxmpp.getEventBus().remove(handler);
		Assert.assertTrue(mutex.isItemNotified("messages:received:2"));

		Assert.assertEquals(receivedMessages2.stream().filter(item -> item.body != null).collect(Collectors.toList()), sentMessages);

		Assert.assertEquals(receivedMessages2, receivedMessages1);
	}

	@Test(dependsOnMethods = {"testHistoryFull2"})
	public void testHistorySince() throws JaxmppException, InterruptedException {
		Room room1 = muc1Module.getRoom(roomJID);
		muc1Module.leave(room1);


		receivedMessages3 = new ArrayList<>();
		MucModule.MucMessageReceivedHandler handler = (sessionObject, message, room, nickname, timestamp) -> {
			try {
				receivedMessages3.add(new Item1(nickname, timestamp, message.getBody(), message.getId()));
				if (receivedMessages3.size() == (receivedMessages1.size()/2 + 1)) {
					mutex.notify("messages:received:3");
				}
			} catch (XMLException ex) {
				Assert.assertTrue(false);
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, handler);

		room1.setLastMessageDate(receivedMessages1.stream().skip(receivedMessages1.size()/2).findFirst().get().ts);
		rejoinAs(user1Jaxmpp, room1, ":3");
		
		mutex.waitFor(20*1000,"messages:received:3");

		user1Jaxmpp.getEventBus().remove(handler);
		Assert.assertTrue(mutex.isItemNotified("messages:received:3"));

		Assert.assertEquals(receivedMessages3.stream().filter(item -> item.body == null).findFirst().get(), receivedMessages3.stream().filter(item -> item.body == null).findFirst().get());

		Assert.assertEquals(receivedMessages3.stream().filter(item -> item.body != null).collect(Collectors.toList()), receivedMessages1.stream().filter(item -> item.body != null).skip(receivedMessages1.size()/2).collect(Collectors.toList()));
	}

	@Test(dependsOnMethods = {"testHistorySince"})
	public void testHistorySinceFail() throws JaxmppException, InterruptedException {
		Room room1 = muc1Module.getRoom(roomJID);
		muc1Module.leave(room1);

		Thread.sleep(1000);

		String expectedEvent = "notJoinAs:" + room1.getNickname() + ":4";

		final Mutex mutex = new Mutex();

		final MucModule.YouJoinedHandler handlerJoined = new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("resp", "joinAs:" + asNickname + ":4");
			}
		};
		final MucModule.PresenceErrorHandler handlerError = new MucModule.PresenceErrorHandler() {
			@Override
			public void onPresenceError(SessionObject sessionObject, Room room, Presence presence, String asNickname) {
				mutex.notify("resp", "notJoinAs:" + asNickname + ":4");
			}
		};

		final tigase.jaxmpp.core.client.eventbus.EventListener listener = new EventListener() {
			@Override
			public void onEvent(Event<? extends EventHandler> event) {
			}
		};
		try {
			user1Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
			user1Jaxmpp.getEventBus().addHandler(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
			user1Jaxmpp.getEventBus().addListener(listener);

			MucModule mucModule = user1Jaxmpp.getModule(MucModule.class);
			Field f = MucModule.class.getDeclaredField("roomsManager");
			f.setAccessible(true);
			((AbstractRoomsManager) f.get(mucModule)).register(room1);

			Presence presence = Presence.create();
			presence.setTo(JID.jidInstance(room1.getRoomJid(), room1.getNickname()));
			final Element x = ElementFactory.create("x", null, "http://jabber.org/protocol/muc");
			presence.addChild(x);

			DateTimeFormat dtf = new DateTimeFormat();
			Element history = ElementFactory.create("history", null, null);
			String since = dtf.format(room1.getLastMessageDate());
			int idx = since.lastIndexOf(':');
			since = since.substring(0, idx) + ".150Z";
			history.setAttribute("since",  since);
			x.addChild(history);

			Method m = Room.class.getDeclaredMethod("setState", Room.State.class);
			m.setAccessible(true);
			m.invoke(room1, Room.State.requested);
			user1Jaxmpp.getContext().getWriter().write(presence);

			mutex.waitFor(1000 * 20, "resp");

			Assert.assertTrue(mutex.isItemNotified(expectedEvent),
							  "Expected event '" + expectedEvent + "' not received.");
		} catch (Exception e) {
			fail(e);
			e.printStackTrace();
		} finally {
			user1Jaxmpp.getEventBus().remove(listener);
			user1Jaxmpp.getEventBus().remove(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
			user1Jaxmpp.getEventBus().remove(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
		}
	}

	protected List<Item> sendRandomMessages(int messages) throws Exception {
		Random random = new Random();
		List<Item> sentMessages = new ArrayList<Item>();
		for (int i = 0; i < messages; i++) {
			int j = Math.abs(random.nextInt()) % mucModules.size();
			Item item = new Item("user" + (j + 1), new Date(), "Message " + UUID.randomUUID().toString());
			mucModules.get(j).getRoom(roomJID).sendMessage(item.body);

			sentMessages.add(item);

			Thread.sleep(1500);
		}

		return Collections.unmodifiableList(sentMessages);
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

		Room join = muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user1");
		user1Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
					@Override
					public void onYouJoined(SessionObject sessionObject, final Room room, final String asXNickname) {
						mutex.notify("joinAs:user1");
					}
				});

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

		joinAs(user2Jaxmpp, roomJID, "user2", "");
	}

	private void joinAs(final Jaxmpp jaxmpp, final BareJID roomJID, final String nick, String suffix)
			throws InterruptedException {
		String expectedEvent = "joinAs:" + nick + suffix;

		final Mutex mutex = new Mutex();

		final MucModule.YouJoinedHandler handlerJoined = new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("resp", "joinAs:" + asNickname + suffix);
			}
		};
		final MucModule.PresenceErrorHandler handlerError = new MucModule.PresenceErrorHandler() {
			@Override
			public void onPresenceError(SessionObject sessionObject, Room room, Presence presence, String asNickname) {
				mutex.notify("resp", "notJoinAs:" + asNickname + suffix);
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

	private void rejoinAs(final Jaxmpp jaxmpp, final Room room, String suffix)
			throws InterruptedException {
		String expectedEvent = "joinAs:" + room.getNickname() + suffix;

		final Mutex mutex = new Mutex();

		final MucModule.YouJoinedHandler handlerJoined = new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("resp", "joinAs:" + asNickname + suffix);
			}
		};
		final MucModule.PresenceErrorHandler handlerError = new MucModule.PresenceErrorHandler() {
			@Override
			public void onPresenceError(SessionObject sessionObject, Room room, Presence presence, String asNickname) {
				mutex.notify("resp", "notJoinAs:" + asNickname + suffix);
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

			MucModule mucModule = jaxmpp.getModule(MucModule.class);
			Field f = MucModule.class.getDeclaredField("roomsManager");
			f.setAccessible(true);
			((AbstractRoomsManager) f.get(mucModule)).register(room);

			room.rejoin();

			mutex.waitFor(1000 * 20, "resp");

			Assert.assertTrue(mutex.isItemNotified(expectedEvent),
							  "Expected event '" + expectedEvent + "' not received.");
		} catch (Exception e) {
			fail(e);
			e.printStackTrace();
		} finally {
			jaxmpp.getEventBus().remove(listener);
			jaxmpp.getEventBus().remove(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
			jaxmpp.getEventBus().remove(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
		}
	}

	private class Item {

		public final String body;
		public final String msgId;
		public final String nickname;
		public final Date ts;

		private Item(String nickname, Date ts, String body) {
			this(nickname, ts, body, null);
		}

		private Item(String nickname, Date ts, String body, String msgId) {
			this.nickname = nickname;
			this.ts = ts;
			this.body = body;
			this.msgId = msgId;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Item) {
				Item o = (Item) obj;
				return body.equals(o.body) && nickname.equals(o.nickname) &&
						(Math.abs(ts.getTime() - o.ts.getTime()) / 1000) == 0;
			}
			return false;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[nickname=" + nickname + ",ts=" + ts.getTime() + ",body=" + body + ",msgId=" + msgId + "]";
		}
	}

	private class Item1 extends Item {

		private Item1(String nickname, Date ts, String body) {
			super(nickname, ts, body);
		}

		private Item1(String nickname, Date ts, String body, String id) {
			super(nickname, ts, body, id);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Item1) {
				Item o = (Item) obj;
				return (body == o.body || body.equals(o.body)) && nickname.equals(o.nickname) &&
						ts.getTime() == o.ts.getTime();
			}
			return super.equals(obj);
		}
	}
}
