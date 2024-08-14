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
package tigase.tests.mix;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.*;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule.RetrieveItemsAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StreamPacket;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.JaxmppHostnameVerifier;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static tigase.jaxmpp.j2se.connectors.socket.SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY;
import static tigase.jaxmpp.j2se.connectors.socket.SocketConnector.HOSTNAME_VERIFIER_KEY;

public class TestBasicConversation
		extends AbstractTest {

	private String channelName;

	private Jaxmpp jaxmpp1;
	private Jaxmpp jaxmpp2;
	private Jaxmpp jaxmpp3;

	private Map<String, String> participants = new HashMap<>();

	private JID mixJID;

	@BeforeClass
	public void setUp() throws Exception {
		JaxmppHostnameVerifier hostnameVerifier = new JaxmppHostnameVerifier() {
			@Override
			public boolean verify(String hostname, Certificate certificate) {
				return true;
			}
		};


		this.jaxmpp1 = createAccount().setLogPrefix("user1").build().createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_DISABLED_KEY, true);
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_KEY, hostnameVerifier);
			return jaxmpp;
		}).setConnected(true).build();
		this.jaxmpp2 = createAccount().setLogPrefix("user2").build().createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_KEY, hostnameVerifier);
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_DISABLED_KEY, true);
//			jaxmpp.getConnectionConfiguration().setPort(5322);
			return jaxmpp;
		}).setConnected(true).build();
		this.jaxmpp3 = createAccount().setLogPrefix("user3").build().createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_DISABLED_KEY, true);
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_KEY, hostnameVerifier);
			return jaxmpp;
		}).setConnected(true).build();

		this.mixJID = TestDiscovery.findMIXComponent(jaxmpp1);

		this.channelName = "channel-" + nextRnd();
	}

	@Test
	public void enableMamForUser1() throws Exception {
		final Mutex mutex = new Mutex();
		jaxmpp1.getModulesManager().getModule(MessageArchiveManagementModule.class).updateSetttings(
				MessageArchiveManagementModule.DefaultValue.always, null, null, new MessageArchiveManagementModule.SettingsCallback() {
					@Override
					public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
										  List<JID> never) throws JaxmppException {
						mutex.notify("mam:enable:success", "mam:enable");
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("mam:enable:error", "mam:enable");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("mam:enable:timeout", "mam:enable");
					}
				});
		mutex.waitFor(10 * 1000, "mam:enable");
		assertTrue(mutex.isItemNotified("mam:enable:success"));
	}

	@Test(dependsOnMethods = "enableMamForUser1")
	public void testCreateChannel() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "create-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("create")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", channelName);

		Response result = sendRequest(getJaxmppAdmin(), (IQ) Stanza.create(request.getElement()));
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
				.setAttribute("node", "urn:xmpp:mix:nodes:messages")
				.up()
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:presence")
				.up()
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:participants")
				.up()
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:info")
				.up()
				.child("nick")
				.setValue(nickname);

		return (IQ) Stanza.create(joinRequest.getElement());
	}

	// swich to StanzaReceivedHandler as MessageModule was not firing those events for message of type `groupchat`.
	private static class MessageHandler
			implements Connector.StanzaReceivedHandler {

		private final Mutex mutex;

		public MessageHandler(Mutex mutex) {
			this.mutex = mutex;
		}

		public void onMessageReceived(SessionObject sessionObject, Message stanza) {
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

		@Override
		public void onStanzaReceived(SessionObject sessionObject, StreamPacket stanza) {
			if (stanza instanceof Message) {
				onMessageReceived(sessionObject, (Message) stanza);
			}
		}
	}

	private static class ParticipantsRetractHandler
			implements PubSubModule.NotificationReceivedHandler {

		private final Mutex mutex;
		private final Map<String, String> participants;

		public ParticipantsRetractHandler(Mutex mutex, Map<String, String> participants) {
			this.mutex = mutex;
			this.participants = participants;
		}

		@Override
		public void onNotificationReceived(SessionObject sessionObject, Message message, JID pubSubJID, String nodeName,
										   String itemId, Element payload, Date delayTime, String itemType) {
			if (nodeName.equals("urn:xmpp:mix:nodes:participants") && itemType.equals("retract")) {
				try {
					String user = message.getTo().getLocalpart();
					String nickname = participants.get(itemId);
					mutex.notify(user + ":" + nickname);
				} catch (Exception e) {
					fail(e);
				}
			}
		}
	}

	private static class ParticipantsHandler
			implements PubSubModule.NotificationReceivedHandler {

		private final Mutex mutex;
		private final Map<String, String> participants;

		public ParticipantsHandler(Mutex mutex, Map<String, String> participants) {
			this.mutex = mutex;
			this.participants = participants;
		}

		@Override
		public void onNotificationReceived(SessionObject sessionObject, Message message, JID pubSubJID, String nodeName,
										   String itemId, Element payload, Date delayTime, String itemType) {
			if (nodeName.equals("urn:xmpp:mix:nodes:participants")) {
				try {
					String user = message.getTo().getLocalpart();
					String nickname = payload.getFirstChild("nick").getValue();
					participants.put(itemId, nickname);
					mutex.notify(user + ":" + nickname);
				} catch (Exception e) {
					fail(e);
				}
			}
		}
	}

	@Test(dependsOnMethods = {"testCreateChannel"})
	public void testJoinUser1() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsHandler handler = new ParticipantsHandler(mutex, participants);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);

			Response result = sendRequest(jaxmpp1, createJoinRequest(jaxmpp1, "user1"));
			AssertJUnit.assertTrue("User1 cannot join to Channel", result instanceof Response.Success);

			participants.put(result.getResponse().getFirstChild("client-join").getFirstChild("join").getAttribute("id"), "user1");
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testJoinUser1"})
	public void testJoinUser2() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsHandler handler = new ParticipantsHandler(mutex, participants);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			Response result = sendRequest(jaxmpp2, createJoinRequest(jaxmpp2, "user2"));
			AssertJUnit.assertTrue("User2 cannot join to Channel", result instanceof Response.Success);

			mutex.waitFor(30000,
						  jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2");

			AssertJUnit.assertTrue("User1 didn't get participant user2", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2"));

			participants.put(result.getResponse().getFirstChild("client-join").getFirstChild("join").getAttribute("id"), "user2");
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testJoinUser2"})
	public void testJoinUser3() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsHandler handler = new ParticipantsHandler(mutex, participants);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			Response result = sendRequest(jaxmpp3, createJoinRequest(jaxmpp3, "user3"));
			AssertJUnit.assertTrue("User3 cannot join to Channel", result instanceof Response.Success);

			mutex.waitFor(30000,
						  jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user3",
						  jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user3"

			);

			AssertJUnit.assertTrue("User2 didn't get participant user3", mutex.isItemNotified(
					jaxmpp2.getSessionObject().getUserBareJid().getLocalpart() + ":user3"));
			AssertJUnit.assertTrue("User1 didn't get participant user3", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user3"));

			participants.put(result.getResponse().getFirstChild("client-join").getFirstChild("join").getAttribute("id"), "user3");
		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testJoinUser2"})
	public void testUser2JoinInUser1MAM() throws Exception {
		final Mutex mutex = new Mutex();

		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = (sessionObject, queryid, messageId, timestamp, message) -> {
			final Element event = message.getChildrenNS("event", "http://jabber.org/protocol/pubsub#event");
			List<Element> tmp = event == null ? null : event.getChildren("items");
			final Element items = tmp == null || tmp.isEmpty() ? null : tmp.get(0);
			final String nodeName = items == null ? null : items.getAttribute("node");

			if (items != null && items.getChildren() != null) {
				for (Element item : items.getChildren()) {
					final String type = item.getName();
					final String itemId = item.getAttribute("id");
//					final Element payload = item.getFirstChild();
					mutex.notify("mam:received:" + message.getFrom() + ":" + type + ":" + itemId);
				}
			}
		};
		jaxmpp1.getContext().getEventBus().addHandler(
				MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class, handler);

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		query.setStart(new Date(System.currentTimeMillis() - (60*60*1000)));
		query.setWith(JID.jidInstance(channelName, mixJID.getDomain()));
		String queryId = UUID.randomUUID().toString();
	 	jaxmpp1.getModulesManager().getModule(MessageArchiveManagementModule.class).queryItems(query, queryId, new RSM(100), new MessageArchiveManagementModule.ResultCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("mam:participants:query:error", "mam:participants:query");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("mam:participants:query:timeout", "mam:participants:query");
			}

			@Override
			public void onSuccess(String queryid, boolean complete, RSM rsm) throws JaxmppException {
				mutex.notify("mam:participants:query:success", "mam:participants:query");
			}
		});

	 	mutex.waitFor(10 * 1000, "mam:participants:query");
	 	assertTrue(mutex.isItemNotified("mam:participants:query:success"));

	 	String expItemid = null;
	 	for (Map.Entry<String,String> e :  participants.entrySet()) {
	 		if (e.getValue().equals("user2")) {
	 			expItemid = e.getKey();
	 			break;
			}
		}
	 	Thread.sleep(100);
	 	assertTrue(mutex.isItemNotified("mam:received:" + channelName + "@" + mixJID.getDomain() + ":item:" + expItemid));

	 	jaxmpp1.getContext().getEventBus().remove(handler);
	}

	@Test(dependsOnMethods = {"testJoinUser3"})
	public void testPublishMessageByUser1() throws Exception {
		final Mutex mutex = new Mutex();
		final MessageHandler handler = new MessageHandler(mutex);
		final String msg = "msg-" + nextRnd();

		try {
			jaxmpp1.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
			jaxmpp2.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
			jaxmpp3.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);

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
			jaxmpp1.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
			jaxmpp2.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
			jaxmpp3.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);

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
	public void testLeaveUser2() throws Exception {
		final Mutex mutex = new Mutex();
		final ParticipantsRetractHandler handler = new ParticipantsRetractHandler(mutex, participants);

		try {
			jaxmpp1.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp2.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);
			jaxmpp3.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);

			ElementBuilder leaveBuilder = ElementBuilder.create("iq")
					.setAttribute("id", "leave-1")
					.setAttribute("type", "set")
					.setAttribute("to", jaxmpp2.getSessionObject().getUserBareJid().toString())
					.child("client-leave")
					.setXMLNS("urn:xmpp:mix:pam:2")
					.setAttribute("channel", channelName + "@" + mixJID)
					.child("leave")
					.setXMLNS("urn:xmpp:mix:core:1");

			Response result = sendRequest(jaxmpp2, (IQ) Stanza.create(leaveBuilder.getElement()));
			AssertJUnit.assertTrue("User2 cannot leave channel", result instanceof Response.Success);

			mutex.waitFor(30000, jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2",
						  jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user2");

			participants.entrySet()
					.stream()
					.filter(e -> "user2".equals(e.getValue()))
					.findFirst()
					.ifPresent(participants::remove);

			AssertJUnit.assertTrue("User1 didn't get participant retract from user2", mutex.isItemNotified(
					jaxmpp1.getSessionObject().getUserBareJid().getLocalpart() + ":user2"));
			AssertJUnit.assertTrue("User3 didn't get participant retract from user2", mutex.isItemNotified(
					jaxmpp3.getSessionObject().getUserBareJid().getLocalpart() + ":user2"));

		} finally {
			jaxmpp1.getEventBus().remove(handler);
			jaxmpp2.getEventBus().remove(handler);
			jaxmpp3.getEventBus().remove(handler);
		}
	}

	@Test(dependsOnMethods = {"testLeaveUser2"})
	public void testPresentNodesModification() throws JaxmppException, InterruptedException {
		final BareJID channelJID = BareJID.bareJIDInstance(channelName, mixJID.getDomain());
		final Mutex mutex = new Mutex();

		final AtomicReference<Element> refPayload = new AtomicReference<>();
		final String TAG1 = "1:retrieve:config";
		getJaxmppAdmin().getModule(PubSubModule.class).retrieveItems(channelJID, "urn:xmpp:mix:nodes:config", null, null, new RetrieveItemsAsyncCallback() {
			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(TAG1 + ":timeout", TAG1);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify(TAG1 + ":error:" + errorCondition + ":" + pubSubErrorCondition, TAG1);
			}

			@Override
			protected void onRetrieve(IQ iq, String s, Collection<Item> collection) {
				refPayload.set(collection.iterator().next().getPayload());
				mutex.notify(TAG1 + ":success", TAG1);
			}
		});
		mutex.waitFor(30*1000, TAG1);
		assertTrue(mutex.isItemNotified(TAG1 + ":success"));
		Element payload = refPayload.get();
		assertNotNull(payload);
		assertEquals("x", payload.getName());
		assertEquals("jabber:x:data", payload.getXMLNS());

		payload = ElementFactory.create(payload);
		payload.setParent(null);

		for (Element field : payload.getChildren("field")) {
			if ("Nodes Present".equals(field.getAttribute("var"))) {
				Set<String> nodesPresent = field.getChildren("value").stream().map(it -> {
					try {
						return it.getValue();
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
				}).collect(Collectors.toSet());

				assertFalse(nodesPresent.contains("allowed"));
				assertFalse(nodesPresent.contains("banned"));
				field.addChild(ElementBuilder.create("value").setValue("allowed").getElement());
				field.addChild(ElementBuilder.create("value").setValue("banned").getElement());
			}
		}

		final String TAG2 = "2:publish:config";
		getJaxmppAdmin().getModule(PubSubModule.class).publishItem(channelJID, "urn:xmpp:mix:nodes:config", "2024-01-01", payload, new PubSubModule.PublishAsyncCallback() {
			@Override
			public void onPublish(String s) {
				mutex.notify(TAG2 + ":success", TAG2);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify(TAG2 + ":error:" + errorCondition + ":" + pubSubErrorCondition, TAG2);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(TAG2 + ":timeout", TAG2);
			}
		});
		mutex.waitFor(30*1000, TAG2);
		assertTrue(mutex.isItemNotified(TAG2 + ":success"));

		Thread.sleep(1000);
		
		final ArrayList<DiscoveryModule.Item> items = new ArrayList<>();
		final String TAG3 = "3:disco:nodes";
		getJaxmppAdmin().getModule(DiscoveryModule.class).getItems(JID.jidInstance(channelJID), "mix", new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String node, ArrayList<DiscoveryModule.Item> newItems) throws XMLException {
				items.addAll(newItems);
				mutex.notify(TAG3 + ":success", TAG3);
			}

			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify(TAG3 + ":error:" + errorCondition, TAG3);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(TAG3 + ":timeout", TAG3);
			}
		});
		mutex.waitFor(30*1000, TAG3);
		assertTrue(mutex.isItemNotified(TAG3 + ":success"));
		assertFalse(items.isEmpty());

		Set<String> nodes = items.stream().map(DiscoveryModule.Item::getNode).filter(Objects::nonNull).collect(Collectors.toSet());
		assertTrue(nodes.contains("urn:xmpp:mix:nodes:allowed"));
		assertTrue(nodes.contains("urn:xmpp:mix:nodes:banned"));

		payload = ElementFactory.create(payload);
		payload.setParent(null);

		for (Element field : payload.getChildren("field")) {
			if ("Nodes Present".equals(field.getAttribute("var"))) {
				Set<String> nodesPresent = field.getChildren("value").stream().map(it -> {
					try {
						return it.getValue();
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
				}).collect(Collectors.toSet());

				assertTrue(nodesPresent.contains("allowed"));
				assertTrue(nodesPresent.contains("banned"));

				nodesPresent.remove("allowed");
				for (Element value : field.getChildren("value")) {
					field.removeChild(value);
				}
				for (String node : nodesPresent) {
					field.addChild(ElementBuilder.create("value").setValue(node).getElement());
				}
			}
		}

		final String TAG4 = "4:publish:config";
		getJaxmppAdmin().getModule(PubSubModule.class).publishItem(channelJID, "urn:xmpp:mix:nodes:config", "2024-01-01", payload, new PubSubModule.PublishAsyncCallback() {
			@Override
			public void onPublish(String s) {
				mutex.notify(TAG4 + ":success", TAG4);
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify(TAG4 + ":error:" + errorCondition + ":" + pubSubErrorCondition, TAG4);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(TAG4 + ":timeout", TAG4);
			}
		});
		mutex.waitFor(30*1000, TAG4);
		assertTrue(mutex.isItemNotified(TAG4 + ":success"));

		Thread.sleep(1000);

		items.clear();
		final String TAG5 = "5:disco:nodes";
		getJaxmppAdmin().getModule(DiscoveryModule.class).getItems(JID.jidInstance(channelJID), "mix", new DiscoveryModule.DiscoItemsAsyncCallback() {
			@Override
			public void onInfoReceived(String node, ArrayList<DiscoveryModule.Item> newItems) throws XMLException {
				items.addAll(newItems);
				mutex.notify(TAG5 + ":success", TAG5);
			}

			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify(TAG5 + ":error:" + errorCondition, TAG5);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(TAG5 + ":timeout", TAG5);
			}
		});
		mutex.waitFor(30*1000, TAG5);
		assertTrue(mutex.isItemNotified(TAG5 + ":success"));
		assertFalse(items.isEmpty());

		nodes = items.stream().map(DiscoveryModule.Item::getNode).filter(Objects::nonNull).collect(Collectors.toSet());
		assertFalse(nodes.contains("urn:xmpp:mix:nodes:allowed"));
		assertTrue(nodes.contains("urn:xmpp:mix:nodes:banned"));
	}

	@Test(dependsOnMethods = {"testPresentNodesModification"})
	public void testDestroyChannel() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "create-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("destroy")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", channelName);

		Response result = sendRequest(getJaxmppAdmin(), (IQ) Stanza.create(request.getElement()));
		AssertJUnit.assertTrue("Cannot destroy channel!", result instanceof Response.Success);
	}
}
