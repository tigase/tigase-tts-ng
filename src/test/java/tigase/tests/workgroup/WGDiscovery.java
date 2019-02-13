/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2019 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.tests.features;

import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.*;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * XEP-0142: Workgroup Queues
 * https://xmpp.org/extensions/xep-0142.html
 */
class WGDiscovery extends AbstractTest {

	private static final String USER_PREFIX = "WG_";
	private final Mutex mutex = new Mutex();
	private Account user;
	private Jaxmpp jaxmpp;

	@Test(groups = {"features"})
	public void testComponentDiscovery() throws JaxmppException, InterruptedException {

		user = createAccount().setLogPrefix("user1").build();
		jaxmpp = user.createJaxmpp().setConnected(true).build();
		jaxmpp.getModule(DiscoveryModule.class)
				.getInfo(JID.jidInstance("wg" + "." + user.getJid().getDomain()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {

					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> receivedFeatures) throws XMLException {

						for (DiscoveryModule.Identity identity : identities) {
							if ("collaboration".equals(identity.getCategory()) && "workgroup".equals(identity.getType())) {
								mutex.notify("discovery:components:workgroup:identity");
							}
						}

						if (receivedFeatures.contains("http://jabber.org/protocol/workgroup")) {
							mutex.notify("discovery:components:workgroup:http://jabber.org/protocol/workgroup");
						}
						mutex.notify("discovery:components:workgroup");
					}

					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("discovery:components:workgroup");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovery:components:workgroup");
					}
				});

		mutex.waitFor(30 * 1000, "discovery:components:workgroup");
		assertTrue(mutex.isItemNotified("discovery:components:workgroup:identity"));
		assertTrue(mutex.isItemNotified("discovery:components:workgroup:http://jabber.org/protocol/workgroup"));
	}
}
