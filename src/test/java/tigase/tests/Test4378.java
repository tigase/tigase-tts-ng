/*
 * Test4378.java
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
package tigase.tests;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Created by andrzej on 17.09.2016.
 */
public class Test4378 extends AbstractJaxmppTest {

	private BareJID userJID;
	private Jaxmpp jaxmpp;

	@BeforeMethod
	protected void setUp() throws Exception {
		userJID = createUserAccount("jaxmpp_");
		jaxmpp = createJaxmppInstance(userJID);
		super.setUp();
	}

	@Test
	public void testStateAfterAuthTimeoutWebSocket() throws Exception {
		testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType.websocket);
	}

	@Test
	public void testStateAfterAuthTimeoutBosh() throws Exception {
		testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType.bosh);
	}

	@Test
	public void testStateAfterAuthTimeoutSocket() throws Exception {
		testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType.socket);
	}

	private void testStateAfterAuthTimeout(ConnectionConfiguration.ConnectionType connectionType) throws Exception {
		jaxmpp.getConnectionConfiguration().setConnectionType(connectionType);

		AuthModule authModule = jaxmpp.getModulesManager().getModule(AuthModule.class);
		jaxmpp.getModulesManager().unregister(authModule);

		AuthModule dummyAuthModule = new AuthModule() {
			@Override
			public void login() throws JaxmppException {

			}
		};
		jaxmpp.getModulesManager().register(dummyAuthModule);
		Field f = jaxmpp.getModulesManager().getClass().getDeclaredField("modulesByClasses");
		f.setAccessible(true);
		((HashMap) f.get(jaxmpp.getModulesManager())).put(AuthModule.class, dummyAuthModule);

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

		try {
			jaxmpp.login(true);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		assertEquals(jaxmpp.getConnector().getState(), Connector.State.disconnected);

		jaxmpp.getModulesManager().unregister(dummyAuthModule);
		jaxmpp.getModulesManager().register(authModule);

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());

		jaxmpp.disconnect();
	}

	@Test
	public void testStateAfterConnectionFailureWebSocket() throws Exception {
		testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType.websocket);
	}

	@Test
	public void testStateAfterConnectionFailureBosh() throws Exception {
		testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType.bosh);
	}

	@Test
	public void testStateAfterConnectionFailureSocket() throws Exception {
		testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType.socket);
	}

	private void testStateAfterConnectionFailure(ConnectionConfiguration.ConnectionType connectionType) throws Exception {
		jaxmpp.getConnectionConfiguration().setConnectionType(connectionType);

		switch (connectionType) {
			case websocket:
				jaxmpp.getConnectionConfiguration().setBoshService("ws://missing/");
				break;
			case bosh:
				jaxmpp.getConnectionConfiguration().setBoshService("bosh://missing/");
				break;
			case socket:
				jaxmpp.getConnectionConfiguration().setServer("missing");
				break;
		}

		try {
			jaxmpp.login(true);
		} catch (Exception ex) {

		}

		assertEquals(jaxmpp.getConnector().getState(), Connector.State.disconnected);

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

		jaxmpp.getSessionObject().clear();

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());

		jaxmpp.disconnect();
	}

}
