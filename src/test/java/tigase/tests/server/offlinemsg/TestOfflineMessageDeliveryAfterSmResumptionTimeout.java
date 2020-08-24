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
package tigase.tests.server.offlinemsg;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.connector.ConnectorWrapper;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.StreamFeaturesModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.streammng.StreamManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;

/**
 * Created by andrzej on 22.06.2016.
 */
public class TestOfflineMessageDeliveryAfterSmResumptionTimeout
		extends AbstractTest {

	private static final String USER_PREFIX = "sm-resumption";

	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeMethod
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getConnectionConfiguration().setResource("test-x");
			jaxmpp.getModulesManager().register(new StreamManagementModule(jaxmpp));
			return jaxmpp;
		}).setConnected(true).build();
	}

	// Messages type == null (normal)
	@Test
	public void testMessageDeliveryReliabilityWithResumptionAndWithFullJid() throws Exception {
		testMessageDeliveryReliability(true, true, 0, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithoutResumptionAndWithFullJid() throws Exception {
		testMessageDeliveryReliability(false, true, 0, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithResumptionAndWithoutFullJid() throws Exception {
		testMessageDeliveryReliability(true, false, 0, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithoutResumptionAndWithoutFullJid() throws Exception {
		testMessageDeliveryReliability(false, false, 0, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithResumptionAndWithFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(true, true, 2000, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithoutResumptionAndWithFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(false, true, 2000, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithResumptionAndWithoutFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(true, false, 2000, null);
	}

	@Test
	public void testMessageDeliveryReliabilityWithoutResumptionAndWithoutFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(false, false, 2000, null);
	}

	// Messages type = chat
	@Test
	public void testMessageDeliveryReliabilityChatWithResumptionAndWithFullJid() throws Exception {
		testMessageDeliveryReliability(true, true, 0, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithFullJid() throws Exception {
		testMessageDeliveryReliability(false, true, 0, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithResumptionAndWithoutFullJid() throws Exception {
		testMessageDeliveryReliability(true, false, 0, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithoutFullJid() throws Exception {
		testMessageDeliveryReliability(false, false, 0, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithResumptionAndWithFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(true, true, 2000, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(false, true, 2000, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithResumptionAndWithoutFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(true, false, 2000, StanzaType.chat);
	}

	@Test
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithoutFullJidAndDelay() throws Exception {
		testMessageDeliveryReliability(false, false, 2000, StanzaType.chat);
	}

	//	 Message type == chat but with binding delay
	//@Test
	@Ignore
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithFullJidWithDelayedBinding() throws Exception {
		testMessageDeliveryReliability(false, true, 0, StanzaType.chat, 2000);
	}

	//@Test
	@Ignore
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithoutFullJidWithDelayedBinding()
			throws Exception {
		testMessageDeliveryReliability(false, false, 0, StanzaType.chat, 2000);
	}

	//@Test
	@Ignore
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithFullJidAndDelayWithDelayedBinding()
			throws Exception {
		testMessageDeliveryReliability(false, true, 2000, StanzaType.chat, 2000);
	}

	//@Test
	@Ignore
	public void testMessageDeliveryReliabilityChatWithoutResumptionAndWithoutFullJidAndDelayWithDelayedBinding()
			throws Exception {
		testMessageDeliveryReliability(false, false, 2000, StanzaType.chat, 2000);
	}

	public void testMessageDeliveryReliability(boolean resume, boolean fullJid, long delayReconnectionAndPresence,
											   StanzaType messageType) throws Exception {

		long estimatedMessageDeliveryTime = estimateMessageDeliveryTime();

		final Mutex mutex = new Mutex();

		log("\n\n\n===== simulation of connection failure \n");
		SocketConnector connector = (SocketConnector) ((ConnectorWrapper) user2Jaxmpp.getConnector()).getConnector();
		Field socketField = connector.getClass().getDeclaredField("socket");
		socketField.setAccessible(true);
		socketField.set(connector, null);
		Field outputStreamField = connector.getClass().getDeclaredField("writer");
		outputStreamField.setAccessible(true);
		outputStreamField.set(connector, null);
		Field readerField = connector.getClass().getDeclaredField("reader");
		readerField.setAccessible(true);
		readerField.set(connector, null);
		try {
			Method m = connector.getClass().getDeclaredMethod("onStreamTerminate");
			m.setAccessible(true);
			m.invoke(connector);
			if (!resume) {
				user2Jaxmpp.getSessionObject().clear();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		assertEquals(Connector.State.disconnected, user2Jaxmpp.getConnector().getState());
		user2Jaxmpp.getSessionObject().setProperty(ResourceBinderModule.BINDED_RESOURCE_JID, null);

		JID destination = fullJid ? JID.jidInstance(user2.getJid(), "test-x") : JID.jidInstance(user2.getJid());
		log("\n\n\n===== sending dummy message so client will discover it is disconnected (workaround) \n");
		sendMessage(user1Jaxmpp, destination, messageType, "test1");

		Thread.sleep(100);

		String body = UUID.randomUUID().toString();

		log("\n\n\n===== sending message to look for \n");
		sendMessage(user1Jaxmpp, destination, messageType, body);

		//Thread.sleep(delay + 65000);
		Thread.sleep((estimatedMessageDeliveryTime * 2) + 1000);

		user2Jaxmpp.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
							new MessageModule.MessageReceivedHandler() {

								@Override
								public void onMessageReceived(SessionObject sessionObject, Chat chat, Message message) {
									try {
										assertEquals("Message was delivered but with wrong destination JID",
													 destination, message.getTo());
										mutex.notify("message:" + message.getBody());
									} catch (XMLException e) {
										e.printStackTrace();
									}
								}
							});

		user2Jaxmpp.getModule(PresenceModule.class).setInitialPresence(false);

		log("\n\n\n===== reconnecting client (resumption of stream or binding using same resource) \n");
		user2Jaxmpp.login(true);

		Thread.sleep(delayReconnectionAndPresence);

		log("\n\n\n===== broadcasting presence \n");
		user2Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, 5);

		mutex.waitFor(150 * 1000, "message:" + body);
		assertTrue("Message was not delivered!", mutex.isItemNotified("message:" + body));
	}

	public synchronized void testMessageDeliveryReliability(boolean resume, boolean fullJid, long delayPresence,
											   StanzaType messageType, long delayBinding) throws Exception {

		long estimatedMessageDeliveryTime = estimateMessageDeliveryTime();

		final Mutex mutex = new Mutex();

		log("\n\n\n===== simulation of connection failure \n");
		SocketConnector connector = (SocketConnector) ((ConnectorWrapper) user2Jaxmpp.getConnector()).getConnector();
		Field socketField = connector.getClass().getDeclaredField("socket");
		socketField.setAccessible(true);
		socketField.set(connector, null);
		Field outputStreamField = connector.getClass().getDeclaredField("writer");
		outputStreamField.setAccessible(true);
		outputStreamField.set(connector, null);
		Field readerField = connector.getClass().getDeclaredField("reader");
		readerField.setAccessible(true);
		readerField.set(connector, null);
		try {
			Method m = connector.getClass().getDeclaredMethod("onStreamTerminate");
			m.setAccessible(true);
			m.invoke(connector);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		Thread.sleep(100);
		if (!resume) {
			user2Jaxmpp.getSessionObject().clear();
		}
		assertEquals(Connector.State.disconnected, user2Jaxmpp.getConnector().getState());
		if (!resume) {
			assertNull(ResourceBinderModule.getBindedJID(user2Jaxmpp.getSessionObject()));
		}

		// sending message from user 1 to user 2
		String body = UUID.randomUUID().toString();
		JID destination = fullJid ? JID.jidInstance(user2.getJid(), "test-x") : JID.jidInstance(user2.getJid());

		log("\n\n\n===== sending dummy message so client will discover it is disconnected (workaround) \n");
		sendMessage(user1Jaxmpp, destination, messageType, "test1");

		log("\n\n\n===== sending dummy message so client will discover it is disconnected (workaround) \n");
		sendMessage(user1Jaxmpp, destination, messageType, "test2");

		Thread.sleep((estimatedMessageDeliveryTime * 2) + 1000);

		Thread.sleep(2000);

		user2Jaxmpp.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
							new MessageModule.MessageReceivedHandler() {

								@Override
								public void onMessageReceived(SessionObject sessionObject, Chat chat, Message message) {
									try {
										assertEquals("Message was delivered but with wrong destination JID",
													 destination, message.getTo());
										mutex.notify("message:" + message.getBody());
									} catch (XMLException e) {
										e.printStackTrace();
									}
								}
							});

		user2Jaxmpp.getModule(PresenceModule.class).setInitialPresence(false);

		if (delayBinding > 0) {
			user2Jaxmpp.getEventBus()
					.addHandler(AuthModule.AuthSuccessHandler.AuthSuccessEvent.class,
								new AuthModule.AuthSuccessHandler() {
									@Override
									public void onAuthSuccess(SessionObject sessionObject) throws JaxmppException {
										try {
											log("\n\n\n===== sending message to look for \n");
											sendMessage(user1Jaxmpp, destination, messageType, body);
										} catch (Exception e) {
											e.printStackTrace();
										}

										user2Jaxmpp.getEventBus()
												.addHandler(
														StreamFeaturesModule.StreamFeaturesReceivedHandler.StreamFeaturesReceivedEvent.class,
														new StreamFeaturesModule.StreamFeaturesReceivedHandler() {
															@Override
															public void onStreamFeaturesReceived(
																	SessionObject sessionObject,
																	Element featuresElement) throws JaxmppException {
																try {
																	Thread.sleep(delayBinding);
																} catch (InterruptedException e) {
																	e.printStackTrace();
																}
															}
														});
									}
								});
		}

		log("\n\n\n===== reconnecting client (resumption of stream or binding using same resource) \n");
		user2Jaxmpp.login(true);

		assertTrue(user2Jaxmpp.isConnected());
		assertNotNull(ResourceBinderModule.getBindedJID(user2Jaxmpp.getSessionObject()));
		Thread.sleep(delayPresence);

		log("\n\n\n===== broadcasting presence \n");
		user2Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, 5);

		mutex.waitFor(resume ? 2 * 5000 : 150 * 1000, "message:" + body);
		assertTrue("Message was not delivered!", mutex.isItemNotified("message:" + body));
	}

	private long estimateMessageDeliveryTime() throws Exception {
		final Mutex mutex = new Mutex();
		MessageModule.MessageReceivedHandler handler = (SessionObject sessionObject, Chat chat, Message message) -> {
			try {
				mutex.notify("message:" + message.getBody());
			} catch (JaxmppException ex) {
			}
		};
		user2Jaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
		String body = UUID.randomUUID().toString();

		long start = System.currentTimeMillis();
		sendMessage(user1Jaxmpp, ResourceBinderModule.getBindedJID(user2Jaxmpp.getSessionObject()), StanzaType.chat,
					body);
		mutex.waitFor(10 * 1000, "message:" + body);

		user2Jaxmpp.getEventBus().remove(handler);

		long end = System.currentTimeMillis();
		long result = end - start;
		return result;
	}

	private void sendMessage(Jaxmpp jaxmpp, JID destination, StanzaType type, String body) throws Exception {
		Message m = Message.create();
		m.setBody(body);
		m.setType(type);
		m.setTo(destination);
		jaxmpp.send(m);
	}

//	public void testMessageDeliveryReliability2(boolean resume, boolean fullJid, long delay) throws Exception {
//		final Mutex mutex = new Mutex();
//		// connecting clients
//		user1Jaxmpp.login(true);
//		user2Jaxmpp.login(true);
//		user2Jaxmpp2.login(true);
//
//		System.out.println("STARTING TEST --------------");
//
//		// simulation of network connection breakdown for user 2
//		SocketConnector connector = (SocketConnector) ((ConnectorWrapper) user2Jaxmpp.getConnector()).getConnector();
//		Field socketField = connector.getClass().getDeclaredField("socket");
//		socketField.setAccessible(true);
//		//((Socket) socketField.get(connector)).close();
//		socketField.set(connector, null);
//		Field outputStreamField = connector.getClass().getDeclaredField("writer");
//		outputStreamField.setAccessible(true);
//		outputStreamField.set(connector, null);
//		Field readerField = connector.getClass().getDeclaredField("reader");
//		readerField.setAccessible(true);
//		readerField.set(connector, null);
//		try {
//			Method m = connector.getClass().getDeclaredMethod("onStreamTerminate");
//			m.setAccessible(true);
//			m.invoke(connector);
//			if (!resume) {
//				user2Jaxmpp.getSessionObject().clear();
//			}
//		} catch (Exception ex) {
//			ex.printStackTrace();
//		}
//		assertEquals(Connector.State.disconnected, user2Jaxmpp.getConnector().getState());
//
//		user2Jaxmpp2.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
//				new MessageModule.MessageReceivedHandler() {
//					@Override
//					public void onMessageReceived(SessionObject sessionObject, Chat chat, Message message) {
//						try {
//							mutex.notify(user2Jaxmpp2.getModule(ResourceBinderModule.class).getBindedJID() + ":to:" + message.getTo(),
//									user2Jaxmpp2.getModule(ResourceBinderModule.class).getBindedJID() + "message:" + message.getBody());
//						} catch (XMLException e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		// sending message from user 1 to user 2
//		String body = UUID.randomUUID().toString();
//		JID destination = fullJid ? JID.jidInstance(user2Jid, "test-x") : JID.jidInstance(user2Jid);
//		user1Jaxmpp.getModule(MessageModule.class).sendMessage(destination, null, "test1");
//		user1Jaxmpp.getModule(MessageModule.class).sendMessage(destination, null, body);
//
//		Thread.sleep(delay + 65000);
//
//		user2Jaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
//				new MessageModule.MessageReceivedHandler() {
//
//					@Override
//					public void onMessageReceived(SessionObject sessionObject, Chat chat, Message message) {
//						try {
//							mutex.notify(user2Jaxmpp.getModule(ResourceBinderModule.class).getBindedJID() + ":to:" + message.getTo(),
//									user2Jaxmpp.getModule(ResourceBinderModule.class).getBindedJID() + "message:" + message.getBody());
//						} catch (XMLException e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		user2Jaxmpp.getModule(PresenceModule.class).setInitialPresence(false);
//
//		user2Jaxmpp.login(true);
//
//		Thread.sleep(delay);
//
//		user2Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, 5);
//
//		mutex.waitFor(150 * 1000, "message:" + body);
//		assertTrue(mutex.isItemNotified("message:" + body));
//		assertTrue(mutex.isItemNotified("to:" + destination));
//		System.out.println("TEST FINISHED --------------");
//	}

}
