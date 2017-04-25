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
package tigase.tests.server.offlinemsg;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.testng.Assert.assertTrue;

/**
 *
 * @author andrzej
 */
public class TestOfflineMessagesLimit extends AbstractTest {

	private static final String USER_PREFIX = "offline_";
	
	Account user1;
	Account user2;
	Jaxmpp userJaxmpp1;
	Jaxmpp userJaxmpp2;
	
	@BeforeMethod
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		userJaxmpp1 = user1.createJaxmpp().setConnected(true).build();
		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		userJaxmpp2 = user2.createJaxmpp().setConnected(true).build();
	}
	
	@Test(groups = { "XMPP - Offline Messages" }, description = "Setting offline messages limit to 3")
	public void testOfflineLimit1() throws Exception {
		int limit = 3;
		Mutex mutex = new Mutex();
		setLimit(mutex, userJaxmpp2, limit);
		testLimit(mutex, userJaxmpp1, userJaxmpp2, limit);
	}
	
	@Test(groups = { "XMPP - Offline Messages" }, description = "Setting offline messages limit to 5")
	public void testOfflineLimit2() throws Exception {
		int limit = 5;
		Mutex mutex = new Mutex();
		setLimit(mutex, userJaxmpp2, limit);
		testLimit(mutex, userJaxmpp1, userJaxmpp2, limit);		
	}

	@Test(groups = { "XMPP - Offline Messages" }, description = "Setting offline messages limit to 0 - disabling offline storage")
	public void testOfflineLimit3() throws Exception {
		int limit = 0;
		Mutex mutex = new Mutex();
		setLimit(mutex, userJaxmpp2, limit);
		testLimit(mutex, userJaxmpp1, userJaxmpp2, limit);		
	}	
	
	@Test(groups = { "XMPP - Offline Messages" }, description = "Setting offline messages limit to 3 and removing limit")
	public void testOfflineLimit4() throws Exception {
		int limit = 3;
		Mutex mutex = new Mutex();
		setLimit(mutex, userJaxmpp2, limit);
		limit = -1;
		setLimit(mutex, userJaxmpp2, limit);
		testLimit(mutex, userJaxmpp1, userJaxmpp2, limit);		
	}
	
	private void setLimit(final Mutex mutex, Jaxmpp jaxmpp, final int limit) throws JaxmppException, InterruptedException {
		final String id = UUID.randomUUID().toString();
		IQ iq = IQ.createIQ();
		iq.setAttribute("id", id);
		iq.setType(StanzaType.set);
		Element msgoffline = ElementFactory.create("msgoffline", null, "msgoffline");
		String limitStr = limit >= 0 ? String.valueOf(limit) : "false";
		msgoffline.setAttribute("limit", limitStr);
		iq.addChild(msgoffline);
		jaxmpp.send(iq, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("offline:limit:set:null:" + responseStanza.getAttribute("type") + ":" + responseStanza.getAttribute("id"));
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("offline:limit:set:" + responseStanza.getFirstChild("msgoffline").getAttribute("limit") 
						+ ":" + responseStanza.getAttribute("type") + ":" + responseStanza.getAttribute("id"));
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("offline:limit:set:null:timeout:null");
			}
		});
		mutex.waitFor(20 * 1000, "offline:limit:set:" + limitStr + ":result:" + id);
		assertTrue(mutex.isItemNotified("offline:limit:set:" + limitStr + ":result:" + id));
	}
	
	private void testLimit(final Mutex mutex, Jaxmpp sender, Jaxmpp receiver, int limit) throws JaxmppException, InterruptedException {
		receiver.disconnect(true);
		
		Thread.sleep(2000);

		final List<String> sent = new ArrayList<String>();
		
		sender.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, 
				new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("offline:message:error:" + stanza.getFirstChild("body").getValue());
				} catch (XMLException ex) {
					Logger.getLogger(TestOfflineMessagesLimit.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});		
		
		for (int i=0; i<10; i++) {
			String body = UUID.randomUUID().toString();
			
			sender.getModule(MessageModule.class).sendMessage(
					JID.jidInstance(receiver.getSessionObject().getUserBareJid()), "test", body);
			sent.add(body);
			Thread.sleep(100);
		}
		
		receiver.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, 
				new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("offline:message:success:" + stanza.getFirstChild("body").getValue());
				} catch (XMLException ex) {
					Logger.getLogger(TestOfflineMessagesLimit.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});
		
		Thread.sleep(2000);
		
		receiver.login(true);
		
		if (limit == -1)
			limit = 10;
		
		for (int i=0; i<10; i++) {
			String id = "offline:message:" + (i < limit ? "success" : "error") + ":" + sent.get(i);
			mutex.waitFor(20 * 1000, id);
			assertTrue(mutex.isItemNotified(id));
		}
	}
}
