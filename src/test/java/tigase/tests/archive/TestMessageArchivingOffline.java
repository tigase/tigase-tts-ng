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
package tigase.tests.archive;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.UIDGenerator;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.AssertJUnit.*;

public class TestMessageArchivingOffline
		extends AbstractTest {

	private static final String USER_PREFIX = "mam-";
	private List<Date> expDates = new ArrayList<>();
	private List<String> expTags = new ArrayList<>();
	private Timer timer;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeClass
	public void setUp() throws Exception {
		timer = new Timer();
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			jaxmpp.getEventBus().addHandler(PresenceModule.BeforePresenceSendHandler.BeforePresenceSendEvent.class,
											(sessionObject, presence) -> {
												presence.setPriority(-1);
											});
			return jaxmpp;
		}).setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			return jaxmpp;
		}).setConnected(true).build();
	}

	@AfterClass
	public void cleanUp() throws Exception {
		timer.cancel();
	}

	@Test
	public void testSettingsChange() throws Exception {
		final Mutex mutex = new Mutex();
		MessageArchiveManagementModule mamModule1 = user1Jaxmpp.getModule(MessageArchiveManagementModule.class);

		mamModule1.retrieveSettings(new MessageArchiveManagementModule.SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
								  List<JID> never) throws JaxmppException {
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

	@Test(dependsOnMethods = {"testSettingsChange"})
	public void testMessageRetrievalFromEmpty() throws Exception {
		final Mutex mutex = new Mutex();
		testMessageRerieval(mutex, user1Jaxmpp, expTags, true);
	}

	@Test(dependsOnMethods = {"testMessageRetrievalFromEmpty"})
	public void testMessageArchival() throws Exception {
		final Mutex mutex = new Mutex();
		for (int i = 0; i < 20; i++) {
			Jaxmpp sender = (i % 2 == 0) ? user2Jaxmpp : user1Jaxmpp;
			Jaxmpp recipient = (i % 2 == 1) ? user2Jaxmpp : user1Jaxmpp;
			expDates.add(new Date());
			sendTestMessage(mutex, sender, recipient, i == 19);
			Thread.sleep(2000);
		}
	}

	@Test(dependsOnMethods = {"testMessageArchival"})
	public void testMessageRetrievalWithNonEmptyResults() throws Exception {
		final Mutex mutex = new Mutex();
		testMessageRerieval(mutex, user1Jaxmpp, null, null, expTags, true, true);
	}

	public void changeSettings(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.DefaultValue value)
			throws Exception {
		String id = UUID.randomUUID().toString();
		MessageArchiveManagementModule mamModule1 = jaxmpp.getModule(MessageArchiveManagementModule.class);
		mamModule1.updateSetttings(value, null, null, new MessageArchiveManagementModule.SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
								  List<JID> never) throws JaxmppException {
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

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, List<String> expectedMessageTags,
									boolean complete) throws Exception {
		testMessageRerieval(mutex, jaxmpp, null, expectedMessageTags, complete);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query,
									List<String> expectedMessageTags, boolean complete) throws Exception {
		testMessageRerieval(mutex, jaxmpp, query, null, expectedMessageTags, complete, false);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query,
									RSM rsm, List<String> expectedMessageTags, boolean complete, boolean updateExpDates) throws Exception {
		String queryid = UUID.randomUUID().toString();

		final AtomicInteger count = new AtomicInteger(0);

		if (updateExpDates) {
			expDates.clear();
		}

		MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler = new MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler() {
			@Override
			public void onArchiveItemReceived(SessionObject sessionObject, String queryid, String messageId,
											  Date timestamp, Message message) throws JaxmppException {
				mutex.notify("item:" + message.getFrom() + ":" + message.getTo() + ":" + message.getBody());
				if (updateExpDates) {
					expDates.add(timestamp);
				}
				count.incrementAndGet();
			}
		};
		jaxmpp.getContext()
				.getEventBus()
				.addHandler(
						MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		MessageArchiveManagementModule mamModule1 = jaxmpp.getModule(MessageArchiveManagementModule.class);
		mamModule1.queryItems((MessageArchiveManagementModule.Query) query, queryid, rsm,
							  new MessageArchiveManagementModule.ResultCallback() {
								  @Override
								  public void onSuccess(String queryid, boolean complete, RSM rsm)
										  throws JaxmppException {
									  mutex.notify("items:received:" + queryid + ":" + complete);
									  timer.schedule(new TimerTask() {
										  @Override
										  public void run() {
											  mutex.notify("items:received");
										  }
									  }, 1000);

								  }

								  @Override
								  public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										  throws JaxmppException {
									  mutex.notify("items:received");
								  }

								  @Override
								  public void onTimeout() throws JaxmppException {
									  mutex.notify("items:received");
								  }
							  });

		mutex.waitFor(10 * 1000, "items:received");

		assertTrue(mutex.isItemNotified("items:received:" + queryid + ":" + complete));

		jaxmpp.getContext()
				.getEventBus()
				.remove(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
						handler);

		for (String tag : expectedMessageTags) {
			assertTrue("Not returned message: " + tag, mutex.isItemNotified("item:" + tag));
		}

		assertEquals(expectedMessageTags.size(), count.get());
	}

	public void sendTestMessage(final Mutex mutex, Jaxmpp sender, Jaxmpp recipient, boolean noBodyWithStore) throws Exception {
		JID to = JID.jidInstance(recipient.getSessionObject().getUserBareJid());
		String msg = noBodyWithStore ?  null : "Message-" + UUID.randomUUID().toString();
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

		recipient.getContext()
				.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		if (noBodyWithStore) {
			Message m = Message.create();
			m.setType(StanzaType.chat);
			m.setTo(to);
			m.setId(UIDGenerator.next());

			m.addChild(ElementFactory.create("store", null, "urn:xmpp:hints"));

			sender.getContext().getWriter().write(m);
		} else {
			sender.getModule(MessageModule.class).sendMessage(to, null, msg);
		}

		mutex.waitFor(1000, tag);

		if (user1Jaxmpp == recipient) {
			assertFalse(mutex.isItemNotified(tag));
		} else {
			assertTrue(mutex.isItemNotified(tag));
		}

		expTags.add(tag);

		recipient.getContext()
				.getEventBus()
				.remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
	}

}
