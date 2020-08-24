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
package tigase.tests.server;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.UUID;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class TestPasswordChange
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("jaxmpp_").build();
	}

	@Test
	public void testPasswordChange() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();
		jaxmpp = user.createJaxmpp().setConnected(true).build();
		assertTrue(jaxmpp.isConnected());

		InBandRegistrationModule module = jaxmpp.getModulesManager().getModule(InBandRegistrationModule.class);
		module.register(user.getJid().getLocalpart(), "password-" + UUID.randomUUID(), null, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("password:changed");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				assertEquals(StanzaType.result, responseStanza.getType());
				if (responseStanza.getType() == StanzaType.result) {
					mutex.notify("password:changed:success", "password:changed");
				} else {
					mutex.notify("password:changed");
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("password:changed");
			}
		});

		mutex.waitFor(30 * 1000, "password:changed");
		assertTrue(mutex.isItemNotified("password:changed:success"));
	}

	@Test(dependsOnMethods = {"testPasswordChange"})
	public void testAuthenticationAfterPasswordChange() throws JaxmppException {
		jaxmpp = user.createJaxmpp().build();

		jaxmpp.login(true);
		assertTrue(jaxmpp.isConnected());
	}
}
