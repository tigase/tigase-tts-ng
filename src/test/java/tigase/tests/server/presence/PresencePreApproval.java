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
package tigase.tests.server.presence;

import org.testng.Assert;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.StreamFeaturesModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

public class PresencePreApproval
		extends AbstractTest {

	@Test(groups = {"presence"}, description = "Presence: pre-approval")
	public void testPresencePreApproval() throws Exception {
		final Mutex mutex = new Mutex();

//		setLoggerLevel(Level.OFF, false );

		Jaxmpp admin = getAdminAccount().createJaxmpp().setConnected(true).build();

		final String domain = ResourceBinderModule.getBindedJID(admin.getSessionObject()).getDomain();

		Account user1 = createAccount().setLogPrefix("pre-approved-user1")
				.setUsername("pre-approved-user1--" + nextRnd())

				.setDomain(domain)
				.build();
		JID user1JID = JID.jidInstance(user1.getJid());
		final Jaxmpp user1Jaxmpp = user1.createJaxmpp().build();

		user1Jaxmpp.getModulesManager().register(new PresenceModule());
		user1Jaxmpp.getModulesManager().register(new RosterModule());

		Account user2 = createAccount().setLogPrefix("pre-approved-user2")
				.setUsername("pre-approved-user2--" + nextRnd())
				.setDomain(domain)
				.build();
		JID user2JID = JID.jidInstance(user2.getJid());
		final Jaxmpp user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();

		user1Jaxmpp.getEventBus()
				.addHandler(StreamFeaturesModule.StreamFeaturesReceivedHandler.StreamFeaturesReceivedEvent.class,
							(sessionObject, featuresElement) -> {
								if (null != featuresElement.getChildrenNS("sub", "urn:xmpp:features:pre-approval")) {
									mutex.notify("featureReceived");
								}

							});

		user1Jaxmpp.login(true);

		mutex.waitFor(5 * 1000, "featureReceived");
		Assert.assertTrue(mutex.isItemNotified("featureReceived"), "Feature present!");

		System.out.println(":: connected: " + user1Jaxmpp.isConnected());

		if (user1Jaxmpp.isConnected() && user2Jaxmpp.isConnected()) {
			user1Jaxmpp.getModule(PresenceModule.class).subscribed(user2JID);

			long start = System.currentTimeMillis();
			user1Jaxmpp.getEventBus()
					.addHandler(RosterModule.ItemAddedHandler.ItemAddedEvent.class,
								(sessionObject, item, modifiedGroups) -> {

									TestLogger.log(":: added: " + item + ", " + item.getSubscription() + ", app: " +
														   item.isApproved());

									mutex.notify(
											item.getJid() + ":" + item.getSubscription() + ":" + item.isApproved());
									mutex.notify("added:" + item.getJid());

								});

			mutex.waitFor(3 * 1000, "added:" + user2JID.getBareJid());
			Assert.assertTrue(mutex.isItemNotified(user2JID.getBareJid() + ":none:true"), "User added correctly!");

			long delay = System.currentTimeMillis() - start;

			user1Jaxmpp.getEventBus()
					.addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class,
								(sessionObject, stanza, jid) -> {

									mutex.notify(user1JID + ":subscribe:" + jid);
								});

			user2Jaxmpp.getModule(PresenceModule.class).subscribe(user1JID);

			Assert.assertFalse(mutex.isItemNotified(user1JID + ":subscribe:" + user2JID), ">subscribe< not filtered!");

			Thread.sleep((delay * 2) + 1000);

			Assert.assertTrue(user1Jaxmpp.getModulesManager()
									  .getModule(RosterModule.class)
									  .getRosterStore()
									  .get(user2JID.getBareJid())
									  .getSubscription()
									  .isFrom(), "User has has correct subscription of the contact");

			Assert.assertTrue(user2Jaxmpp.getModulesManager()
									  .getModule(RosterModule.class)
									  .getRosterStore()
									  .get(user1JID.getBareJid())
									  .getSubscription()
									  .isTo(), "Contact has has correct subscription of the user");

		}

	}

}
