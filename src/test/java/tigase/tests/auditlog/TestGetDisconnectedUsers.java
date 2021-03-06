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
package tigase.tests.auditlog;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestGetDisconnectedUsers
		extends AbstractAuditlogTest {

	private static final String USER_PREFIX = "auditlog-";
	private static Date start = new Date();
	
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeClass
	public void setUp() throws Exception {
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();
	}

	@Test
	public void disconnectUser1() throws JaxmppException, InterruptedException {
		user2Jaxmpp.disconnect(true);
		Thread.sleep(1000);
	}

	@Test(dependsOnMethods = {"disconnectUser1"})
	public void retrieveDisconnectedUsers1() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), null);

		assertFalse(existsInResults(user1.getJid(), results));
		assertTrue(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveDisconnectedUsers1"})
	public void retrieveDisconnectedUsers1WithFilter() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), USER_PREFIX);

		assertFalse(existsInResults(user1.getJid(), results));
		assertTrue(existsInResults(user2.getJid(), results));

		results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), "non-auditlog-");

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveDisconnectedUsers1WithFilter"})
	public void loginUser2() throws JaxmppException {
		user2Jaxmpp.login(true);
	}

	@Test(dependsOnMethods = {"loginUser2"})
	public void retrieveDisconnectedUsers2() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), null);

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveDisconnectedUsers2"})
	public void retrieveDisconnectedUsers2WithFilter() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), USER_PREFIX);

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));

		results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), "non-auditlog-");

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveDisconnectedUsers2WithFilter"})
	public void disconnectUser3() throws JaxmppException {
		user2Jaxmpp.disconnect(true);
	}

	@Test(dependsOnMethods = {"disconnectUser3"})
	public void retrieveDisconnectedUsers3() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), null);

		assertFalse(existsInResults(user1.getJid(), results));
		assertTrue(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveDisconnectedUsers3"})
	public void retrieveDisconnectedUsers3WithFilter() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), USER_PREFIX);

		assertFalse(existsInResults(user1.getJid(), results));
		assertTrue(existsInResults(user2.getJid(), results));

		results = getDisconnectedUsers(mutex, user1.getJid().getDomain(), "non-auditlog-");

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	private List<Element> getDisconnectedUsers(Mutex mutex, String domain, String userLike)
			throws JaxmppException, InterruptedException {
		String id = UUID.randomUUID().toString();

		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addListSingleField("domain", domain);
		if (userLike != null) {
			form.addTextSingleField("jidLike", userLike);
		}
		List<Element> results = new ArrayList<>();
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(getComponentJID(), "get-disconnected-users",
																Action.execute, form, new AdHocCommansModule.AdHocCommansAsyncCallback() {

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
						mutex.notify(id + ":get-disconnected-users:failure:" + error.getElementName());
						mutex.notify(id + ":get-disconnected-users:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify(id + ":get-disconnected-users:failure:timeout");
						mutex.notify(id + ":get-disconnected-users:completed");
					}

					@Override
					protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
							throws JaxmppException {
						mutex.notify(id + ":get-disconnected-users:success");
						List<Element> children = data.getChildren();
						boolean started = false;
						boolean ended = false;
						for (int i=0; i<children.size(); i++) {
							Element el = children.get(i);
							if (started && !ended) {
								if ("item".equals(el.getName())) {
									results.add(el);
								} else {
									ended = true;
								}
							} else if (!started) {
								if ("reported".equals(el.getName()) && "Disconnected users".equals(el.getAttribute("label"))) {
									started = true;
								}
							}
						}
						mutex.notify(id + ":get-disconnected-users:completed");
					}
				});

		mutex.waitFor(30 * 1000, id + ":get-disconnected-users:completed");
		assertTrue(mutex.isItemNotified(id + ":get-disconnected-users:success"));

		return results;
	}

	private boolean existsInResults(BareJID jid, List<Element> results) {
		return results.stream().filter(el -> {
			try {
				return jid.toString().equals(el.getFirstChild("field").getFirstChild("value").getValue());
			} catch (XMLException ex) {
				throw new RuntimeException(ex);
			}
		}).findAny().isPresent();
	}

}
