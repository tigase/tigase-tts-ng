/*
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
package tigase.tests.server.adhoc;

import org.testng.Assert;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.FixedField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

public class TestChangeUserPassword
		extends AbstractTest {

	@Test(groups = {"ad hoc"}, description = "Admin ad-hoc: Change User Password")
	public void testAdHocChangeUserPassword() throws Exception {
		final Mutex mutex = new Mutex();

		Jaxmpp admin = getAdminAccount().createJaxmpp().setConnected(true).build();

		final String domain = ResourceBinderModule.getBindedJID(admin.getSessionObject()).getDomain();

		Account userRegular = createAccount().setLogPrefix("all-xmpp-test")
				.setUsername("all-xmpp-test" + nextRnd())
				.setDomain(domain)
				.build();
		BareJID userJID = userRegular.getJid();
		final Jaxmpp userRegularJaxmpp = userRegular.createJaxmpp().setConnected(true).build();

		AdHocCommansModule adHoc = admin.getModule(AdHocCommansModule.class);

		final JID sessManJID = JID.jidInstance("sess-man", domain);

		TestLogger.log("Executing command");

		final JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addFORM_TYPE("http://jabber.org/protocol/admin");
		form.addJidSingleField("accountjid", JID.jidInstance(userJID));
		form.addTextPrivateField("password", "password");

		adHoc.execute(sessManJID, "http://jabber.org/protocol/admin#change-user-password", null, form,

					  new AdHocCommansModule.AdHocCommansAsyncCallback() {

						  @Override
						  public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
								  throws JaxmppException {
							  TestLogger.log("Error! " + error);
							  mutex.notify("addUser", "addUser:error");
						  }

						  @Override
						  public void onTimeout() throws JaxmppException {
							  mutex.notify("addUser", "addUser:timeout");
						  }

						  @Override
						  protected void onResponseReceived(String sessionid, String node, State status,
															JabberDataElement data) throws JaxmppException {

							  FixedField nff = data.getField("Note");
							  if (nff != null) {
								  mutex.notify("addUser:success");
							  }
							  mutex.notify("addUser");
						  }
					  });

		mutex.waitFor(30 * 1000, "addUser");
		Assert.assertTrue(mutex.isItemNotified("addUser:success"), "User added correctly!");
	}

}
