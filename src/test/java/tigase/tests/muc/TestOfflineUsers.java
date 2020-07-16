package tigase.tests.muc;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
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

import java.util.Optional;

import static org.testng.Assert.assertTrue;

public class TestOfflineUsers
		extends AbstractTest {

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
		Assert.assertTrue(mutex.isItemNotified("recv1:test-" + mid),
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
		mutex.waitFor(1000 * 20, "3:joinAs:user3", "OccupantComes:user1", "OccupantComes:" + user2.getJid().toString());

		Assert.assertTrue(mutex.isItemNotified("3:joinAs:user3"), "User3 isn't in room!");
		Assert.assertTrue(mutex.isItemNotified("OccupantComes:user1"), "Expected user1 in room.");
		Assert.assertTrue(mutex.isItemNotified("OccupantComes:" + user2.getJid().toString()),
						  "Expected offline user 'user2' (nickname=" + user2.getJid().toString() + ") in room.");
	}

	@Test(groups = {"Multi User Chat"}, description = "#8660: Presence delivery from offline users (persistence change)")
	public void testOfflineUsersPresence1() throws Exception {
		Thread.sleep(200);
		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.xa);

		final Mutex mutex = new Mutex();
		user2Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("user2:room:joined");
			}
		});
		Room room = muc2Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user2.getJid().toString());
		mutex.waitFor(10 * 1000, "user2:room:joined");
		Assert.assertTrue(mutex.isItemNotified("user2:room:joined"));
		Thread.sleep(500);

		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.online);

		removePersistentMember(user2.getJid());
		Thread.sleep(500);

		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.online);

		muc2Module.leave(room);
		Thread.sleep(500);

		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.offline);

		addPersistentMember(user2.getJid());
		Thread.sleep(500);
		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.xa);

		removePersistentMember(user2.getJid());
		Thread.sleep(500);
		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.offline);
	}

	@Test(groups = {"Multi User Chat"}, description = "#8660: Presence delivery to persistent user")
	public void testPresenceDeliveryToPersistentUserOnJoin() throws Exception {
		Thread.sleep(500);
		Room muc3Room = muc3Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user3");
		Assert.assertEquals(getPresenceShow(muc1Module, roomJID, user2.getJid().toString()), Presence.Show.xa);

		final Mutex mutex = new Mutex();
		user2Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("user2:room:joined");
			}
		});
		Room room = muc2Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user2.getJid().toString());
		mutex.waitFor(10 * 1000, "user2:room:joined");
		Assert.assertTrue(mutex.isItemNotified("user2:room:joined"));
		Thread.sleep(500);

		Assert.assertEquals(getPresenceShow(muc2Module, roomJID, "user1"), Presence.Show.online);
		Assert.assertEquals(getPresenceShow(muc2Module, roomJID, "user3"), Presence.Show.online);

		muc3Module.leave(muc3Room);
		Thread.sleep(500);

		Assert.assertEquals(getPresenceShow(muc2Module, roomJID, user3.getJid().toString()), Presence.Show.offline);
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

	@BeforeMethod
	void prepareMucRoom() throws Exception {
		mutex.clear();
		this.user1 = createAccount().setLogPrefix("muc-test-user1").build();
		this.user2 = createAccount().setLogPrefix("muc-test-user2").build();
		this.user3 = createAccount().setLogPrefix("muc-test-user3").build();
		this.user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		this.user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();
		this.user3Jaxmpp = user3.createJaxmpp().setConnected(true).build();

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
		Assert.assertTrue(mutex.isItemNotified("1:joinAs:user1"));

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
		Assert.assertTrue(mutex.isItemNotified("getConfig:success"));

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
		Assert.assertTrue(mutex.isItemNotified("setConfig:success"));
		Thread.sleep(1000);

		addPersistentMember(user2.getJid());
	}

	@AfterMethod
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
	
	private void addPersistentMember(BareJID occupant) throws Exception {
		Jaxmpp jaxmppAdmin = getAdminAccount().createJaxmpp().setConnected(true).build();
		AdHocCommansModule adhoc = jaxmppAdmin.getModule(AdHocCommansModule.class);

		JabberDataElement data = new JabberDataElement(XDataType.submit);
		data.addTextSingleField("room_name", roomJID.getLocalpart());
		data.addTextSingleField("occupant_jid", occupant.toString());

		adhoc.execute(JID.jidInstance(roomJID.getDomain()), "room-occupant-persistent-add", Action.complete, data,
					  new AsyncCallback() {
						  @Override
						  public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
								  throws JaxmppException {
							  TestLogger.log("Cannot add persistent member: " + error);
							  mutex.notify("add:p_member");
						  }

						  @Override
						  public void onSuccess(Stanza responseStanza) throws JaxmppException {
							  mutex.notify("add:p_member:OK", "add:p_member");
						  }

						  @Override
						  public void onTimeout() throws JaxmppException {
							  TestLogger.log("Cannot add persistent member: timeout");
							  mutex.notify("add:p_member");
						  }
					  });

		mutex.waitFor(20_000, "add:p_member");
		Assert.assertTrue(mutex.isItemNotified("add:p_member:OK"), "Persistent member is not added!");

	}

	private void removePersistentMember(BareJID occupant) throws Exception {
		Jaxmpp jaxmppAdmin = getAdminAccount().createJaxmpp().setConnected(true).build();
		AdHocCommansModule adhoc = jaxmppAdmin.getModule(AdHocCommansModule.class);

		JabberDataElement data = new JabberDataElement(XDataType.submit);
		data.addTextSingleField("room_name", roomJID.getLocalpart());
		data.addTextSingleField("occupant_jid", occupant.toString());

		adhoc.execute(JID.jidInstance(roomJID.getDomain()), "room-occupant-persistent-remove", Action.complete, data,
					  new AsyncCallback() {
						  @Override
						  public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
								  throws JaxmppException {
							  TestLogger.log("Cannot remove persistent member: " + error);
							  mutex.notify("remove:p_member");
						  }

						  @Override
						  public void onSuccess(Stanza responseStanza) throws JaxmppException {
							  mutex.notify("remove:p_member:OK", "remove:p_member");
						  }

						  @Override
						  public void onTimeout() throws JaxmppException {
							  TestLogger.log("Cannot remove persistent member: timeout");
							  mutex.notify("remove:p_member");
						  }
					  });

		mutex.waitFor(20_000, "remove:p_member");
		Assert.assertTrue(mutex.isItemNotified("remove:p_member:OK"), "Persistent member is not removed!");

	}

}
