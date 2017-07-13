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

import org.testng.Assert;
import org.testng.annotations.*;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.BooleanField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.*;
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

/**
 * Created by andrzej on 25.07.2016.
 */
public class TestMessageArchivingMUC extends AbstractTest {

	private static final String USER_PREFIX = "muc_test";

	BareJID adminJID;
	Jaxmpp adminJaxmpp;

	Account user1;
	Jaxmpp user1Jaxmpp;
	Account user2;
	Jaxmpp user2Jaxmpp;
	Account user3;
	Jaxmpp user3Jaxmpp;
	Account user4;
	Jaxmpp user4Jaxmpp;

	@BeforeClass
	public void prepareAdmin() throws JaxmppException {
		adminJaxmpp = getAdminAccount().createJaxmpp().setConnected(true).build();
		adminJID = getAdminAccount().getJid();
	}

	@BeforeMethod
	public void prepareTest() throws JaxmppException, InterruptedException {
		// prepare users
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2 = user1;
		user3 = createAccount().setLogPrefix(USER_PREFIX).build();
		user4 = createAccount().setLogPrefix(USER_PREFIX).build();

		// prepare Jaxmpp instances
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();
		user3Jaxmpp = user3.createJaxmpp().setConnected(true).build();
		user4Jaxmpp = user4.createJaxmpp().setConnected(true).build();
	}

	@Test
	public void testArchivingOfMucMessages() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();

		// create MUC room and join
		String roomName = "test_2710_" + nextRnd();
		String mucDomain = "muc." + user1.getJid().getDomain();
		BareJID roomJid = BareJID.bareJIDInstance(roomName, mucDomain);
		joinRoom(mutex, user1Jaxmpp, roomJid);
		configureRoom(mutex, user1Jaxmpp, roomJid);
		Thread.sleep(2000);

		enableArchiving(mutex, user1Jaxmpp);
		enableArchiving(mutex, user2Jaxmpp);
		enableArchiving(mutex, user3Jaxmpp);
		enableArchiving(mutex, user4Jaxmpp);

		joinRoom(mutex, user2Jaxmpp, roomJid);
		Thread.sleep(2000);
		joinRoom(mutex, user3Jaxmpp, roomJid);
		Thread.sleep(2000);
		joinRoom(mutex, user4Jaxmpp, roomJid);

		List<String> msgs = new ArrayList<String>();
		List<String> subjects = new ArrayList<String>();
		msgs.add(sendMucMessage(mutex, user1Jaxmpp, roomJid, "Message " + UUID.randomUUID().toString()));
		msgs.add(sendMucMessage(mutex, user2Jaxmpp, roomJid, "Message " + UUID.randomUUID().toString()));
		subjects.add(changeMucSubject(mutex, user1Jaxmpp, roomJid, "Subject " + UUID.randomUUID().toString()));
		msgs.add(sendMucMessage(mutex, user3Jaxmpp, roomJid, "Message " + UUID.randomUUID().toString()));
		msgs.add(sendMucMessage(mutex, user4Jaxmpp, roomJid, "Message " + UUID.randomUUID().toString()));
		subjects.add(changeMucSubject(mutex, user1Jaxmpp, roomJid, "Subject " + UUID.randomUUID().toString()));

		String id = UUID.randomUUID().toString();
		assertArchivedMessages(mutex, user1Jaxmpp, roomJid, msgs, subjects, id);
		assertArchivedMessages(mutex, user2Jaxmpp, roomJid, msgs, subjects, id);
		assertArchivedMessages(mutex, user3Jaxmpp, roomJid, msgs, subjects, id);
		assertArchivedMessages(mutex, user4Jaxmpp, roomJid, msgs, subjects, id);

		leaveRoom(mutex, user1Jaxmpp, roomJid);
		leaveRoom(mutex, user2Jaxmpp, roomJid);
		leaveRoom(mutex, user3Jaxmpp, roomJid);
		leaveRoom(mutex, user4Jaxmpp, roomJid);

		joinRoom(mutex, user1Jaxmpp, roomJid);
		Thread.sleep(2000);
		joinRoom(mutex, user2Jaxmpp, roomJid);
		Thread.sleep(2000);
		joinRoom(mutex, user3Jaxmpp, roomJid);
		Thread.sleep(2000);
		joinRoom(mutex, user4Jaxmpp, roomJid);
		Thread.sleep(2000);

		id = UUID.randomUUID().toString();
		assertArchivedMessages(mutex, user1Jaxmpp, roomJid, msgs, subjects, id);
		assertArchivedMessages(mutex, user2Jaxmpp, roomJid, msgs, subjects, id);
		assertArchivedMessages(mutex, user3Jaxmpp, roomJid, msgs, subjects, id);
		assertArchivedMessages(mutex, user4Jaxmpp, roomJid, msgs, subjects, id);

		leaveRoom(mutex, user1Jaxmpp, roomJid);
		leaveRoom(mutex, user2Jaxmpp, roomJid);
		leaveRoom(mutex, user3Jaxmpp, roomJid);
		leaveRoom(mutex, user4Jaxmpp, roomJid);
	}

	private static Room joinRoom(final Mutex mutex, final Jaxmpp jaxmpp, final BareJID roomJid) throws JaxmppException, InterruptedException {
		final JID bindedJid = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());
		final String nick = bindedJid.getResource();
		jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {

			@Override
			public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
				mutex.notify("muc:" + bindedJid + ":" + room.getRoomJid().toString() + ":joined");
			}
		});

		Room room = jaxmpp.getModule(MucModule.class).join(roomJid.getLocalpart(), roomJid.getDomain(),
				nick);

		mutex.waitFor(20 * 1000, "muc:" + bindedJid + ":" + roomJid.toString()+":joined");

		Assert.assertTrue(mutex.isItemNotified("muc:" + bindedJid + ":" + roomJid.toString()+":joined"), "Could not join room " + roomJid + " as " + bindedJid);

		return room;
	}

	private static Room configureRoom(final Mutex mutex, final Jaxmpp jaxmpp, final BareJID roomJid) throws JaxmppException, InterruptedException {
		Room room = jaxmpp.getModule(MucModule.class).getRoom(roomJid);
		jaxmpp.getModule(MucModule.class).getRoomConfiguration(room, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("muc:" + roomJid + ":configRetrieve:error");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				Element query = responseStanza.getChildrenNS("query", "http://jabber.org/protocol/muc#owner");
				query = ElementFactory.create(query);
				Element x = query.getChildrenNS("x", "jabber:x:data");
				JabberDataElement data = new JabberDataElement(x);
				data.setAttribute("type", XDataType.submit.name());
				((BooleanField) data.getField("muc#roomconfig_persistentroom")).setFieldValue(true);

				IQ iq = IQ.createIQ();
				iq.setTo(responseStanza.getFrom());
				iq.setAttribute("type", "set");
				iq.addChild(query);

				jaxmpp.send(iq, new AsyncCallback() {

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
						mutex.notify("muc:" + roomJid + ":configSet:error");
					}

					@Override
					public void onSuccess(Stanza responseStanza) throws JaxmppException {
						mutex.notify("muc:" + roomJid + ":configSet:success");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("muc:" + roomJid + ":configSet:timeout");
					}
				});

				mutex.notify("muc:" + roomJid + ":configRetrieve:success");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("muc:" + roomJid + ":configRetrieve:timeout");
			}

		});

		mutex.waitFor(40 * 1000, "muc:" + roomJid + ":configRetrieve:success", "muc:" + roomJid + ":configSet:success");

		Assert.assertTrue(mutex.isItemNotified("muc:" + roomJid + ":configSet:success"), "MUC room configuration failed");

		return room;
	}

	private static void leaveRoom(final Mutex mutex, final Jaxmpp jaxmpp, final BareJID roomJid) throws JaxmppException {
		Room room = jaxmpp.getModule(MucModule.class).getRoom(roomJid);
		jaxmpp.getModule(MucModule.class).leave(room);
	}

	private static void enableArchiving(final Mutex mutex, final Jaxmpp jaxmpp) throws XMLException, JaxmppException, InterruptedException {
		final JID bindedJid = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());
		MessageArchivingModule.Settings settings = new MessageArchivingModule.Settings();
		settings.setAutoSave(true);
		settings.setSaveMode(SaveMode.Message);
		settings.setChildAttr("default", "muc-save", "true");
		jaxmpp.getModule(MessageArchivingModule.class).setSettings(settings, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("archive:" + bindedJid + ":settingsset:error");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("archive:" + bindedJid + ":settingsset:success");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("archive:" + bindedJid + ":settingsset:timeout");
			}
		});
		mutex.waitFor(20 * 1000, "archive:" + bindedJid + ":settingsset:success");
		Assert.assertTrue(mutex.isItemNotified("archive:" + bindedJid + ":settingsset:success"),
				"Could not properly set message archiving settings");
	}

	private static String sendMucMessage(final Mutex mutex, Jaxmpp jaxmpp, BareJID roomJid, String msg) throws JaxmppException, InterruptedException {
		jaxmpp.getModule(MucModule.class).getRoom(roomJid).sendMessage(msg);
		Thread.sleep(2000);
		return msg;
	}

	private static String changeMucSubject(final Mutex mutex, Jaxmpp jaxmpp, BareJID roomJid, String subject) throws JaxmppException, InterruptedException {
		Message msg = Message.create();
		msg.setTo(JID.jidInstance(roomJid));
		msg.setType(StanzaType.groupchat);
		msg.setSubject(subject);
		jaxmpp.send(msg);
		Thread.sleep(2000);
		return subject;
	}

	private static void assertArchivedMessages(final Mutex mutex, final Jaxmpp jaxmpp, final BareJID roomJid, List<String> msgs, List<String> subjects, final String id) throws JaxmppException, InterruptedException {
		final JID bindedJid = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());
		Criteria crit = new Criteria().setWith(JID.jidInstance(roomJid));
		final List<ChatItem> items = new ArrayList<ChatItem>();
		jaxmpp.getModule(MessageArchivingModule.class).retrieveCollection(crit, new MessageArchivingModule.ItemsAsyncCallback() {

			@Override
			protected void onItemsReceived(ChatResultSet chat) throws XMLException {
				items.addAll(chat.getItems());
				mutex.notify("archive:" + bindedJid + ":retrieve:" + roomJid + ":" + id + ":success");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("archive:" + bindedJid + ":retrieve:" + roomJid + ":" + id + ":error");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("archive:" + bindedJid + ":retrieve:" + roomJid + ":" + id + ":timeout");
			}
		});
		mutex.waitFor(20 * 1000, "archive:" + bindedJid + ":retrieve:" + roomJid + ":" + id + ":success");
		Assert.assertTrue(mutex.isItemNotified("archive:" + bindedJid + ":retrieve:" + roomJid + ":" + id + ":success"));

		//Assert.assertEquals(items.size(), msgs.size() + subjects.size());

		List<String> gotMsgs = new ArrayList<String>();
		List<String> gotSubjects = new ArrayList<String>();
		for (ChatItem item : items) {
			if (item.getBody() != null)
				gotMsgs.add(item.getBody());
			Element itemEl = item.getItem();
			Element subjectEl = itemEl.getFirstChild("subject");
			if (subjectEl != null) {
				String subject = subjectEl.getValue();
				if (subject != null && !subject.isEmpty()) {
					gotSubjects.add(subjectEl.getValue());
				}
			}
		}
		Assert.assertEquals(gotMsgs, msgs);
		Assert.assertEquals(gotSubjects, subjects);
	}

}
