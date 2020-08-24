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
package tigase.tests;

import org.testng.annotations.Test;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.utils.Account;

import static org.testng.Assert.assertTrue;
import static tigase.TestLogger.log;

public class ExampleJaxmppTest
		extends AbstractTest {

	@Test(groups = {"examples"}, description = "Simple test verifying logging in by the user")
	public void SimpleLoginTest() {

		try {
			log("This is test case");

			Jaxmpp contact = getAdminAccount().createJaxmpp().setConnected(true).build();

			assertTrue(contact.isConnected(), "contact was not connected");

			if (contact.isConnected()) {
				contact.disconnect();
			}

			Account createUserAccount = createAccount().setLogPrefix("test_user").build();
			Jaxmpp createJaxmpp = createUserAccount.createJaxmpp().build();
			createJaxmpp.login(true);

			assertTrue(createJaxmpp.isConnected(), "contact was not connected");

		} catch (Exception e) {
			fail(e);
		}
	}

}
