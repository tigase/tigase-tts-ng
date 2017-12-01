/*
 * TestNonSaslAuthentication.java
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
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.utils.Account;

import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class TestNonSaslAuthentication
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("jaxmpp_").build();
	}

	@Test
	public void testAuth() throws JaxmppException {
		jaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			SaslModule saslModule = jaxmpp.getModule(SaslModule.class);
			if (saslModule != null) {
				jaxmpp.getModulesManager().unregister(saslModule);
			}
			return jaxmpp;
		}).build();

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testAuthFailure() throws JaxmppException {
		jaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			SaslModule saslModule = jaxmpp.getModule(SaslModule.class);
			if (saslModule != null) {
				jaxmpp.getModulesManager().unregister(saslModule);
			}
			return jaxmpp;
		}).build();

		jaxmpp.getConnectionConfiguration().setUserPassword("DUMMY_PASSWORD");
		try {
			jaxmpp.login(true);
		} catch (JaxmppException ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

}