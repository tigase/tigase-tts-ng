/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2019 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.tests.server;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.StreamFeaturesModule;
import tigase.jaxmpp.core.client.xmpp.modules.capabilities.CapabilitiesModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.List;

import static tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule.INFO_XMLNS;

public class StreamCaps
		extends AbstractTest {

	private final Mutex mutex = new Mutex();
	private Jaxmpp clientJaxmpp;
	private Account clientUser;

	@BeforeClass
	public void setUp() throws Exception {
		clientUser = createAccount().setLogPrefix("caps").build();
		clientJaxmpp = clientUser.createJaxmpp().setConnected(true).build();
	}

	@Test(groups = {"features"})
	public void testComponentDiscovery() throws JaxmppException, InterruptedException {
		final Element streamFeatures = StreamFeaturesModule.getStreamFeatures(clientJaxmpp.getSessionObject());
		TestLogger.log("stream features: " + streamFeatures.getAsString());

		final Element c = streamFeatures.getChildrenNS("c", "http://jabber.org/protocol/caps");
		Assert.assertNotNull(c);

		final String streamCaps = c.getAttribute("ver");
		Assert.assertNotNull(streamCaps);

		final DiscoveryModule discovery = clientJaxmpp.getModule(DiscoveryModule.class);
		final String domain = clientUser.getJid().getDomain();

		discovery.getInfo(JID.jidInstance(domain), null, new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {

				mutex.notify("disco:info");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				Element query = stanza.getChildrenNS("query", INFO_XMLNS);
				List<Element> ids = query.getChildren("identity");
				final String[] identities = ids.stream().map(id -> {
					return getAttribute(id, "category") + "/" + getAttribute(id, "type") + "/" +
							getAttribute(id, "lang") + "/" + getAttribute(id, "name");
				}).toArray(String[]::new);

				List<Element> feats = query.getChildren("feature");
				final String[] features = feats.stream().map(el -> getAttribute(el, "var")).toArray(String[]::new);

				final Element x = query.getChildrenNS("x", "jabber:x:data");

				final JabberDataElement jde = new JabberDataElement(x);

				final String caps = CapabilitiesModule.generateVerificationString(identities, features, jde);

				TestLogger.log("disco#info calculated CAPS: " + caps);

				mutex.notify("disco:info", "caps:" + caps);

			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:info");
			}
		});

		mutex.waitFor(10 * 1000, "disco:info");
		Assert.assertTrue(mutex.isItemNotified("caps:" + streamCaps));

	}

	private String getAttribute(Element id, String category) {
		try {
			return id.getAttribute(category) != null ? id.getAttribute(category) : "";
		} catch (XMLException e) {
			return "";
		}
	}

}
