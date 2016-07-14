/*
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
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
 *
 */
package tigase.tests.server;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.server.offlinemsg.TestOfflineMessagesLimit;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.testng.Assert.assertTrue;

/**
 *
 * @author andrzej
 */
public class TestWebSocketConnectivity extends AbstractTest {

	private static final String USER_PREFIX = "ws_";
	
	BareJID userJid1;
	Jaxmpp userJaxmpp1;
	Jaxmpp userJaxmpp2;
	
	@BeforeMethod
	@Override
	public void setUp() throws Exception {
		super.setUp();		
		userJid1 = createUserAccount(USER_PREFIX);
		userJaxmpp1 = createJaxmpp("non_" + USER_PREFIX, userJid1);
		userJaxmpp2 = createJaxmpp(USER_PREFIX, userJid1);
	}
	
	@AfterMethod
	public void cleanUp() throws Exception {
		if (userJaxmpp2 != null)
			userJaxmpp2.disconnect(true);
		removeUserAccount(userJaxmpp1);
	}	
	
	@Test
	public void testWebSocketConnectivity() throws Exception {
		String wsUri = "ws://" + getInstanceHostname() + ":5290/";
		userJaxmpp2.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.websocket);
		userJaxmpp2.getConnectionConfiguration().setBoshService(wsUri);
		userJaxmpp1.login(true);
		userJaxmpp2.login(true);
		
		assertTrue(userJaxmpp1.isConnected());
		assertTrue(userJaxmpp2.isConnected());
		Mutex mutex = new Mutex();

		userJaxmpp2.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, 
				new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("websocket:message:to:" + stanza.getFirstChild("body").getValue());
				} catch (XMLException ex) {
					Logger.getLogger(TestOfflineMessagesLimit.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});
		
		String body = UUID.randomUUID().toString();
		Message msg = Message.createMessage();
		msg.setTo(ResourceBinderModule.getBindedJID(userJaxmpp2.getSessionObject()));
		msg.setBody(body);
		userJaxmpp1.send(msg);
		mutex.waitFor(20 * 1000, "websocket:message:to:" + body);
		Thread.sleep(1000);
		assertTrue(mutex.isItemNotified("websocket:message:to:" + body));
	}
	
}
