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
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.BooleanField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.TextSingleField;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.Collection;
import java.util.Optional;

import static org.testng.Assert.assertTrue;

public class TestOffline extends AbstractTest {

	final Mutex mutex = new Mutex();
	final MutableObject<JabberDataElement> roomConfig = new MutableObject<JabberDataElement>();
	private MucModule muc1Module;
	private MucModule muc2Module;
	private MucModule muc3Module;
	private BareJID roomJID;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;
	private Account user3;
	private Jaxmpp user3Jaxmpp;
	private String user2Nickname;

	@Test(groups = {"Multi User Chat"}, description = "#8660: Delivery presence from offline user")
	public void testOfflineUserSendsMessage() throws JaxmppException, InterruptedException {
		user1Jaxmpp.getEventBus()
				.addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class,
							(sessionObject, message, room, nickname, timestamp) -> {
								try {
									mutex.notify("recv1:" + message.getBody());
								} catch (XMLException e) {
									Assert.fail(e.getMessage());
								}
							});

		final String mid = nextRnd();
		Element msg = ElementBuilder.create("message")
				.setAttribute("type", "groupchat")
				.setAttribute("to", roomJID.toString())
				.child("body")
				.setValue("test-" + mid)
				.getElement();

		user2Jaxmpp.send(Stanza.create(msg));
		mutex.waitFor(20_000, "recv1:test-" + mid);
		assertTrue(mutex.isItemNotified("recv1:test-" + mid),
						  "User1 did not received message from offline user");
	}

	@Test(groups = {"Multi User Chat"}, description = "#8660: Delivery presence from offline user")
	public void testOfflineUsersPresence() throws Exception {
		user3Jaxmpp.getEventBus()
				.addHandler(MucModule.OccupantComesHandler.OccupantComesEvent.class,
							(sessionObject, room, occupant, nickname) -> {
								TestLogger.log("Occupant comes: " + nickname);
								mutex.notify("OccupantComes:" + nickname);
							});

		muc3Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user3");
		mutex.waitFor(1000 * 20, "3:joinAs:user3", "OccupantComes:user1", "OccupantComes:" + user2Nickname);

		assertTrue(mutex.isItemNotified("3:joinAs:user3"), "User3 isn't in room!");
		assertTrue(mutex.isItemNotified("OccupantComes:user1"), "Expected user1 in room.");
		assertTrue(mutex.isItemNotified("OccupantComes:" + user2Nickname),
						  "Expected offline user 'user2' (nickname=" + user2Nickname + ") in room.");
	}

	@Test(groups = {"Multi User Chat"}, description = "#8660: Presence delivery from offline users (persistence change)")
	public void testOfflineUsersPresence1() throws Exception {
		Thread.sleep(100);
		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2Nickname), Presence.Show.xa);

		final Mutex mutex = new Mutex();
		user2Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("user2:room:joined");
			}
		});
		Room room = muc2Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user2Nickname);
		mutex.waitFor(10 * 1000, "user2:room:joined");
		assertTrue(mutex.isItemNotified("user2:room:joined"));
		Thread.sleep(100);

		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2Nickname), Presence.Show.online);

		removePersistentMember(user2Jaxmpp);
		Thread.sleep(100);

		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2Nickname), Presence.Show.online);

		muc2Module.leave(room);
		Thread.sleep(300);

		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2Nickname), Presence.Show.offline);
	}

	private static Presence.Show getPresenceShow(MucModule muc1Module, BareJID roomJID, String nickname) {
		return Optional.ofNullable(muc1Module.getRoom(roomJID).getPresences().get(nickname)).map(p -> {
			try {
				return p.getPresence().getShow();
			} catch (XMLException e) {
				return Presence.Show.offline;
			}
		}).orElse(Presence.Show.offline);
	}

	@BeforeTest
	void prepareMucRoom() throws Exception {
		mutex.clear();
		this.user1 = createAccount().setLogPrefix("user1").build();
		this.user2 = createAccount().setLogPrefix("user2").build();
		this.user3 = createAccount().setLogPrefix("user3").build();
		this.user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		this.user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();
		this.user3Jaxmpp = user3.createJaxmpp().setConnected(true).build();

		this.user2Nickname = user2.getJid().getLocalpart().substring(0,1).toUpperCase() + user2.getJid().getLocalpart().substring(1);

		this.roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1.getJid().getDomain());

		this.muc1Module = user1Jaxmpp.getModule(MucModule.class);
		this.muc2Module = user2Jaxmpp.getModule(MucModule.class);
		this.muc3Module = user3Jaxmpp.getModule(MucModule.class);

		user1Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class,
							(sessionObject, room, asNickname) -> mutex.notify("1:joinAs:" + asNickname));
		user2Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class,
							(sessionObject, room, asNickname) -> mutex.notify("2:joinAs:" + asNickname));
		user3Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class,
							(sessionObject, room, asNickname) -> mutex.notify("3:joinAs:" + asNickname));

		muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user1");

		mutex.waitFor(1000 * 20, "1:joinAs:user1");
		assertTrue(mutex.isItemNotified("1:joinAs:user1"));

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
		assertTrue(mutex.isItemNotified("getConfig:success"));

		Thread.sleep(1000);

		muc1Module.setRoomConfiguration(muc1Module.getRoom(roomJID), roomConfig.getValue(), new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				TestLogger.log("Error on set config: " + errorCondition);
				mutex.notify("setConfig", "setConfig:error");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("setConfig", "setConfig:success");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("setConfig", "setConfig:timeout");
			}
		});
		mutex.waitFor(1000 * 20, "setConfig");
		assertTrue(mutex.isItemNotified("setConfig:success"));
		Thread.sleep(1000);

		final Mutex mutex = new Mutex();
		user2Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("user2:room:joined");
			}
		});
		Room room = muc2Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user2Nickname);
		mutex.waitFor(10 * 1000, "user2:room:joined");
		assertTrue(mutex.isItemNotified("user2:room:joined"));
		Thread.sleep(100);

		addPersistentMember(user2Jaxmpp);
		
		muc2Module.leave(room);
		Thread.sleep(100);
	}

	@AfterTest
	public void destroyMucRoom() throws JaxmppException, InterruptedException {
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setTo(JID.jidInstance(roomJID));

		final Mutex mutex = new Mutex();
		Element query = ElementFactory.create("query", null, "http://jabber.org/protocol/muc#owner");
		query.addChild(ElementFactory.create("destroy"));
		iq.addChild(query);
		user1Jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("room:destroyed:error:" + error,"room:destroyed");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("room:destroyed:success", "room:destroyed");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("room:destroyed:timeout", "room:destroyed");
			}
		});
		mutex.waitFor(10 * 1000, "room:destroyed");
		assertTrue(mutex.isItemNotified("room:destroyed:success"));
	}

	private void addPersistentMember(Jaxmpp jaxmpp) throws Exception {
		final Mutex mutex = new Mutex();
		jaxmpp.getModule(DiscoveryModule.class).getInfo(JID.jidInstance(roomJID.getDomain()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
			@Override
			protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
										  Collection<String> features) throws XMLException {
				features.forEach(feature -> mutex.notify("disco:features:" + feature));
				mutex.notify("disco:features:success", "disco:features");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:features:error:" + error, "disco:features");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:features:timeout", "disco:features");
			}
		});
		mutex.waitFor(10 * 1000, "disco:features");
		assertTrue(mutex.isItemNotified("disco:features:success"));
		assertTrue(mutex.isItemNotified("disco:features:http://tigase.org/protocol/muc#offline"));

		IQ iq = IQ.create();
		iq.setType(StanzaType.get);
		iq.setTo(JID.jidInstance(roomJID));

		Element query = ElementFactory.create("query", null, "jabber:iq:register");
		iq.addChild(query);
		jaxmpp.getContext().getWriter().write(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("register:form:retrieve:error:" + error, "register:form:retrieve");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				JabberDataElement form = new JabberDataElement(responseStanza.getFirstChild("query").getFirstChild("x"));
				mutex.notify("register:form:retrieve:success", "register:form:retrieve");
				TextSingleField nicknameField = form.getField("muc#register_roomnick");
				nicknameField.setFieldValue(user2Nickname);
				BooleanField offlineField = form.getField("{http://tigase.org/protocol/muc}offline");
				offlineField.setFieldValue(true);

				IQ iqSet = IQ.create();
				iqSet.setType(StanzaType.set);
				iqSet.setTo(JID.jidInstance(roomJID));

				Element query = ElementFactory.create("query", null, "jabber:iq:register");
				iqSet.addChild(query);
				query.addChild(form.createSubmitableElement(XDataType.submit));

				jaxmpp.getContext().getWriter().write(iqSet, new AsyncCallback() {
					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("register:form:submit:error:" + error, "register:form:submit");
					}

					@Override
					public void onSuccess(Stanza responseStanza) throws JaxmppException {
						mutex.notify("register:form:submit:success", "register:form:submit");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("register:form:submit:timeout", "register:form:submit");
					}
				});
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("register:form:retrieve:timeout", "register:form:retrieve");
			}
		});
		mutex.waitFor(10 * 1000, "register:form:retrieve");
		assertTrue(mutex.isItemNotified("register:form:retrieve:success"));

		mutex.waitFor(10 * 1000, "register:form:submit");
		assertTrue(mutex.isItemNotified("register:form:submit:success"));
	}

	private void removePersistentMember(Jaxmpp jaxmpp) throws Exception {
		final Mutex mutex = new Mutex();

		IQ iq = IQ.create();
		iq.setType(StanzaType.get);
		iq.setTo(JID.jidInstance(roomJID));

		Element query = ElementFactory.create("query", null, "jabber:iq:register");
		iq.addChild(query);
		jaxmpp.getContext().getWriter().write(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("register:form:retrieve:error:" + error, "register:form:retrieve");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				JabberDataElement form = new JabberDataElement(responseStanza.getFirstChild("query").getFirstChild("x"));
				mutex.notify("register:form:retrieve:success", "register:form:retrieve");
				TextSingleField nicknameField = form.getField("muc#register_roomnick");
				nicknameField.setFieldValue(user2Nickname);
				BooleanField offlineField = form.getField("{http://tigase.org/protocol/muc}offline");
				offlineField.setFieldValue(false);

				IQ iqSet = IQ.create();
				iqSet.setType(StanzaType.set);
				iqSet.setTo(JID.jidInstance(roomJID));

				Element query = ElementFactory.create("query", null, "jabber:iq:register");
				iqSet.addChild(query);
				query.addChild(form.createSubmitableElement(XDataType.submit));

				jaxmpp.getContext().getWriter().write(iqSet, new AsyncCallback() {
					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("register:form:submit:error:" + error, "register:form:submit");
					}

					@Override
					public void onSuccess(Stanza responseStanza) throws JaxmppException {
						mutex.notify("register:form:submit:success", "register:form:submit");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("register:form:submit:timeout", "register:form:submit");
					}
				});
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("register:form:retrieve:timeout", "register:form:retrieve");
			}
		});
		mutex.waitFor(10 * 1000, "register:form:retrieve");
		assertTrue(mutex.isItemNotified("register:form:retrieve:success"));

		mutex.waitFor(10 * 1000, "register:form:submit");
		assertTrue(mutex.isItemNotified("register:form:submit:success"));
	}


}
