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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.*;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertTrue;

/**
 * Created by andrzej on 25.07.2016.
 */
public class TestMessageArchivingBasic
		extends AbstractTest {

	private static final String USER_PREFIX = "MaM-";
	private List<String> expTags = new ArrayList<>();
	private String id;
	private Mutex mutex;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeClass
	public void setUp() throws Exception {
		mutex = new Mutex();
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			return jaxmpp;
		}).setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			return jaxmpp;
		}).setConnected(true).build();
		id = UUID.randomUUID().toString();
	}

	@Test
	public void testChangeArchiveSettings() throws Exception {
		setArchiveSettings(user1Jaxmpp, id, true);
		String expect = "setArchiveSettings:" + id + ":success";
		mutex.waitFor(20 * 1000, expect);
		assertTrue(mutex.isItemNotified(expect), "Setting settings failed - shoule pass.");
	}

	@Test(dependsOnMethods = {"testChangeArchiveSettings"})
	public void testMessageArchiving() throws Exception {
		// lets filll the archive
		for (int i = 0; i < 10; i++) {
			String tag = nextRnd();
			if (i % 2 == 0) {
				sendAndWait(user1Jaxmpp, user2Jaxmpp, tag);
			} else {
				sendAndWait(user2Jaxmpp, user1Jaxmpp, tag);
			}
			expTags.add(tag);
			Thread.sleep(1000);
		}
	}

	@Test(dependsOnMethods = {"testMessageArchiving"})
	public void testMesssageRetrivalSuccess() throws Exception {
		String testId = UUID.randomUUID().toString();
		retrieveArchivedMessage(user1Jaxmpp, user2Jaxmpp.getSessionObject().getUserBareJid(), id, testId);
		for (String tag : expTags) {
			assertTrue(mutex.isItemNotified("2:" + testId + ":" + id + ":retriveCollection:success:" + tag),
					   "Retrieving message from repository failed - " + tag);
		}
	}

	@Test(dependsOnMethods = {"testMessageArchiving"})
	public void testMesssageRetrivalJidComparison() throws Exception {
		// check that localpart and domain are compared in case insensitive way
		String testId = UUID.randomUUID().toString();
		retrieveArchivedMessage(user1Jaxmpp, BareJID.bareJIDInstance(
				user2Jaxmpp.getSessionObject().getUserBareJid().toString().toLowerCase()), id, testId);
		for (String tag : expTags) {
			assertTrue(mutex.isItemNotified("2:" + testId + ":" + id + ":retriveCollection:success:" + tag),
					   "Retrieving message from repository failed - " + tag);
		}
	}

	private void retrieveArchivedMessage(final Jaxmpp jaxmppUser1, BareJID userBareJid, final String id,
										 final String testId) throws JaxmppException, InterruptedException {
		jaxmppUser1.getModule(MessageArchivingModule.class)
				.listCollections(JID.jidInstance(userBareJid), null, null, null,
								 new MessageArchivingModule.CollectionAsyncCallback() {

									 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											 throws JaxmppException {
										 mutex.notify("1:" + testId + ":" + id + ":retriveCollection:error");
									 }

									 public void onTimeout() throws JaxmppException {
										 mutex.notify("1:" + testId + ":" + id + ":retriveCollection:timeout");
									 }

									 @Override
									 protected void onCollectionReceived(ResultSet<Chat> vcard) throws XMLException {
										 mutex.notify("1:" + testId + ":" + id + ":retriveCollection:received");

										 for (Chat item : vcard.getItems()) {
											 try {
												 jaxmppUser1.getModule(MessageArchivingModule.class)
														 .retriveCollection(item.getWithJid(), item.getStart(), null,
																			null, null, null,
																			new MessageArchivingModule.ItemsAsyncCallback() {

																				public void onError(
																						Stanza responseStanza,
																						XMPPException.ErrorCondition error)
																						throws JaxmppException {
																					mutex.notify(
																							"2:" + testId + ":" + id +
																									":retriveCollection:error");
																				}

																				public void onTimeout()
																						throws JaxmppException {
																					mutex.notify(
																							"2:" + testId + ":" + id +
																									":retriveCollection:timeout");
																				}

																				@Override
																				protected void onItemsReceived(
																						ChatResultSet chat)
																						throws XMLException {
																					for (ChatItem item : chat.getItems()) {
																						mutex.notify(
																								"2:" + testId + ":" +
																										id +
																										":retriveCollection:success:" +
																										item.getBody());
																					}
																					mutex.notify(
																							"2:" + testId + ":" + id +
																									":retriveCollection:received");
																				}

																			});
											 } catch (JaxmppException ex) {
												 ex.printStackTrace();
											 }
										 }
									 }

								 });

		mutex.waitFor(20 * 1000, "1:" + testId + ":" + id + ":retriveCollection:received",
					  "2:" + testId + ":" + id + ":retriveCollection:received");
	}

	private void setArchiveSettings(Jaxmpp user, final String id, boolean enable)
			throws JaxmppException, InterruptedException {
		if (user.isConnected()) {
			user.disconnect(true);
			Thread.sleep(500);
			user.login(true);
		}

		user.getModule(MessageArchivingModule.class).setAutoArchive(enable, new AsyncCallback() {

			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("setArchiveSettings:" + id + ":error");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws XMLException {
				mutex.notify("setArchiveSettings:" + id + ":success");
			}

			public void onTimeout() throws JaxmppException {
				mutex.notify("setArchiveSettings:" + id + ":timeout");
			}
		});

		Thread.sleep(2 * 1000);
	}

}
