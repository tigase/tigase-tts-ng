/*
 * TestPresenceDeliveryToUserWithNegativePriority.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License,
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
 */

package tigase.tests.server;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by andrzej on 21.06.2017.
 */
public class TestPresenceDeliveryToUserWithNegativePriority
		extends AbstractTest {

	private Account userA;
	private Jaxmpp userAjaxmpp;
	private Account userB;
	private Jaxmpp userBjaxmpp;

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		userA = createAccount().setLogPrefix("userA").build();
		userB = createAccount().setLogPrefix("userB").build();

		userAjaxmpp = userA.createJaxmpp().setConnected(true).build();
		userBjaxmpp = userB.createJaxmpp().setConnected(true).build();
	}

	@Test
	public void testDelivery1() throws JaxmppException, InterruptedException {
		userAjaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.online, null, -10);
		Thread.sleep(2000);

		final Mutex mutex = new Mutex();

		PresenceModule.SubscribeRequestHandler handler = new PresenceModule.SubscribeRequestHandler() {
			@Override
			public void onSubscribeRequest(SessionObject sessionObject, Presence stanza, BareJID jid) {
				try {
					mutex.notify("received:presence:" + stanza.getId());
				} catch (Exception ex) {
					assertNull(ex);
				}
			}
		};

		userAjaxmpp.getEventBus()
				.addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, handler);

		Presence p = Presence.createPresence();
		p.setTo(JID.jidInstance(userA.getJid()));
		p.setType(StanzaType.subscribe);
		String id = nextRnd();
		p.setId(id);
		userBjaxmpp.send(p);

		mutex.waitFor(20 * 1000, "received:presence:" + id);
		assertTrue(mutex.isItemNotified("received:presence:" + id));

		mutex.clear();

		assertFalse(mutex.isItemNotified("received:presence:" + id));

		userAjaxmpp.disconnect(true);

		userAjaxmpp.login(true);

		mutex.waitFor(20 * 1000, "received:presence:" + id);
		assertFalse(mutex.isItemNotified("received:presence:" + id));
	}

}
