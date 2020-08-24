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
package tigase.tests.server;

import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.testng.Assert.assertTrue;

public class TestServiceDiscoveryExtensions extends AbstractJaxmppTest {

	@Test
	public void testDiscoveryAdmin() throws InterruptedException, JaxmppException {
		final Mutex mutex = new Mutex();
		Jaxmpp jaxmpp = getJaxmppAdmin();

		testDiscovery(mutex, jaxmpp);
	}

	@Test
	public void testDiscoveryUser() throws InterruptedException, JaxmppException {
		final Mutex mutex = new Mutex();
		Jaxmpp jaxmpp = createAccount().setLogPrefix("queryuser").build().createJaxmpp().setConnected(true).build();

		testDiscovery(mutex, jaxmpp);
	}

	private void testDiscovery(final Mutex mutex, final Jaxmpp jaxmpp) throws InterruptedException, JaxmppException {
		DiscoveryModule discoveryModule = jaxmpp.getModule(DiscoveryModule.class);
		discoveryModule.getInfo(JID.jidInstance(jaxmpp.getSessionObject().getUserBareJid().getDomain()), null, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("disco:info:retrieve:error:" + error, "disco:info:retrieve");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("disco:info:retrieve:success");
				Element query = responseStanza.getFirstChild("query");
				if (query != null) {
					Element x = query.getFirstChild("x");
					if (x != null) {
						List<Element> fields = x.getChildren("field");
						if (fields != null) {
							for (Element field : fields) {
								mutex.notify("disco:info:field:" + field.getAttribute("var") + "=" + ((Collection<Element>) Optional.ofNullable(field.getChildren("value")).orElse(
										Collections.EMPTY_LIST)).stream().map(el -> {
									try {
										return el.getValue();
									} catch (XMLException e) {
										e.printStackTrace();
										return e.getMessage();
									}
								}).collect(Collectors.joining(",")));
							}
						}
					}
				}

				mutex.notify("disco:info:retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("disco:info:retrieve:timeout", "disco:info:retrieve");
			}
		});

		mutex.waitFor(10 * 1000, "disco:info:retrieve");
		assertTrue(mutex.isItemNotified("disco:info:retrieve:success"));

		assertTrue(mutex.isItemNotified("disco:info:field:abuse-addresses=mailto:abuse@" + getDomain() + ",xmpp:abuse@" + getDomain()));
	}
}
