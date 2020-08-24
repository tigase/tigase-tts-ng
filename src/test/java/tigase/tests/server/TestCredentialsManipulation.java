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

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Collection;

import static org.testng.AssertJUnit.*;

public class TestCredentialsManipulation
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;
	private String randomUsername;

	private void addUsername(String username) throws JaxmppException, InterruptedException {
		JabberDataElement request = new JabberDataElement(XDataType.submit);
		request.addTextSingleField("jid", user.getJid().toString());
		request.addTextSingleField("credentialId", username);
		request.addTextSingleField("password", "123");
		JabberDataElement response = callAdHoc("auth-credentials-add", request);

		assertNotNull(response);
	}

	private JabberDataElement callAdHoc(String node, JabberDataElement data)
			throws JaxmppException, InterruptedException {
		final AdHocCommansModule adHoc = jaxmpp.getModule(AdHocCommansModule.class);
		final Mutex mutex = new Mutex();
		final Mutable<JabberDataElement> response = new MutableObject<>(null);
		adHoc.execute(JID.jidInstance(this.user.getJid().getDomain()), node, Action.execute, data,
					  new AdHocCommansModule.AdHocCommansAsyncCallback() {
						  @Override
						  public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
								  throws JaxmppException {
							  AssertJUnit.fail("error: " + errorCondition);
						  }

						  @Override
						  protected void onResponseReceived(String sessionid, String node, State status,
															JabberDataElement data) throws JaxmppException {
							  response.setValue(data);
							  mutex.notify("adhoc");
						  }

						  @Override
						  public void onTimeout() throws JaxmppException {
							  AssertJUnit.fail("Timeout");
						  }
					  });

		mutex.waitFor(30000, "adhoc");
		assertNotNull("There was no response to ad-hoc call, node: '" + node + "' with data" + data, response.getValue());

		return response.getValue();
	}

	private void deleteUsername(String username) throws JaxmppException, InterruptedException {
		JabberDataElement request = new JabberDataElement(XDataType.submit);
		request.addTextSingleField("jid", user.getJid().toString());
		request.addTextSingleField("credentialId", username);
		JabberDataElement response = callAdHoc("auth-credentials-delete", request);

		assertNotNull(response);
	}

	private Collection<String> getUsernames() throws Exception {
		JabberDataElement request = new JabberDataElement(XDataType.submit);
		request.addTextSingleField("jid", user.getJid().toString());
		JabberDataElement response = callAdHoc("auth-credentials-list", request);

		final ArrayList<String> names = new ArrayList<>();

		for (Element item : response.getChildren("item")) {
			Element f = item.getFirstChild("field");
			Assert.assertEquals("credentialId", f.getAttribute("var"));
			names.add(f.getFirstChild("value").getValue());
		}
		return names;
	}

	@BeforeClass
	public void prepareAccountAndJaXMPP() throws JaxmppException, InterruptedException {
		this.user = createAccount().setLogPrefix("user1").build();
		this.jaxmpp = user.createJaxmpp().setConnected(true).build();
	}

	@Test
	public void testRetrievingUsernames1() throws Exception {
		Collection<String> names = getUsernames();
		assertEquals(1, names.size());
		assertTrue(names.contains("default"));
	}

	@Test(dependsOnMethods = {"testRetrievingUsernames1"})
	public void testAddingCredentials() throws Exception {
		this.randomUsername = "bzz_" + nextRnd();
		addUsername(randomUsername);

		Collection<String> names = getUsernames();
		assertEquals(2, names.size());
		assertTrue(names.contains("default"));
		assertTrue(names.contains(randomUsername));
	}

	@Test(dependsOnMethods = {"testAddingCredentials"})
	public void testAuthenticationWithNewCredentials() throws JaxmppException {
		Jaxmpp jaxmpp = user.createJaxmpp().setConfigurator(j -> {
			j.getSessionObject().setUserProperty(AuthModule.LOGIN_USER_NAME_KEY, randomUsername);
			j.getConnectionConfiguration().setUserPassword("123");
			return j;
		}).setConnected(true).build();
		assertTrue(jaxmpp.isConnected());
		jaxmpp.disconnect(true);
	}

	@Test(dependsOnMethods = {"testAuthenticationWithNewCredentials"})
	public void testDeletingCredentials() throws Exception {
		deleteUsername(randomUsername);
		Collection<String> names = getUsernames();
		assertEquals(1, names.size());
		assertTrue(names.contains("default"));
	}

}
