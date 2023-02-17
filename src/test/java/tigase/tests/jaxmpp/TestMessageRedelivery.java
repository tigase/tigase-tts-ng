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
package tigase.tests.jaxmpp;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.connector.ConnectorWrapper;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.streammng.StreamManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.testng.Assert.*;

public class TestMessageRedelivery
		extends AbstractJaxmppTest {

	private static Logger logger = Logger.getLogger(TestMessageRedelivery.class.getCanonicalName());

	private Jaxmpp senderJaxmpp;
	private Account senderUser;
	private Jaxmpp receiverJaxmpp;
	private Account receiverUser;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		senderUser = createAccount().setLogPrefix("jaxmpp_").build();
		senderJaxmpp = senderUser.createJaxmpp().build();
		receiverUser = createAccount().setLogPrefix("jaxmpp_").build();
		receiverJaxmpp = receiverUser.createJaxmpp().setConfigurator(jaxmpp -> {
			//jaxmpp.getSessionObject().setUserProperty(StreamManagementModule.STREAM_MANAGEMENT_DISABLED_KEY, true);
			return jaxmpp;
		}).setResource("test-sm").build();
	}

	@Test
	public void testMessageRedeliverySMwithResumption() throws Exception {
		testMessageRedelivery(true, false);
	}

	@Test
	public void testMessageRedeliverySMnoResumption() throws Exception {
		testMessageRedelivery(true, false);
	}

	@Test
	public void testMessageRedeliveryNoSM() throws Exception {
		testMessageRedelivery(false, false);
	}
	
	public void testMessageRedelivery(boolean useSM, boolean useResumption) throws Exception {
		receiverJaxmpp.getSessionObject().setUserProperty(StreamManagementModule.STREAM_MANAGEMENT_DISABLED_KEY, !useSM);

		final Mutex mutex = new Mutex();

		receiverJaxmpp.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.socket);
		receiverJaxmpp.getConnectionConfiguration().setServer(getInstanceHostname());

		receiverJaxmpp.login(true);
		senderJaxmpp.login(true);

		assertTrue(receiverJaxmpp.isConnected());

		List<String> received = new ArrayList<>();
		List<String> expected = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		receiverJaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
												(sessionObject, chat, message) -> {
													try {
														if (!received.contains(message.getBody())) {
															received.add(message.getBody());
															if (expected.size() > 3 && (received.size() + errors.size()) == expected.size()) {
																mutex.notify("items:received:all");
															}
														}
													} catch (XMLException e) {
														throw new RuntimeException(e);
													}
												});
		senderJaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
											  (sessionObject, chat, message) -> {
												  try {
													  errors.add(message.getBody());
													  if (expected.size() > 3 && (received.size() + errors.size()) == expected.size()) {
														  mutex.notify("items:received:all");
													  }
												  } catch (XMLException e) {
													  throw new RuntimeException(e);
												  }
											  });
		expected.add("Test message " + UUID.randomUUID());
		sendAndWait(senderJaxmpp, receiverJaxmpp, expected.get(0));
		Thread.sleep(500);

		assertEquals(received.size(), expected.size());
		assertEquals(received, expected);

		assertTrue(receiverJaxmpp.isConnected());

		Method method = SocketConnector.class.getDeclaredMethod("closeSocket");
		method.setAccessible(true);

		for (int i=0; i<50; i++) {
			String message = "Test message " + UUID.randomUUID();
			final Message msg1 = Message.create();
			msg1.setTo(JID.jidInstance(receiverUser.getJid()));
			msg1.setBody(message);
			msg1.setType(StanzaType.chat);
			msg1.setId(UUID.randomUUID().toString());

			expected.add(message);
			senderJaxmpp.send(msg1);
		}

		method.invoke(((ConnectorWrapper) receiverJaxmpp.getConnector()).getConnector());

		Thread.sleep(500);

		try {
			receiverJaxmpp.disconnect(true, !useResumption);
		} catch (Throwable ex) {}
		assertFalse(receiverJaxmpp.isConnected());

		Thread.sleep(1000);

		receiverJaxmpp.login(true);

		assertTrue(receiverJaxmpp.isConnected());

		mutex.waitFor(10000, "items:received:all");

		if (!useSM) {
			// when SM is not used, some packets may be lost (were written to the socket I/O buffer but not sent
			assertTrue(expected.size() - (received.size() + errors.size()) < 2);
		} else {
			assertEquals(received, expected);
		}
	}

}

