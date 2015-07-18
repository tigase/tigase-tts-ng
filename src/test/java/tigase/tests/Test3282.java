/*
 * Test3282.java
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

import java.nio.charset.Charset;
import org.testng.Assert;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;
import static tigase.TestLogger.log;
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

/**
 *
 * @author andrzej
 */
public class Test3282 extends AbstractTest {
	
	final String helloWorld = new String(new byte[] { (byte) 0xe4, (byte) 0xbd, (byte) 0xa0, (byte) 0xe5, (byte) 0xa5,
			(byte) 0xbd, (byte) 0xe4, (byte) 0xb8, (byte) 0x96, (byte) 0xe7, (byte) 0x95, (byte) 0x8c },
			Charset.forName("UTF-8"));

	private static final String XMLNS = "urn:ietf:params:xml:ns:vcard-4.0";

	protected void fail(String msg) {
		log(msg);
		Assert.fail(msg);
	}

	@Test(groups = { "Phase 1" }, description = "Test VCard4 - VCardTemp conversion integration")
	public void testVCard4ToVCardTempConversion() throws JaxmppException, InterruptedException {
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

		iqRetrieve = ElementFactory.create("iq");
		iqRetrieve.setAttribute("type", "get");
		iqRetrieve.setAttribute("to", user1JID.toString());
		iqRetrieve.addChild(ElementFactory.create("vCard", null, "vcard-temp"));
		jaxmpp2.send((IQ) Stanza.create(iqRetrieve), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCard4 retrieval by buddy returned error = " + error);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					if (responseStanza.getType() == StanzaType.result) {
						Element vcard = responseStanza.getChildrenNS("vCard", "vcard-temp");
						Element fn = vcard.getChildren("FN").get(0);
						if (fn.getValue().equals("Test Example 1" + helloWorld))
							mutex.notify("vcardTempRetrieve:jaxmpp2");
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

		mutex.waitFor(1000 * 20, "vcardTempRetrieve:jaxmpp2");

		assertTrue("VCardTemp retrieval by buddy failed", mutex.isItemNotified("vcardTempRetrieve:jaxmpp2"));

		
		jaxmpp1.disconnect();
		jaxmpp2.disconnect();
	}
	
	@Test(groups = { "Phase 1" }, description = "Test VCardTemp - VCard4 conversion integration")
	public void testVCardTempToVCard4Conversion() throws JaxmppException, InterruptedException {
		final BareJID user1JID = createUserAccount("user1");
		final BareJID user2JID = createUserAccount("user2");
		final Jaxmpp jaxmpp1 = createJaxmpp("user1", user1JID);
		final Jaxmpp jaxmpp2 = createJaxmpp("user2", user2JID);

		jaxmpp1.login(true);
		jaxmpp2.login(true);

		Element iqPublish = ElementFactory.create("iq");
		iqPublish.setAttribute("type", "set");
		Element vcard = ElementFactory.create("vCard", null, "vcard-temp");
		Element fn = ElementFactory.create("FN", "Test Example 1" + helloWorld, null);
		vcard.addChild(fn);
		iqPublish.addChild(vcard);

		final Mutex mutex = new Mutex();

		jaxmpp1.send((IQ) Stanza.create(iqPublish), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCardTemp publication returned error = " + error);
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

		assertTrue("VCardTemp publication failed", mutex.isItemNotified("vcardSet:jaxmpp1"));

		Element iqRetrieve = ElementFactory.create("iq");
		iqRetrieve.setAttribute("type", "get");
		iqRetrieve.addChild(ElementFactory.create("vCard", null, "vcard-temp"));
		jaxmpp1.send((IQ) Stanza.create(iqRetrieve), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCard4 retrieval by owner returned error = " + error);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					if (responseStanza.getType() == StanzaType.result) {
						Element vcard = responseStanza.getChildrenNS("vCard", "vcard-temp");
						Element fn = vcard.getChildren("FN").get(0);
						if (fn.getValue().equals("Test Example 1" + helloWorld))
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

		assertTrue("VCardTemp retrieval by owner failed", mutex.isItemNotified("vcardRetrieve:jaxmpp1"));

		iqRetrieve = ElementFactory.create("iq");
		iqRetrieve.setAttribute("type", "get");
		iqRetrieve.setAttribute("to", user1JID.toString());
		iqRetrieve.addChild(ElementFactory.create("vCard", null, "vcard-temp"));
		jaxmpp2.send((IQ) Stanza.create(iqRetrieve), new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				fail("VCardTemp retrieval by buddy returned error = " + error);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					if (responseStanza.getType() == StanzaType.result) {
						Element vcard = responseStanza.getChildrenNS("vCard", "vcard-temp");
						Element fn = vcard.getChildren("FN").get(0);
						if (fn.getValue().equals("Test Example 1" + helloWorld))
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

		assertTrue("VCardTemp retrieval by buddy failed", mutex.isItemNotified("vcardRetrieve:jaxmpp2"));

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
							mutex.notify("vcard4Retrieve:jaxmpp2");
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

		mutex.waitFor(1000 * 20, "vcard4Retrieve:jaxmpp2");

		assertTrue("VCard4 retrieval by buddy failed", mutex.isItemNotified("vcard4Retrieve:jaxmpp2"));

		
		jaxmpp1.disconnect();
		jaxmpp2.disconnect();
	}

}
