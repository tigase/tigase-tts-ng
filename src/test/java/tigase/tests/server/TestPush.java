/*
 * TestPush.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2019 "Tigase, Inc." <office@tigase.com>
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

import org.testng.annotations.BeforeClass;
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

import java.util.Collection;

import static org.testng.AssertJUnit.assertTrue;

/**
 * Test is responsible for testing Push support.
 */
public class TestPush
		extends AbstractTest {

	private Account user;
	private Jaxmpp jaxmpp;

	@BeforeClass
	public void setUp() throws Exception {
		user = this.createAccount().setLogPrefix("pep-").build();
		jaxmpp = user.createJaxmpp().setConnected(true).build();
	}


	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(JID.jidInstance(jaxmpp.getSessionObject().getUserBareJid()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						if (identities != null) {
							identities.forEach(identity -> mutex.notify("discovery:identity:" + identity.getCategory() + ":" + identity.getType()));
						}
						if (features != null) {
							features.forEach(feature -> mutex.notify("discovery:feature:" + feature));
						}
						mutex.notify("discovery:completed:success", "discovery:completed");
					}

					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("discovery:completed:error", "discovery:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovery:completed:timeout", "discovery:completed");
					}
				});

		mutex.waitFor(10 * 1000, "discovery:completed");
		assertTrue(mutex.isItemNotified("discovery:completed:success"));
		assertTrue(mutex.isItemNotified("discovery:identity:account:registered"));
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:push:0"));
	}

}
