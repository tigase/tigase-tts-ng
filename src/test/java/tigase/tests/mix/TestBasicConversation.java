package tigase.tests.mix;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.Date;

public class TestBasicConversation
		extends AbstractTest {

	private String channelName;

	private Jaxmpp jaxmpp1;
	private Jaxmpp jaxmpp2;
	private Jaxmpp jaxmpp3;

	private JID mixJID;

	@BeforeClass
	public void setUp() throws Exception {
		this.jaxmpp1 = createAccount().setLogPrefix("user1").build().createJaxmpp().setConnected(true).build();
		this.jaxmpp2 = createAccount().setLogPrefix("user2").build().createJaxmpp().setConnected(true).build();
		this.jaxmpp3 = createAccount().setLogPrefix("user3").build().createJaxmpp().setConnected(true).build();

		this.mixJID = TestDiscovery.findMIXComponent(jaxmpp1);

		this.channelName = "channel-" + nextRnd();
	}

	@Test
	public void testCreateChannel() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "create-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("create")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", channelName);

		Response result = sendRequest(jaxmpp1, (IQ) Stanza.create(request.getElement()));
		AssertJUnit.assertTrue("Cannot create channel!", result instanceof Response.Success);
	}

	private IQ createJoinRequest(Jaxmpp jaxmpp, String nickname) throws Exception {
		ElementBuilder joinRequest = ElementBuilder.create("iq")
				.setAttribute("id", "join-" + jaxmpp.getSessionObject().getUserBareJid().getLocalpart())
				.setAttribute("type", "set")
				.setAttribute("to", jaxmpp.getSessionObject().getUserBareJid().toString())
				.child("client-join")
				.setXMLNS("urn:xmpp:mix:pam:2")
				.setAttribute("channel", channelName + "@" + mixJID)
				.child("join")
				.setXMLNS("urn:xmpp:mix:core:1")
				.child("subscribe")
				.setXMLNS("urn:xmpp:mix:nodes:messages")
				.up()
				.child("subscribe")
				.setXMLNS("urn:xmpp:mix:nodes:presence")
				.up()
				.child("subscribe")
				.setXMLNS("urn:xmpp:mix:nodes:participants")
				.up()
				.child("subscribe")
				.setXMLNS("urn:xmpp:mix:nodes:info")
				.up()
				.child("nick")
				.setValue(nickname);

		return (IQ) Stanza.create(joinRequest.getElement());
	}

	private static class MessageHandler
			implements MessageModule.MessageReceivedHandler {

		private final Mutex mutex;

		public MessageHandler(Mutex mutex) {
			this.mutex = mutex;
		}

		@Override
		public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
			try {
				Element mix = stanza.getChildrenNS("mix", "urn:xmpp:mix:core:1");
				if (mix != null) {
					String user = stanza.getTo().getLocalpart();
					String sender = mix.getFirstChild("nick").getValue();
					mutex.notify(user + ":" + sender + ":" + stanza.getBody());
				}
			} catch (Exception e) {
				fail(e);
			}
		}
	}

	private static class ParticipantsHandler
			implements PubSubModule.NotificationReceivedHandler {

		private final Mutex mutex;

		public ParticipantsHandler(Mutex mutex) {
			this.mutex = mutex;
		}

		@Override
		public void onNotificationReceived(SessionObject sessionObject, Message message, JID pubSubJID, String nodeName,
										   String itemId, Element payload, Date delayTime, String itemType) {
			if (nodeName.equals("urn:xmpp:mix:nodes:participants")) {
				try {
					String user = message.getTo().getLocalpart();
					mutex.notify(user + ":" + payload.getFirstChild("nick").getValue());
				} catch (Exception e) {
					fail(e);
				}
			}
		}
	}

	@Test(dependsOnMethods = {"testCreateChannel"})
	public void testJoinUser1() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsHandler handler = new ParticipantsHandler(mutex);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);

			Response result = sendRequest(jaxmpp1, createJoinRequest(jaxmpp1, "user1"));
			AssertJUnit.assertTrue("User1 cannot join to Channel", result instanceof Response.Success);

			mutex.waitFor(30000, jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user1");

			AssertJUnit.assertTrue("User1 didn't get participant user1", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user1"));
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testJoinUser1"})
	public void testJoinUser2() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsHandler handler = new ParticipantsHandler(mutex);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			Response result = sendRequest(jaxmpp2, createJoinRequest(jaxmpp2, "user2"));
			AssertJUnit.assertTrue("User2 cannot join to Channel", result instanceof Response.Success);

			mutex.waitFor(30000, jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user2",
						  jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user1",
						  jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2");

			AssertJUnit.assertTrue("User2 didn't get participant user2", mutex.isItemNotified(
					jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user2"));

			AssertJUnit.assertTrue("User2 didn't get participant user1", mutex.isItemNotified(
					jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user1"));
			AssertJUnit.assertTrue("User1 didn't get participant user2", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2"));

		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testJoinUser2"})
	public void testJoinUser3() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsHandler handler = new ParticipantsHandler(mutex);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			Response result = sendRequest(jaxmpp3, createJoinRequest(jaxmpp3, "user3"));
			AssertJUnit.assertTrue("User3 cannot join to Channel", result instanceof Response.Success);

			mutex.waitFor(30000, jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user3",
						  jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user2",
						  jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user1",

						  jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user3",
						  jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user3"

			);
			AssertJUnit.assertTrue("User3 didn't get participant user3", mutex.isItemNotified(
					jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user3"));
			AssertJUnit.assertTrue("User3 didn't get participant user2", mutex.isItemNotified(
					jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user2"));
			AssertJUnit.assertTrue("User3 didn't get participant user1", mutex.isItemNotified(
					jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user1"));

			AssertJUnit.assertTrue("User2 didn't get participant user3", mutex.isItemNotified(
					jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user3"));
			AssertJUnit.assertTrue("User1 didn't get participant user3", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user3"));
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testJoinUser3"})
	public void testPublishMessageByUser1() throws Exception {
		final Mutex mutex = new Mutex();
		final MessageHandler handler = new MessageHandler(mutex);
		final String msg = "msg-" + nextRnd();

		try {
			jaxmpp1.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			jaxmpp2.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			jaxmpp3.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

			ElementBuilder msgBuilder = ElementBuilder.create("message")
					.setAttribute("type", "groupchat")
					.setAttribute("id", "msg-1")
					.setAttribute("to", channelName + "@" + mixJID)
					.child("body")
					.setValue(msg);

			jaxmpp1.send(Stanza.create(msgBuilder.getElement()));

			mutex.waitFor(30000, jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user1:" + msg,
						  jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user1:" + msg,
						  jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user1:" + msg);

			AssertJUnit.assertTrue("User1 didn't get msg from user1", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user1:" + msg));
			AssertJUnit.assertTrue("User2 didn't get msg from user1", mutex.isItemNotified(
					jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user1:" + msg));
			AssertJUnit.assertTrue("User3 didn't get msg from user1", mutex.isItemNotified(
					jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user1:" + msg));
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testPublishMessageByUser1"})
	public void testPublishMessageByUser2() throws Exception {
		final Mutex mutex = new Mutex();
		final MessageHandler handler = new MessageHandler(mutex);
		final String msg = "msg-" + nextRnd();

		try {
			jaxmpp1.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			jaxmpp2.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			jaxmpp3.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

			ElementBuilder msgBuilder = ElementBuilder.create("message")
					.setAttribute("type", "groupchat")
					.setAttribute("id", "msg-2")
					.setAttribute("to", channelName + "@" + mixJID)
					.child("body")
					.setValue(msg);

			jaxmpp2.send(Stanza.create(msgBuilder.getElement()));

			mutex.waitFor(30000, jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2:" + msg,
						  jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user2:" + msg,
						  jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user2:" + msg);

			AssertJUnit.assertTrue("User1 didn't get msg from user2", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2:" + msg));
			AssertJUnit.assertTrue("User2 didn't get msg from user2", mutex.isItemNotified(
					jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user2:" + msg));
			AssertJUnit.assertTrue("User3 didn't get msg from user2", mutex.isItemNotified(
					jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user2:" + msg));
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testPublishMessageByUser2"})
	public void testDestroyChannel() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "create-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("destroy")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", channelName);

		Response result = sendRequest(jaxmpp1, (IQ) Stanza.create(request.getElement()));
		AssertJUnit.assertTrue("Cannot destroy channel!", result instanceof Response.Success);
	}
}
