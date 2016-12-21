package tigase.tests.muc;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
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
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.*;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 20.12.2016.
 */
public class TestMessageArchiveManagement
		extends AbstractTest {

	final Mutex mutex = new Mutex();

	private BareJID user1Jid;
	private Jaxmpp user1Jaxmpp;
	private MucModule muc1Module;
	private BareJID user2Jid;
	private Jaxmpp user2Jaxmpp;
	private MucModule muc2Module;
	private BareJID user3Jid;
	private Jaxmpp user3Jaxmpp;
	private MucModule muc3Module;

	private BareJID roomJID;

	private List<MucModule> mucModules;
	private List<Item> sentMessages;

	@BeforeClass
	@Override
	protected void setUp() throws Exception {
		super.setUp();

		user1Jid = createUserAccount("user1");
		user1Jaxmpp = createJaxmpp("user1", user1Jid);
		user2Jid = createUserAccount("user2");
		user2Jaxmpp = createJaxmpp("user2", user2Jid);
		user3Jid = createUserAccount("user3");
		user3Jaxmpp = createJaxmpp("user3", user3Jid);

		user1Jaxmpp.login(true);
		user2Jaxmpp.login(true);
		user3Jaxmpp.login(true);

		muc1Module = user1Jaxmpp.getModule(MucModule.class);
		muc2Module = user2Jaxmpp.getModule(MucModule.class);
		muc3Module = user3Jaxmpp.getModule(MucModule.class);
		mucModules = Arrays.asList(muc1Module, muc2Module, muc3Module);

		roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1Jid.getDomain());

		joinAll();
		sentMessages = sendRandomMessages(20);
	}

	@Override
	public Jaxmpp createJaxmpp(String logPrefix, BareJID user1JID) {
		Jaxmpp jaxmpp = super.createJaxmpp(logPrefix, user1JID);

		jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());

		return jaxmpp;
	}

	@AfterClass
	public void cleanUpTest() throws JaxmppException, InterruptedException {
		tearDownUser(user3Jaxmpp);
		tearDownUser(user2Jaxmpp);
		tearDownUser(user1Jaxmpp);
	}

	private void tearDownUser(Jaxmpp user) throws JaxmppException, InterruptedException {
		if (null != user) {
			if (!user.isConnected()) {
				user.login(true);
			}
			removeUserAccount(user);
		}
	}

	@Test
	public void test_MAM_retrieveAll() throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = null;
		assertRetrievedMessages(mutex, roomJID, sentMessages, user1Jaxmpp, query, rsm, (complete, rsm1) -> {
			assertEquals(true, complete);
			assertEquals(sentMessages.size(), rsm1.getCount().intValue());
			assertEquals(0, rsm1.getIndex().intValue());
		});
	}

	@Test(dependsOnMethods = {"test_MAM_retrieveAll"})
	public void test_MAM_retrieveBetween() throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		query.setStart(sentMessages.get(2).ts);
		query.setEnd(sentMessages.get(sentMessages.size() - 3).ts);

		RSM rsm = null;
		List<Item> expMessages = sentMessages.subList(2, (sentMessages.size() - 3));
		assertRetrievedMessages(mutex, roomJID, expMessages, user1Jaxmpp, query, rsm, (complete, rsm1) -> {
			assertEquals(true, complete);
			assertEquals(expMessages.size(), rsm1.getCount().intValue());
			assertEquals(0, rsm1.getIndex().intValue());
		});
	}

	@Test(dependsOnMethods = {"test_MAM_retrieveBetween"})
	public void test_MAM_retrieveAllWithPagination() throws Exception {
		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		RSM rsm = new RSM();
		rsm.setMax(sentMessages.size() / 2);

		List<Item> expMessages1 = sentMessages.subList(0, sentMessages.size() / 2);
		List<Item> recvMessages1 = assertRetrievedMessages(mutex, roomJID, expMessages1, user1Jaxmpp, query, rsm,
														   (complete, rsm1) -> {
															   assertEquals(false, complete);
															   assertEquals(sentMessages.size(),
																			rsm1.getCount().intValue());
															   assertEquals(0, rsm1.getIndex().intValue());
														   });

		rsm.setAfter(recvMessages1.get(expMessages1.size() - 1).msgId);
		List<Item> expMessages2 = sentMessages.subList(sentMessages.size() / 2, ((int) (sentMessages.size() / 2)) * 2);
		assertRetrievedMessages(mutex, roomJID, expMessages2, user1Jaxmpp, query, rsm, (complete, rsm1) -> {
			assertEquals(sentMessages.size() % 2 == 0, complete);
			assertEquals(sentMessages.size(), rsm1.getCount().intValue());
			assertEquals(expMessages1.size(), rsm1.getIndex().intValue());
		});
	}

	@Test(dependsOnMethods = {"test_MAM_retrieveAllWithPagination"})
	public void test_MAM_checkNonJoined() throws Exception {
		String queryId = UUID.randomUUID().toString();
		muc1Module.leave(muc1Module.getRoom(roomJID));
		Thread.sleep(500);
		user1Jaxmpp.getModule(MessageArchiveManagementModule.class)
				.queryItems(new MessageArchiveManagementModule.Query(), JID.jidInstance(roomJID), null,
							queryId, null, new MessageArchiveManagementModule.ResultCallback() {
							@Override
							public void onSuccess(String queryid, boolean complete, RSM rsm) throws JaxmppException {
								assertTrue(false);
							}

							@Override
							public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									throws JaxmppException {
								mutex.notify("mam:error:" + queryId + ":error=" + error.name());
							}

							@Override
							public void onTimeout() throws JaxmppException {
								assertTrue(false);
							}
						});

		String id = "mam:error:" + queryId + ":error=" + XMPPException.ErrorCondition.forbidden.name();
		mutex.waitFor(30 * 1000, id);

		assertTrue(mutex.isItemNotified(id));
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

	private List<Item> assertRetrievedMessages(Mutex mutex, BareJID roomJID, List<Item> expMessages, Jaxmpp user1Jaxmpp,
											   MessageArchiveManagementModule.Query query, RSM rsm, Verifier verifier)
			throws Exception {
		List<Item> receivedMessages = new ArrayList<>();
		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = (String queryid, String messageId, Date timestamp, Message message) -> {
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
									verifier.check(complete, rsm);
									mutex.notify("mam:success:" + queryid + ":count=" + rsm.getCount());
								}

								@Override
								public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										throws JaxmppException {

								}

								@Override
								public void onTimeout() throws JaxmppException {

								}
							});
		mutex.waitFor(30 * 1000, "mam:success:" + queryId + ":count=" + expMessages.size());

		Thread.sleep(500);

		assertEquals(expMessages.size(), receivedMessages.size());
		expMessages.forEach(sent -> {
			Item recv = receivedMessages.stream().filter(sent::equals).findFirst().get();
			assertNotNull(recv);
		});

		user1Jaxmpp.getEventBus()
				.remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		return receivedMessages;
	}

	protected void joinAll() throws Exception {
		user2Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
					@Override
					public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
						mutex.notify("2:joinAs:" + asNickname);
					}
				});
		user3Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
					@Override
					public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
						mutex.notify("3:joinAs:" + asNickname);
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

		joinAs(user2Jaxmpp, roomJID, "user2", "joinAs:user2");
		joinAs(user3Jaxmpp, roomJID, "user3", "joinAs:user3");
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

		final EventListener listener = new EventListener() {
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

	private interface Verifier {

		void check(boolean complete, RSM rsm);

	}

	private class Item {

		public final String msgId;
		public final String nickname;
		public final String body;
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
				return body.equals(o.body) && nickname.equals(o.nickname) && ((ts.getTime()/1000) == (o.ts.getTime()/1000));
			}
			return false;
		}
	}
}
