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
package tigase.tests.server;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.connector.ConnectorWrapper;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.StreamFeaturesModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.websocket.WebSocketConnector;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.server.offlinemsg.TestOfflineMessagesLimit;
import tigase.tests.utils.Account;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.testng.Assert.*;

/**
 * @author andrzej
 */
public class TestWebSocketConnectivity
		extends AbstractTest {

	private final static Charset UTF_CHARSET = Charset.forName("UTF-8");
	private static final String EOL = "\r\n";
	private static final byte[] HTTP_RESPONSE_101 = "HTTP/1.1 101 ".getBytes(UTF_CHARSET);

	private static final String USER_PREFIX = "ws_";

	Account user1;
	Jaxmpp userJaxmpp1;
	Jaxmpp userJaxmpp2;

	@BeforeMethod
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		userJaxmpp1 = user1.createJaxmpp().setLogPrefix("non_" + USER_PREFIX).build();
		userJaxmpp2 = user1.createJaxmpp().build();
	}

	//@Test
//	public void testWebSocket_Connectivity() throws Exception {
//		String wsUri = "ws://" + getInstanceHostname() + ":5290/";
//		userJaxmpp2.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
//		userJaxmpp2.getConnectionConfiguration().setBoshService(wsUri);
//		userJaxmpp1.login(true);
//		userJaxmpp2.login(true);
//
//		assertTrue(userJaxmpp1.isConnected());
//		assertTrue(userJaxmpp2.isConnected());
//		Mutex mutex = new Mutex();
//
//		userJaxmpp2.getEventBus()
//				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
//							new MessageModule.MessageReceivedHandler() {
//
//								@Override
//								public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
//									try {
//										mutex.notify("websocket:message:to:" + stanza.getFirstChild("body").getValue());
//									} catch (XMLException ex) {
//										Logger.getLogger(TestOfflineMessagesLimit.class.getName())
//												.log(Level.SEVERE, null, ex);
//									}
//								}
//							});
//
//		String body = UUID.randomUUID().toString();
//		Message msg = Message.createMessage();
//		msg.setTo(ResourceBinderModule.getBindedJID(userJaxmpp2.getSessionObject()));
//		msg.setBody(body);
//		userJaxmpp1.send(msg);
//		mutex.waitFor(20 * 1000, "websocket:message:to:" + body);
//		Thread.sleep(1000);
//		assertTrue(mutex.isItemNotified("websocket:message:to:" + body));
//	}

	//@Test
//	public void testWebSocket_TwoWebSocketTextFramesInSingleTcpFrame() throws Exception {
//		String wsUri = "ws://" + getInstanceHostname() + ":5290/";
//		userJaxmpp1.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
//		userJaxmpp1.getConnectionConfiguration().setBoshService(wsUri);
//		userJaxmpp2.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
//		userJaxmpp2.getConnectionConfiguration().setBoshService(wsUri);
//		userJaxmpp1.login(true);
//		userJaxmpp2.login(true);
//
//		assertTrue(userJaxmpp1.isConnected());
//		assertTrue(userJaxmpp2.isConnected());
//		Mutex mutex = new Mutex();
//
//		userJaxmpp2.getEventBus()
//				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
//							new MessageModule.MessageReceivedHandler() {
//
//								@Override
//								public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
//									try {
//										mutex.notify("websocket:message:to:" + stanza.getFirstChild("body").getValue());
//									} catch (XMLException ex) {
//										Logger.getLogger(TestOfflineMessagesLimit.class.getName())
//												.log(Level.SEVERE, null, ex);
//									}
//								}
//							});
//
//		String body = UUID.randomUUID().toString();
//		Message msg1 = Message.createMessage();
//		msg1.setTo(ResourceBinderModule.getBindedJID(userJaxmpp2.getSessionObject()));
//		msg1.setBody("First-" + body);
//
//		Message msg2 = Message.createMessage();
//		msg2.setTo(ResourceBinderModule.getBindedJID(userJaxmpp2.getSessionObject()));
//		msg2.setBody(body);
//
//		ByteBuffer frame1 = generateTextFrame(msg1.getAsString());
//		ByteBuffer frame2 = generateTextFrame(msg2.getAsString());
//		ByteBuffer tmp = ByteBuffer.allocate(frame1.remaining() + frame2.remaining());
//		tmp.put(frame1);
//		tmp.put(frame2);
//		tmp.flip();
//
//		byte[] data = new byte[tmp.remaining()];
//		tmp.get(data);
//		sendUnwrappedData(userJaxmpp1, data);
//
//		mutex.waitFor(20 * 1000, "websocket:message:to:" + body);
//		Thread.sleep(1000);
//		assertTrue(mutex.isItemNotified("websocket:message:to:" + body));
//	}

	@Test
	public void testWebSocket_TwoFramesPingAndTextFrameInSingleTcpFrame() throws Exception {
		String wsUri = "ws://" + getInstanceHostname() + ":5290/";
		userJaxmpp1.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
		userJaxmpp1.getConnectionConfiguration().setBoshService(wsUri);
		userJaxmpp2.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
		userJaxmpp2.getConnectionConfiguration().setBoshService(wsUri);
		userJaxmpp1.login(true);
		userJaxmpp2.login(true);

		assertTrue(userJaxmpp1.isConnected());
		assertTrue(userJaxmpp2.isConnected());
		Mutex mutex = new Mutex();

		userJaxmpp2.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
							new MessageModule.MessageReceivedHandler() {

								@Override
								public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
									try {
										mutex.notify("websocket:message:to:" + stanza.getFirstChild("body").getValue());
									} catch (XMLException ex) {
										Logger.getLogger(TestOfflineMessagesLimit.class.getName())
												.log(Level.SEVERE, null, ex);
									}
								}
							});

		String body = UUID.randomUUID().toString();

		Message msg2 = Message.createMessage();
		msg2.setTo(ResourceBinderModule.getBindedJID(userJaxmpp2.getSessionObject()));
		msg2.setBody(body);

		ByteBuffer frame1 = ByteBuffer.allocate(40);
		frame1.put((byte) 0x89);
		frame1.put((byte) 0x84);
		byte[] payload = new byte[]{0x00, 0x00, 0x00, 0x00};
		byte[] mask = new byte[]{0x00, 0x00, 0x00, 0x00};
		frame1.put(mask);
		for (int i = 0; i < payload.length; i++) {
			frame1.put((byte) (payload[i] ^ mask[i]));
		}
		frame1.flip();
		ByteBuffer frame2 = generateTextFrame(msg2.getAsString());
		ByteBuffer tmp = ByteBuffer.allocate(frame1.remaining() + frame2.remaining());
		tmp.put(frame1);
		tmp.put(frame2);
		tmp.flip();

		byte[] data = new byte[tmp.remaining()];
		tmp.get(data);
		sendUnwrappedData(userJaxmpp1, data);

		mutex.waitFor(20 * 1000, "websocket:message:to:" + body);
		Thread.sleep(1000);
		assertTrue(mutex.isItemNotified("websocket:message:to:" + body));
	}

	@Test
	public void testWebSocket_ConnectionHeaderCaseSensitivity() throws IOException, InterruptedException {
		assertEquals(testConnectionHeaderCaseSensitivity("Upgrade"), HTTP_RESPONSE_101, "Unexpected response!");
		assertEquals(testConnectionHeaderCaseSensitivity("Upgrade".toLowerCase()), HTTP_RESPONSE_101, "Unexpected response!");
		assertEquals(testConnectionHeaderCaseSensitivity("Upgrade".toUpperCase()), HTTP_RESPONSE_101, "Unexpected response!");
		assertNotEquals(testConnectionHeaderCaseSensitivity("ERROR"), HTTP_RESPONSE_101, "Unexpected response!");
	}

	@Test
	public void testWebSocket_StartTLSFeature() throws IOException, InterruptedException, JaxmppException {
		String wsUri = "ws://" + getInstanceHostname() + ":5290/";
		userJaxmpp1.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
		userJaxmpp1.getConnectionConfiguration().setBoshService(wsUri);

		ensureDisconnected(userJaxmpp1);

		final Mutex mutex = new Mutex();

		userJaxmpp1.getEventBus().addHandler(
				StreamFeaturesModule.StreamFeaturesReceivedHandler.StreamFeaturesReceivedEvent.class, new StreamFeaturesModule.StreamFeaturesReceivedHandler() {
					@Override
					public void onStreamFeaturesReceived(SessionObject sessionObject, Element element)
							throws JaxmppException {
						List<Element> features = element.getChildren();
						if (features != null) {
							for (Element feature : features) {
								mutex.notify(("received:feature:" + feature.getName() + ":" + feature.getXMLNS()));
							}
						}
						mutex.notify("received:features:success");
					}
				});

		ensureConnected(userJaxmpp1);
		mutex.waitFor(10 * 1000, "received:features:success");
		assertTrue(mutex.isItemNotified("received:features:success"));
		assertFalse(mutex.isItemNotified("received:feature:starttls:urn:ietf:params:xml:ns:xmpp-tls"));
	}

	public byte[] testConnectionHeaderCaseSensitivity(String header) throws IOException, InterruptedException {
		URI uri = URI.create(getWebSocketURI());
		String wskey = UUID.randomUUID().toString();
		StringBuilder sb = new StringBuilder();
		sb.append("GET ").append(uri.getPath() != null ? uri.getPath() : "/").append(" HTTP/1.1").append(EOL);
		sb.append("Host: ").append(uri.getHost());
		if (uri.getPort() != -1) {
			sb.append(":").append(uri.getPort());
		}
		sb.append(EOL);
		sb.append("Connection: ").append(header).append(EOL);
		sb.append("Upgrade: websocket").append(EOL);
		sb.append("Sec-WebSocket-Key: ").append(wskey).append(EOL);
		sb.append("Sec-WebSocket-Protocol: ").append("xmpp").append(",").append("xmpp-framing").append(EOL);
		sb.append("Sec-WebSocket-Version: 13").append(EOL);
		sb.append(EOL);
		byte[] buffer = sb.toString().getBytes(UTF_CHARSET);

		Socket socket = new Socket();
		socket.setKeepAlive(false);
		socket.setTcpNoDelay(true);

		socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));

		socket.getOutputStream().write(buffer);

		buffer = new byte[4096];
		while (socket.getInputStream().available() < HTTP_RESPONSE_101.length) {
			Thread.sleep(100);
		}
		socket.getInputStream().read(buffer);

		socket.close();
		
		return Arrays.copyOf(buffer, HTTP_RESPONSE_101.length);
	}

	private ByteBuffer generateTextFrame(String input) {
		try {
			Random random = new SecureRandom();
			byte[] mask = new byte[4];
			byte[] buffer = input.getBytes("UTF-8");
			// prepare WebSocket header according to Hybi specification
			int size = buffer.length;
			random.nextBytes(mask);
			byte maskedLen = (byte) 0x80;
			ByteBuffer bbuf = ByteBuffer.allocate(4096);
			bbuf.put((byte) 0x81);
			if (size <= 125) {
				maskedLen |= (byte) size;
				bbuf.put(maskedLen);
			} else if (size <= 0xFFFF) {
				maskedLen |= (byte) 0x7E;
				bbuf.put(maskedLen);
				bbuf.putShort((short) size);
			} else {
				maskedLen |= (byte) 0x7F;
				bbuf.put(maskedLen);
				bbuf.putLong(size);
			}
			bbuf.put(mask);

			for (int i = 0; i < buffer.length; i++) {
				buffer[i] = (byte) (buffer[i] ^ mask[i % 4]);
			}
			bbuf.put(buffer, 0, buffer.length);
			bbuf.flip();
			return bbuf;
		} catch (Exception e) {
		}
		return null;
	}

	private void sendUnwrappedData(Jaxmpp jaxmpp, byte[] data) throws Exception {
		WebSocketConnector webSocketConnector = (WebSocketConnector) ((ConnectorWrapper) jaxmpp.getConnector()).getConnector();
		Field f = WebSocketConnector.class.getDeclaredField("writer");
		f.setAccessible(true);
		OutputStream writer = (OutputStream) f.get(webSocketConnector);
		writer.write(data);
		writer.flush();
	}
}
