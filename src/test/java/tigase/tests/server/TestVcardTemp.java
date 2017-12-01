/*
 * TestVcardTemp.java
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

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCard;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import static org.testng.AssertJUnit.assertTrue;

public class TestVcardTemp
		extends AbstractTest {

	private Jaxmpp jaxmpp1;

	private Account user1;

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		user1 = createAccount().setLogPrefix("user1").build();
		jaxmpp1 = user1.createJaxmpp().setConnected(true).build();
	}

	@Test(groups = {"Phase 1"}, description = "Retrieve and set vcard-temp")
	public void testRetrieveAndSetVCardTemp() throws Exception {
		final Mutex mutex = new Mutex();
		final VCardModule module = jaxmpp1.getModule(VCardModule.class);

		module.retrieveVCard(JID.jidInstance(user1.getJid()), new VCardModule.VCardAsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("retrieve");
			}

			@Override
			protected void onVCardReceived(VCard vCard) throws XMLException {
				mutex.notify("retrieved", "retrieve");

			}
		});
		mutex.waitFor(1000 * 20, "retrieve");

		assertTrue("Cannot retrieve vcard-temp", mutex.isItemNotified("retrieved"));

		// Trying to set vcard-temp with empty photo type and phot val. (Related to Bug #6293)
		VCard vCard = new VCard();
		vCard.setFullName("Tester Testerowsky");
		vCard.setPhotoType(null);
		vCard.setPhotoVal(null);

		final IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.addChild(vCard.makeElement());

		sendAndWait(jaxmpp1, iq, new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				Assert.fail("Cannot set vcard-temp");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {

			}

			@Override
			public void onTimeout() throws JaxmppException {
				Assert.fail("Timeout during setting vcard-temp");

			}
		});

	}

}
