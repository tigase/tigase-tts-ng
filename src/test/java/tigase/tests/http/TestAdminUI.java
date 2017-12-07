/*
 * TestAdminUI.java
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
package tigase.tests.http;

import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.utils.Account;
import tigase.util.stringprep.TigaseStringprepException;

import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import static org.testng.Assert.*;

/**
 * @author andrzej
 */
public class TestAdminUI
		extends AbstractTest {

	private DefaultCredentialsProvider credentialProvider;
	private WebClient webClient;

	@BeforeMethod
	public void setUp() throws Exception {
		credentialProvider = new DefaultCredentialsProvider();
		credentialProvider.addCredentials(getAdminAccount().getJid().toString(), getAdminAccount().getPassword());
		webClient = new WebClient();
		webClient.setCredentialsProvider(credentialProvider);
	}

	@AfterMethod
	public void cleanUp() {
		credentialProvider = null;
		webClient = null;
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check authorization requirement")
	public void testAuthorization() throws IOException {
		System.out.println("##### Check authorization requirement");
		WebClient webClient = new WebClient();
		try {
			Page p = webClient.getPage(getAdminUrl());
			assertNull(p);
		} catch (FailingHttpStatusCodeException ex) {
			assertEquals(ex.getStatusCode(), 401);
		}
		webClient.setCredentialsProvider(credentialProvider);
		Page p = webClient.getPage(getAdminUrl());
		assertNotNull(p);
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check 'Users' contains valid commands")
	public void testCommandsAvailability() throws IOException {
		System.out.println("##### Check 'Users' contains valid commands");
		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor getUserInfoLink = p.getAnchorByText("Get User Info");
		HtmlAnchor addUserLink = p.getAnchorByText("Add user");
		HtmlAnchor changeUserPasswordLink = p.getAnchorByText("Change user password");
		HtmlAnchor deleteUserLink = p.getAnchorByText("Delete user");
		HtmlAnchor modifyUserLink = p.getAnchorByText("Modify user");
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check adding user")
	public void testAddUser() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check adding user");
		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor addUserLink = p.getAnchorByText("Add user");
		p = addUserLink.click();
		HtmlForm form = p.getForms().get(0);
		HtmlInput accountJid = form.getInputByName("accountjid");
		HtmlInput password1 = form.getInputByName("password");
		HtmlInput password2 = form.getInputByName("password-verify");
		HtmlInput email = form.getInputByName("email");

		BareJID jid = randomUserJid();
		accountJid.setValueAttribute(jid.toString());
		password1.setValueAttribute(jid.getLocalpart());
		password2.setValueAttribute(jid.getLocalpart());
		email.setValueAttribute("email+" + jid.toString());
		HtmlSubmitInput button = form.getInputByName("submit");

		p = button.click();
		form = p.getForms().get(0);
		form.getInputByValue("Operation successful");

		Account user1 = createAccount().setUsername(jid.getLocalpart())
				.setDomain(jid.getDomain())
				.setRegister(false)
				.build();
		accountManager.add(user1);
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check delete user")
	public void testDeleteUser() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check delete user");
		Account user1 = createAccount().setLogPrefix(getUserPrefix()).build();

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor delUserLink = p.getAnchorByText("Delete user");
		p = delUserLink.click();

		HtmlForm form = p.getForms().get(0);
		HtmlTextArea accountJids = form.getTextAreaByName("accountjids");
		accountJids.setText(user1.getJid().toString());

		HtmlSubmitInput button = form.getInputByName("submit");

		p = button.click();
		form = p.getForms().get(0);
		HtmlTextArea notes = form.getTextAreaByName("Notes");
		assertTrue(notes.getText().contains("Operation successful for user " + user1.getJid().toString()));
		accountManager.remove(user1);
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check modify user")
	public void testModifyUser() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check modify user");
		Account user1 = createAccount().setLogPrefix(getUserPrefix()).build();
		Jaxmpp userJaxmpp1 = user1.createJaxmpp().setConnected(true).build();
		assertTrue(userJaxmpp1.isConnected());
		userJaxmpp1.disconnect(true);

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor modUserLink = p.getAnchorByText("Modify user");
		p = modUserLink.click();

		HtmlForm form = p.getForms().get(0);
		HtmlTextInput accountJid = form.getInputByName("accountjid");
		accountJid.setValueAttribute(user1.getJid().toString());

		HtmlSubmitInput button = form.getInputByName("submit");
		p = button.click();

		form = p.getForms().get(0);

		HtmlTextInput email = form.getInputByName("email");
		email.setValueAttribute(user1.getJid().toString());
		HtmlCheckBoxInput enabled = form.getInputByName("Account enabled");
		enabled.setChecked(false);

		button = form.getInputByName("submit");
		p = button.click();

		form = p.getForms().get(0);
		form.getInputByValue("Operation successful");

		p = webClient.getPage(getAdminUrl());
		userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		modUserLink = p.getAnchorByText("Modify user");
		p = modUserLink.click();

		form = p.getForms().get(0);
		accountJid = form.getInputByName("accountjid");
		accountJid.setValueAttribute(user1.getJid().toString());

		button = form.getInputByName("submit");
		p = button.click();

		form = p.getForms().get(0);

		email = form.getInputByName("email");
		assertEquals(email.getValueAttribute(), user1.getJid().toString());
		enabled = form.getInputByName("Account enabled");
		assertEquals(enabled.isChecked(), false);
		enabled.setChecked(true);

		assertFalse(userJaxmpp1.isConnected());
		try {
			userJaxmpp1.getEventBus()
					.addHandler(AuthModule.AuthFailedHandler.AuthFailedEvent.class, (sessionObject, error) -> {
						synchronized (userJaxmpp1) {
							userJaxmpp1.notify();
						}
						userJaxmpp1.disconnect(true);
					});
			userJaxmpp1.login(true);
		} catch (Exception ex) {
		}
		assertFalse(userJaxmpp1.isConnected());

		button = form.getInputByName("submit");
		p = button.click();

		form = p.getForms().get(0);
		form.getInputByValue("Operation successful");
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check get user info - user online")
	public void testGetUserInfoUserOnline()
			throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check get user info - online");
		Account user1 = createAccount().setLogPrefix(getUserPrefix()).build();
		accountManager.remove(user1);
		Jaxmpp userJaxmpp1 = user1.createJaxmpp().setConnected(true).build();

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor getUserLink = p.getAnchorByText("Get User Info");
		p = getUserLink.click();

		HtmlForm form = p.getForms().get(0);
		HtmlTextInput accountJid = form.getInputByName("accountjid");
		accountJid.setValueAttribute(user1.getJid().toString());

		HtmlSubmitInput button = form.getInputByName("submit");
		p = button.click();

		form = p.getForms().get(0);
		assertEquals(form.getInputByName("JID").getValueAttribute(), "JID: " + user1.getJid().toString());
		assertEquals(form.getInputByName("Status").getValueAttribute(), "Status: online");
		assertEquals(form.getInputByName("Active connections").getValueAttribute(), "Active connections: 1");
		assertEquals(form.getInputByName("Offline messages: 0").getValueAttribute(), "Offline messages: 0");
		DomNodeList<HtmlElement> tables = form.getElementsByTagName("table");
		DomNodeList<HtmlElement> tbody = tables.get(0).getElementsByTagName("tbody");
		DomNodeList<HtmlElement> trs = tbody.get(0).getElementsByTagName("tr");
		Iterator<DomElement> tds = trs.get(0).getChildElements().iterator();
		assertEquals(tds.next().asText(),
					 ResourceBinderModule.getBindedJID(userJaxmpp1.getSessionObject()).getResource());

		trs = tbody.get(0).getElementsByTagName("tr");
		tds = trs.get(0).getChildElements().iterator();
		assertEquals(tds.next().asText(),
					 ResourceBinderModule.getBindedJID(userJaxmpp1.getSessionObject()).getResource());
	}

	@Test(groups = {"HTTP - Admin"}, description = "Check get user info - user offline")
	public void testGetUserInfoUserOffline()
			throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check get user info - offline");
		Account user1 = createAccount().setLogPrefix(getUserPrefix()).build();
		Jaxmpp userJaxmpp1 = user1.createJaxmpp().setConnected(true).build();
		String userJid1Resource = ResourceBinderModule.getBindedJID(userJaxmpp1.getSessionObject()).getResource();
		userJaxmpp1.disconnect(true);

		Account user2 = createAccount().setLogPrefix(getUserPrefix()).build();
		Jaxmpp userJaxmpp2 = user2.createJaxmpp().setConnected(true).build();
		JID to = JID.jidInstance(user1.getJid());
		userJaxmpp2.getModule(MessageModule.class).sendMessage(to, null, "Test message 1");
		userJaxmpp2.getModule(MessageModule.class).sendMessage(to, null, "Test message 2");

		Thread.sleep(2000);

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor getUserLink = p.getAnchorByText("Get User Info");
		p = getUserLink.click();

		HtmlForm form = p.getForms().get(0);
		HtmlTextInput accountJid = form.getInputByName("accountjid");
		accountJid.setValueAttribute(user1.getJid().toString());

		HtmlSubmitInput button = form.getInputByName("submit");
		p = button.click();

		form = p.getForms().get(0);
		assertEquals(form.getInputByName("JID").getValueAttribute(), "JID: " + user1.getJid().toString());
		assertEquals(form.getInputByName("Status").getValueAttribute(), "Status: offline");
		assertEquals(form.getInputByName("Offline messages: 2").getValueAttribute(), "Offline messages: 2");
		DomNodeList<HtmlElement> tables = form.getElementsByTagName("table");
		//DomNodeList<HtmlElement> tbody = tables.get(0).getElementsByTagName("tbody");
		if (!tables.isEmpty()) {
			DomNodeList<HtmlElement> trs = tables.get(0).getElementsByTagName("tr");
			if (!trs.isEmpty()) {
				// in some cases this might not be saved to database yet
				Iterator<DomElement> tds = trs.get(0).getChildElements().iterator();
				assertEquals(tds.next().asText(), userJid1Resource);
			}
		}
	}

	private BareJID randomUserJid() throws TigaseStringprepException {
		return BareJID.bareJIDInstance(getUserPrefix() + UUID.randomUUID().toString(), getDomain());
	}

	private String getUserPrefix() {
		return "http-admin_";
	}

	private String getAdminUrl() {
		return "http://" + getDomain(0) + ":" + getHttpPort() + "/admin/";
	}
}
