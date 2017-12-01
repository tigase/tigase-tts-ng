/*
 * TestSaslAuthentication.java
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
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslMechanism;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.saslmechanisms.PlainMechanism;
import tigase.jaxmpp.core.client.xmpp.modules.auth.scram.ScramMechanism;
import tigase.jaxmpp.core.client.xmpp.modules.auth.scram.ScramSHA256Mechanism;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.utils.Account;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class TestSaslAuthentication
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	private static Jaxmpp configureJaxmpp(Jaxmpp jaxmpp, SaslMechanism mechanism) {
		SaslModule module = jaxmpp.getModule(SaslModule.class);

		module.removeAllMechanisms();
		module.addMechanism(mechanism);

		return jaxmpp;
	}

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("jaxmpp_").build();
	}

	@Test
	public void testSaslPlain() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new PlainMechanism());
		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testSaslPlainWithAuthzid() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new PlainMechanism());
		forceAuthzid();
		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testSaslPlainFailure() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new PlainMechanism());
		jaxmpp.getConnectionConfiguration().setUserPassword("DUMMY_PASSWORD");
		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

	@Test
	public void testSaslPlainWithAuthzidFailure() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new PlainMechanism());
		forceAuthzid();
		jaxmpp.getSessionObject().setUserProperty(AuthModule.LOGIN_USER_NAME_KEY, "some-user");
		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA1() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramMechanism());
		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA1WithAuthzid() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramMechanism());
		forceAuthzid();
		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA1Failure() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramMechanism());
		jaxmpp.getConnectionConfiguration().setUserPassword("DUMMY_PASSWORD");
		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA1WithAuthzidFailure() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramMechanism());
		forceAuthzid();
		jaxmpp.getSessionObject().setUserProperty(AuthModule.LOGIN_USER_NAME_KEY, "some-user");
		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA256() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramSHA256Mechanism());
		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA256WithAuthzid() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramSHA256Mechanism());
		forceAuthzid();
		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA256Failure() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramSHA256Mechanism());
		jaxmpp.getConnectionConfiguration().setUserPassword("DUMMY_PASSWORD");
		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

	@Test
	public void testSaslScramSHA256WithAuthzidFailure() throws JaxmppException {
		jaxmpp = createJaxmppWithMechanism(new ScramSHA256Mechanism());
		forceAuthzid();
		jaxmpp.getSessionObject().setUserProperty(AuthModule.LOGIN_USER_NAME_KEY, "some-user");
		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
		}

		assertFalse(jaxmpp.isConnected());
	}

	private void forceAuthzid() {
		jaxmpp.getSessionObject().setUserProperty(SaslMechanism.FORCE_AUTHZID, true);
	}

	private Jaxmpp createJaxmppWithMechanism(SaslMechanism mechanism) throws JaxmppException {
		return user.createJaxmpp().setConfigurator(it -> configureJaxmpp(it, mechanism)).build();
	}
}
