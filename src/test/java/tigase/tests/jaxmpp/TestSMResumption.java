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
package tigase.tests.jaxmpp;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.XmppModule;
import tigase.jaxmpp.core.client.connector.ConnectorWrapper;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.j2se.ConnectionConfiguration;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.utils.Account;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

public class TestSMResumption extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("jaxmpp_").build();
		jaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new TestModule());
			return jaxmpp;
		}).setResource("test-sm").build();
	}

	@Test
	public void testSMResumption() throws Exception {
		jaxmpp.getConnectionConfiguration().setConnectionType(ConnectionConfiguration.ConnectionType.socket);
		jaxmpp.getConnectionConfiguration().setServer(getInstanceHostname());

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
		Thread.sleep(500);
		jaxmpp.getContext().getWriter().write(ElementFactory.create("enable", null, "tigase:test"));
		jaxmpp.getContext().getWriter().write(ElementFactory.create("enable", null, "tigase:test"));
		Thread.sleep(100);

		assertEquals(jaxmpp.getModule(TestModule.class).counter.get(), 2);

		assertTrue(jaxmpp.isConnected());

		Method method = SocketConnector.class.getDeclaredMethod("closeSocket");
		method.setAccessible(true);
		System.out.println(jaxmpp.getConnector());
		method.invoke(((ConnectorWrapper) jaxmpp.getConnector()).getConnector());
//		try {
//			jaxmpp.getContext().getWriter().write(ElementFactory.create("test", null, "tigase:test"));
//		} catch (Throwable ex) {
//			ex.printStackTrace();
//		}
		jaxmpp.disconnect(true,false);

		assertFalse(jaxmpp.isConnected());

		Thread.sleep(4000);

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());

		Thread.sleep(1000);
		assertEquals(jaxmpp.getModule(TestModule.class).counter.get(), 2);
		jaxmpp.getContext().getWriter().write(ElementFactory.create("enable", null, "tigase:test"));
		Thread.sleep(1000);
		assertEquals(jaxmpp.getModule(TestModule.class).counter.get(), 3);
	}

	public static class TestModule implements XmppModule {

		public final AtomicInteger counter = new AtomicInteger(0);

		@Override
		public Criteria getCriteria() {
			return ElementCriteria.name("enable");
		}

		@Override
		public String[] getFeatures() {
			return new String[0];
		}

		@Override
		public void process(Element element) throws XMPPException, XMLException, JaxmppException {
			if ("enable".equals(element.getName()) && "tigase:test".equals(element.getXMLNS())) {
				counter.incrementAndGet();
			}
		}

	}

}

