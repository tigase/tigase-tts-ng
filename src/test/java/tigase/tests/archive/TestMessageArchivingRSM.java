/*
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests.archive;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.ChatItem;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.ChatResultSet;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.Criteria;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.MessageArchivingModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Created by andrzej on 25.07.2016.
 */
public class TestMessageArchivingRSM extends AbstractTest {
	private final Mutex mutex = new Mutex();

	private BareJID user1JID;
	private BareJID user2JID;
	private Jaxmpp jaxmppUser1;
	private Jaxmpp jaxmppUser2;

	@BeforeMethod
	public void prepareTest() throws JaxmppException, InterruptedException {
		user1JID = createUserAccount( "user" );
		jaxmppUser1 = createJaxmpp( user1JID.getLocalpart(), user1JID );
		jaxmppUser1.login( true );

		user2JID = createUserAccount( "user" );
		jaxmppUser2 = createJaxmpp( user2JID.getLocalpart(), user2JID );
		jaxmppUser2.login( true );
	}

	@AfterMethod
	public void cleanUpTest() throws JaxmppException, InterruptedException {
		if ( !jaxmppUser1.isConnected() ){
			jaxmppUser1.login( true );
		}
		removeUserAccount( jaxmppUser1 );

		if ( !jaxmppUser2.isConnected() ){
			jaxmppUser2.login( true );
		}
		removeUserAccount( jaxmppUser2 );
	}

	@Test
	public void testCreationAndRetrievalOfLastMsgs() throws Exception {
		final String id = "retrievalOfLastMsgs";
		Date start = new Date();

		setArchiveSettings(jaxmppUser1, id, true);

		List<String> msgs = new ArrayList<String>();
		final List<String> retrievedMsgs = new ArrayList<String>();
		final StringBuilder before = new StringBuilder();

		msgs.addAll(generateMessages(jaxmppUser1, jaxmppUser2, "Test message 1 -", 25));
		msgs.addAll(generateMessages(jaxmppUser2, jaxmppUser1, "Test message 2 -", 25));

		// retrieving last 10 items
		Criteria crit = new Criteria().setWith(JID.jidInstance(user2JID)).setStart(start).setLastPage(true).setLimit(10);
		jaxmppUser1.getModule(MessageArchivingModule.class).retrieveCollection(crit, new MessageArchivingModule.ItemsAsyncCallback() {

			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("1:" + id + ":retriveCollection:error");
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify("1:" + id + ":retriveCollection:timeout");
			}

			@Override
			protected void onItemsReceived(ChatResultSet chat) throws XMLException {
				for (ChatItem item : chat.getItems()) {
					retrievedMsgs.add(item.getBody());
				}
				before.append(chat.getFirst());
				chat.getFirst();
				mutex.notify("1:" + id + ":retriveCollection:received");
			}

		});

		mutex.waitFor( 20 * 1000, "1:" + id + ":retriveCollection:received" );

		assertEquals(10, retrievedMsgs.size());
		List<String> expMsgs = msgs.subList(msgs.size() - 10, msgs.size());
		assertEquals(expMsgs, retrievedMsgs);

		// retrieving next 10 items (10 items before first from previous response)
		crit.setLastPage(false);
		crit.setBefore(before.toString());
		retrievedMsgs.clear();
		jaxmppUser1.getModule(MessageArchivingModule.class).retrieveCollection(crit, new MessageArchivingModule.ItemsAsyncCallback() {

			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("2:" + id + ":retriveCollection:error");
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify("2:" + id + ":retriveCollection:timeout");
			}

			@Override
			protected void onItemsReceived(ChatResultSet chat) throws XMLException {
				for (ChatItem item : chat.getItems()) {
					retrievedMsgs.add(item.getBody());
				}
				before.append(chat.getFirst());
				chat.getFirst();
				mutex.notify("2:" + id + ":retriveCollection:received");
			}

		});

		mutex.waitFor( 20 * 1000, "2:" + id + ":retriveCollection:received" );

		assertEquals(10, retrievedMsgs.size());
		expMsgs = msgs.subList(msgs.size() - 20, msgs.size() - 10);
		assertEquals(expMsgs, retrievedMsgs);

		// retrieving 10 items from the oldest
		crit.setLastPage(false);
		crit.setBefore(null);
		before.delete(0, before.length());
		retrievedMsgs.clear();
		jaxmppUser1.getModule(MessageArchivingModule.class).retrieveCollection(crit, new MessageArchivingModule.ItemsAsyncCallback() {

			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("3:" + id + ":retriveCollection:error");
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify("3:" + id + ":retriveCollection:timeout");
			}

			@Override
			protected void onItemsReceived(ChatResultSet chat) throws XMLException {
				for (ChatItem item : chat.getItems()) {
					retrievedMsgs.add(item.getBody());
				}
				before.append(chat.getFirst());
				chat.getFirst();
				mutex.notify("3:" + id + ":retriveCollection:received");
			}

		});

		mutex.waitFor( 20 * 1000, "3:" + id + ":retriveCollection:received" );

		assertEquals(10, retrievedMsgs.size());
		expMsgs = msgs.subList(0, 10);
		assertEquals(expMsgs, retrievedMsgs);
	}

	private List<String> generateMessages(Jaxmpp sender, Jaxmpp receiver, String msgPrefix, int count) throws Exception {
		List<String> sendMessages = new ArrayList<String>();
		for (int i=0; i<count; i++) {
			String msg = msgPrefix + " " + i;
			sendAndWait(sender, receiver, msg);
			sendMessages.add(msg);
		}
		return sendMessages;
	}

	private void setArchiveSettings( Jaxmpp user, final String id, boolean enable ) throws JaxmppException, InterruptedException {
		if ( user.isConnected() ){
			user.disconnect( true );
			Thread.sleep( 500 );
			user.login( true );
		}

		user.getModule( MessageArchivingModule.class ).setAutoArchive( enable, new AsyncCallback() {

			public void onError( Stanza responseStanza, XMPPException.ErrorCondition error ) throws JaxmppException {
				mutex.notify( "setArchiveSettings:" + id + ":error" );
			}

			@Override
			public void onSuccess( Stanza responseStanza ) throws XMLException {
				mutex.notify( "setArchiveSettings:" + id + ":success" );
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify( "setArchiveSettings:" + id + ":timeout" );
			}
		} );

		Thread.sleep( 2 * 1000 );
	}

}
