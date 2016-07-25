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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.AbstractField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 24.07.2016.
 */
public class TestMessageArchiveManagement extends AbstractTest {

	private static final String USER_PREFIX = "mam-";

	private BareJID user1Jid;
	private BareJID user2Jid;

	private Jaxmpp user1Jaxmpp;
	private Jaxmpp user2Jaxmpp;

	private List<String> expTags = new ArrayList<>();
	private List<Date> expDates = new ArrayList<>();

	@BeforeClass
	@Override
	public void setUp() throws Exception {
		super.setUp();
		user1Jid = createUserAccount(USER_PREFIX);
		user1Jaxmpp = createJaxmpp(USER_PREFIX, user1Jid);
		user1Jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());

		user2Jid = createUserAccount(USER_PREFIX);
		user2Jaxmpp = createJaxmpp(USER_PREFIX, user2Jid);
		user2Jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());

		// connecting clients
		user1Jaxmpp.login(true);
		user2Jaxmpp.login(true);
	}

	@AfterClass
	public void cleanUp() throws Exception {
		removeUserAccount(user1Jaxmpp);
		removeUserAccount(user2Jaxmpp);
	}

	@Test
	public void testRetrievalOfForm() throws Exception {
		final Mutex mutex = new Mutex();
		MessageArchiveManagementModule mamModule1 = user1Jaxmpp.getModule(MessageArchiveManagementModule.class);
		mamModule1.retrieveQueryForm(new MessageArchiveManagementModule.QueryFormCallback() {
			@Override
			public void onSuccess(JabberDataElement form) throws JaxmppException {
				for(AbstractField f : form.getFields()) {
					Element val = f.getFirstChild("value");
					mutex.notify("form:field:" + f.getVar() + ":" + (val == null ? null : val.getValue()));
				}
				mutex.notify("form:fields");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("form:fields");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("form:fields");
			}
		});

		mutex.waitFor(10 * 1000, "form:fields");

		assertTrue(mutex.isItemNotified("form:field:FORM_TYPE:urn:xmpp:mam:1"));
		assertTrue(mutex.isItemNotified("form:field:with:null"));
		assertTrue(mutex.isItemNotified("form:field:start:null"));
		assertTrue(mutex.isItemNotified("form:field:end:null"));
	}

	@Test
	public void testSettingsChange() throws Exception {
		final Mutex mutex = new Mutex();
		MessageArchiveManagementModule mamModule1 = user1Jaxmpp.getModule(MessageArchiveManagementModule.class);

		mamModule1.retrieveSettings(new MessageArchiveManagementModule.SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always, List<JID> never) throws JaxmppException {
				mutex.notify("settings:1:default:" + defValue.name());
				mutex.notify("settings:1");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("settings:1");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("settings:1");
			}
		});
		mutex.waitFor(10 * 1000, "settings:1");

		assertTrue(mutex.isItemNotified("settings:1:default:never"));

		changeSettings(mutex, user1Jaxmpp, MessageArchiveManagementModule.DefaultValue.roster);
		changeSettings(mutex, user1Jaxmpp, MessageArchiveManagementModule.DefaultValue.always);
	}

	@Test(dependsOnMethods = { "testSettingsChange" })
	public void testMessageRetrievalFromEmpty() throws Exception {
		final Mutex mutex = new Mutex();
		testMessageRerieval(mutex, user1Jaxmpp, expTags, true);
	}

	@Test(dependsOnMethods = { "testMessageRetrievalFromEmpty" })
	public void testMessageArchival() throws Exception {
		final Mutex mutex = new Mutex();
		for (int i=0; i<20; i++) {
			Jaxmpp sender = (i % 2 == 0) ? user2Jaxmpp : user1Jaxmpp;
			Jaxmpp recipient = (i % 2 == 1) ? user2Jaxmpp : user1Jaxmpp;
			expDates.add(new Date());
			sendTestMessage(mutex, sender, recipient);
			Thread.sleep(2000);
		}
	}

	@Test(dependsOnMethods = { "testMessageArchival" })
	public void testMessageRetrievalWithNonEmptyResults() throws Exception {
		final Mutex mutex = new Mutex();
		testMessageRerieval(mutex, user1Jaxmpp, expTags, true);
	}

	@Test(dependsOnMethods = { "testMessageArchival" })
	public void testMessageRetrievalWithNonEmptyResultsWithQuery() throws Exception {
		final Mutex mutex = new Mutex();

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		query.setWith(JID.jidInstance(user2Jaxmpp.getSessionObject().getUserBareJid()));
		query.setStart(expDates.get(2));
		query.setEnd(expDates.get(18));

		List<String> expTags = new ArrayList<>();
		for (int i=0; i<this.expTags.size(); i++) {
			if (i<2 || i>= 18)
				continue;
			String tag = this.expTags.get(i);
			expTags.add(tag);
		}

		testMessageRerieval(mutex, user1Jaxmpp, query, expTags, true);
	}

	@Test(dependsOnMethods = { "testMessageArchival" })
	public void testMessageRetrievalWithNonEmptyResultsWithQueryAndRsm() throws Exception {
		final Mutex mutex = new Mutex();

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		query.setWith(JID.jidInstance(user2Jaxmpp.getSessionObject().getUserBareJid()));
		query.setStart(expDates.get(2));
		query.setEnd(expDates.get(18));

		List<String> expTags = new ArrayList<>();
		for (int i=0; i<this.expTags.size(); i++) {
			if (i<2 || i>= 7)
				continue;
			String tag = this.expTags.get(i);
			expTags.add(tag);
		}

		RSM rsm = new RSM();
		rsm.setMax(5);

		testMessageRerieval(mutex, user1Jaxmpp, query, rsm, expTags, false);
	}

	public void changeSettings(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.DefaultValue value) throws Exception {
		String id = UUID.randomUUID().toString();
		MessageArchiveManagementModule mamModule1 = jaxmpp.getModule(MessageArchiveManagementModule.class);
		mamModule1.updateSetttings(value, null, null, new MessageArchiveManagementModule.SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always, List<JID> never) throws JaxmppException {
				mutex.notify("settings:" + id + ":default:" + defValue.name());
				mutex.notify("settings:" + id);
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("settings:" + id);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("settings:" + id);
			}
		});
		mutex.waitFor(10 * 1000, "settings:" + id);

		assertTrue(mutex.isItemNotified("settings:" + id + ":default:" + value.name()));
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, List<String> expectedMessageTags, boolean complete) throws Exception {
		testMessageRerieval(mutex, jaxmpp, null, expectedMessageTags, complete);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query, List<String> expectedMessageTags, boolean complete) throws Exception {
		testMessageRerieval(mutex, jaxmpp, query, null, expectedMessageTags, complete);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query, RSM rsm, List<String> expectedMessageTags, boolean complete) throws Exception {
		String queryid = UUID.randomUUID().toString();

		final AtomicInteger count = new AtomicInteger(0);

		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = new MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler() {
			@Override
			public void onArchiveItemReceived(String queryid, String messageId, Date timestamp, Message message) throws JaxmppException {
				mutex.notify("item:" + message.getFrom() + ":" + message.getTo() + ":" + message.getBody());
				count.incrementAndGet();
			}
		};
		jaxmpp.getContext().getEventBus().addHandler(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class, handler);

		MessageArchiveManagementModule mamModule1 = jaxmpp.getModule(MessageArchiveManagementModule.class);
		mamModule1.queryItems((MessageArchiveManagementModule.Query) query, queryid, rsm, new MessageArchiveManagementModule.ResultCallback() {
			@Override
			public void onSuccess(String queryid, boolean complete, RSM rsm) throws JaxmppException {
				mutex.notify("items:received:" + queryid + ":" + complete);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				mutex.notify("items:received");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("items:received");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("items:received");
			}
		});

		mutex.waitFor(10 * 1000, "items:received");

		assertTrue(mutex.isItemNotified("items:received:" + queryid + ":" + complete));

		jaxmpp.getContext().getEventBus().remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class, handler);

		for (String tag : expectedMessageTags) {
			assertTrue(mutex.isItemNotified("item:" + tag));
		}

		assertEquals(expectedMessageTags.size(), count.get());
	}

	public void sendTestMessage(final Mutex mutex, Jaxmpp sender, Jaxmpp recipient) throws Exception {
		JID to = JID.jidInstance(recipient.getSessionObject().getUserBareJid());
		String msg = "Message-" + UUID.randomUUID().toString();
		String tag = "" + ResourceBinderModule.getBindedJID(sender.getSessionObject()) + ":" + to + ":" + msg;

		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("" + stanza.getFrom() + ":" + stanza.getTo() + ":" + stanza.getBody());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}
		};

		recipient.getContext().getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		sender.getModule(MessageModule.class).sendMessage(to, null, msg);

		mutex.waitFor(10 * 1000, tag);

		expTags.add(tag);

		recipient.getContext().getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
	}
}