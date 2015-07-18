/*
 * Test2407_Task2410.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
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

import static org.testng.AssertJUnit.assertTrue;

import java.nio.charset.Charset;

import org.testng.Assert;
import org.testng.annotations.Test;

import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;

import static tigase.TestLogger.log;

/**
 *
 * @author andrzej
 */
public class Test2407_Task2410 extends AbstractTest {

	final String helloWorld = new String(new byte[] { (byte) 0xe4, (byte) 0xbd, (byte) 0xa0, (byte) 0xe5, (byte) 0xa5,
			(byte) 0xbd, (byte) 0xe4, (byte) 0xb8, (byte) 0x96, (byte) 0xe7, (byte) 0x95, (byte) 0x8c },
			Charset.forName("UTF-8"));

	private static final String XMLNS = "urn:ietf:params:xml:ns:vcard-4.0";

	protected void fail(String msg) {
		log(msg);
		Assert.fail(msg);
	}

	@Test(groups = { "Phase 1" }, description = "Test VCard4 support - XEP-0292")
	public void testVCard4SupportXEP0292() throws JaxmppException, InterruptedException {
		final BareJID user1JID = createUserAccount("user1");
		final BareJID user2JID = createUserAccount("user2");
		final Jaxmpp jaxmpp1 = createJaxmpp("user1", user1JID);
		final Jaxmpp jaxmpp2 = createJaxmpp("user2", user2JID);

		jaxmpp1.login(true);
		jaxmpp2.login(true);

		Element iqPublish = ElementFactory.create("iq");
		iqPublish.setAttribute("type", "set");
		Element vcard = ElementFactory.create("vcard", null, XMLNS);
		Element fn = ElementFactory.create("fn");
		fn.addChild(ElementFactory.create("text", "Test Example 1" + helloWorld, null));
		vcard.addChild(fn);
		iqPublish.addChild(vcard);

		final Mutex mutex = new Mutex();

		jaxmpp1.send((IQ) Stanza.create(iqPublish), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCard4 publication returned error = " + error);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					if (responseStanza.getType() == StanzaType.result)
						mutex.notify("vcardSet:jaxmpp1");
				} catch (Exception e) {
					fail(e);
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); // To
																				// change
																				// body
																				// of
																				// generated
																				// methods,
																				// choose
																				// Tools
																				// |
																				// Templates.
			}
		});

		mutex.waitFor(1000 * 20, "vcardSet:jaxmpp1");

		assertTrue("VCard4 publication failed", mutex.isItemNotified("vcardSet:jaxmpp1"));

		Element iqRetrieve = ElementFactory.create("iq");
		iqRetrieve.setAttribute("type", "get");
		iqRetrieve.addChild(ElementFactory.create("vcard", null, XMLNS));
		jaxmpp1.send((IQ) Stanza.create(iqRetrieve), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCard4 retrieval by owner returned error = " + error);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					if (responseStanza.getType() == StanzaType.result) {
						Element vcard = responseStanza.getChildrenNS("vcard", XMLNS);
						Element fn = vcard.getChildren("fn").get(0);
						Element fnText = fn.getChildren("text").get(0);
						if (fnText.getValue().equals("Test Example 1" + helloWorld))
							mutex.notify("vcardRetrieve:jaxmpp1");
					}
				} catch (Exception e) {
					fail(e);
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); // To
																				// change
																				// body
																				// of
																				// generated
																				// methods,
																				// choose
																				// Tools
																				// |
																				// Templates.
			}
		});

		mutex.waitFor(1000 * 20, "vcardRetrieve:jaxmpp1");

		assertTrue("VCard4 retrieval by owner failed", mutex.isItemNotified("vcardRetrieve:jaxmpp1"));

		iqRetrieve = ElementFactory.create("iq");
		iqRetrieve.setAttribute("type", "get");
		iqRetrieve.setAttribute("to", user1JID.toString());
		iqRetrieve.addChild(ElementFactory.create("vcard", null, XMLNS));
		jaxmpp2.send((IQ) Stanza.create(iqRetrieve), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCard4 retrieval by buddy returned error = " + error);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					if (responseStanza.getType() == StanzaType.result) {
						Element vcard = responseStanza.getChildrenNS("vcard", XMLNS);
						Element fn = vcard.getChildren("fn").get(0);
						Element fnText = fn.getChildren("text").get(0);
						if (fnText.getValue().equals("Test Example 1" + helloWorld))
							mutex.notify("vcardRetrieve:jaxmpp2");
					}
				} catch (Exception e) {
					fail(e);
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet."); // To
																				// change
																				// body
																				// of
																				// generated
																				// methods,
																				// choose
																				// Tools
																				// |
																				// Templates.
			}
		});

		mutex.waitFor(1000 * 20, "vcardRetrieve:jaxmpp2");

		assertTrue("VCard4 retrieval by buddy failed", mutex.isItemNotified("vcardRetrieve:jaxmpp2"));

		jaxmpp1.disconnect();
		jaxmpp2.disconnect();
	}
}
