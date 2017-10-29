/*
 * Test4266.java
 *
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
package tigase.tests.jaxmpp;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.utils.Account;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Created by andrzej on 17.09.2016.
 */
public class TestReconnections
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("jaxmpp_").build();
		jaxmpp = user.createJaxmpp().build();
	}

	@Test
	public void testMultipleReconnectionsWebSocket() throws Exception {
		testMultipleReconnections(ConnectionConfiguration.ConnectionType.websocket);

	}

	@Test
	public void testMultipleReconnectionsBosh() throws Exception {
		testMultipleReconnections(ConnectionConfiguration.ConnectionType.bosh);

	}

	@Test
	public void testMultipleReconnectionsSocket() throws Exception {
		testMultipleReconnections(ConnectionConfiguration.ConnectionType.socket);

	}

	private void testMultipleReconnections(ConnectionConfiguration.ConnectionType connectionType) throws Exception {
		jaxmpp.getConnectionConfiguration().setConnectionType(connectionType);

		switch (connectionType) {
			case websocket:
				jaxmpp.getConnectionConfiguration().setBoshService(getWebSocketURI());
				break;
			case bosh:
				jaxmpp.getConnectionConfiguration().setBoshService(getBoshURI());
				break;
			case socket:
				jaxmpp.getConnectionConfiguration().setServer(getInstanceHostname());
				break;
		}

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());

		Thread.sleep(2000);

		assertTrue(jaxmpp.isConnected());

		jaxmpp.disconnect(true);

		assertFalse(jaxmpp.isConnected());

		Thread.sleep(2000);

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());

		Thread.sleep(2000);

		assertTrue(jaxmpp.isConnected());
	}

}
