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
package tigase.tests.mix;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;

import static tigase.tests.mix.TestCreate.CHANNEL_NAME;

public class TestDestroy
		extends AbstractTest {

	private Jaxmpp adminJaxmpp;
	private Jaxmpp userJaxmpp;
	private JID mixJID;

	@BeforeClass
	public void setUp() throws Exception {
		adminJaxmpp = getJaxmppAdmin();
		this.mixJID = TestDiscovery.findMIXComponent(adminJaxmpp);

		this.userJaxmpp = createAccount().setLogPrefix("user1").build().createJaxmpp().setConnected(true).build();
	}

	@Test
	public void testDestroyByOther() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "destroy-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("destroy")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", CHANNEL_NAME);
		Response result = sendRequest(userJaxmpp, (IQ) Stanza.create(request.getElement()));
		AssertJUnit.assertTrue(result instanceof Response.Error);
	}

	@Test(dependsOnMethods = {"testDestroyByOther"})
	public void testDestroyByOwner() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "destroy-02")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("destroy")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", CHANNEL_NAME);
		Response result = sendRequest(adminJaxmpp, (IQ) Stanza.create(request.getElement()));
		AssertJUnit.assertTrue(result instanceof Response.Success);
	}

}
