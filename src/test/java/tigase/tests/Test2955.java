/*
 * Test2955.java
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

import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.util.TigaseStringprepException;

/**
 *
 * @author andrzej
 */
public class Test2955 extends AbstractTest {
	
	private WebClient webClient;
	private DefaultCredentialsProvider credentialProvider;
	private BareJID userJid1 = null;
	private Jaxmpp userJaxmpp1 = null;
	private BareJID userJid2 = null;
	private Jaxmpp userJaxmpp2 = null;	
	
	@BeforeMethod
	@Override
	public void setUp() throws Exception {
		userJid1 = null;
		userJid2 = null;
		super.setUp();	
		Jaxmpp adminJaxmpp = createJaxmppAdmin();
		credentialProvider = new DefaultCredentialsProvider();
		credentialProvider.addCredentials(adminJaxmpp.getSessionObject().getUserProperty(SessionObject.USER_BARE_JID).toString(), 
						adminJaxmpp.getSessionObject().getUserProperty(SessionObject.PASSWORD));		
		webClient = new WebClient();
		webClient.setCredentialsProvider(credentialProvider);
	}

	@AfterMethod
	public void cleanUp() throws Exception {
		credentialProvider = null;
		webClient = null;
		try {
			if (userJid1 != null) {
				if (userJaxmpp1 == null) {
					userJaxmpp1 = createJaxmpp(LOG_PREFIX_KEY, userJid1);
				}

				removeUserAccount(userJaxmpp1);
			}
			if (userJid2 != null) {
				if (userJaxmpp2 == null) {
					userJaxmpp2 = createJaxmpp(LOG_PREFIX_KEY, userJid2);
				}
				
				removeUserAccount(userJaxmpp2);
			}
		} finally {
			userJid1 = null;
			userJid2 = null;
		}
	}
	
	@Test(groups = { "HTTP - Admin" }, description = "Check authorization requirement")
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

	@Test(groups = { "HTTP - Admin" }, description = "Check 'Users' contains valid commands")
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
	
	@Test(groups = { "HTTP - Admin" }, description = "Check adding user")
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
		
		userJid1 = jid;
//		
//		Jaxmpp jaxmpp = createJaxmpp(LOG_PREFIX_KEY, jid);
//		jaxmpp.login(true);
//		removeUserAccount(jaxmpp);
	}	
	
	@Test(groups = { "HTTP - Admin" }, description = "Check delete user")
	public void testDeleteUser() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check delete user");
		userJid1 = createUserAccount(getUserPrefix());

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor delUserLink = p.getAnchorByText("Delete user");
		p = delUserLink.click();
		
		HtmlForm form = p.getForms().get(0);
		HtmlTextArea accountJids = form.getTextAreaByName("accountjids");
		accountJids.setText(userJid1.toString());
		
		HtmlSubmitInput button = form.getInputByName("submit");
		
		p = button.click();
		form = p.getForms().get(0);
		HtmlTextArea notes = form.getTextAreaByName("Notes");
		assertTrue(notes.getText().contains("Operation successful for user " + userJid1.toString()));
		userJid1 = null;
	}	
	
	@Test(groups = { "HTTP - Admin" }, description = "Check modify user")
	public void testModifyUser() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check modify user");
		userJid1 = createUserAccount(getUserPrefix());
		userJaxmpp1 = createJaxmpp(LOG_PREFIX_KEY, userJid1);
		userJaxmpp1.login(true);
		assertTrue(userJaxmpp1.isConnected());
		userJaxmpp1.disconnect(true);

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor modUserLink = p.getAnchorByText("Modify user");
		p = modUserLink.click();
		
		HtmlForm form = p.getForms().get(0);
		HtmlTextInput accountJid = form.getInputByName("accountjid");
		accountJid.setValueAttribute(userJid1.toString());
		
		HtmlSubmitInput button = form.getInputByName("submit");
		p = button.click();
		
		form = p.getForms().get(0);
		
		HtmlTextInput email = form.getInputByName("email");
		email.setValueAttribute(userJid1.toString());
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
		accountJid.setValueAttribute(userJid1.toString());
		
		button = form.getInputByName("submit");
		p = button.click();
		
		form = p.getForms().get(0);
		
		email = form.getInputByName("email");
		assertEquals(email.getValueAttribute(), userJid1.toString());
		enabled = form.getInputByName("Account enabled");
		assertEquals(enabled.isChecked(), false);
		enabled.setChecked(true);
		
		try {
			userJaxmpp1.getEventBus().addHandler(AuthModule.AuthFailedHandler.AuthFailedEvent.class, new AuthModule.AuthFailedHandler() {

				@Override
				public void onAuthFailed(SessionObject sessionObject, SaslModule.SaslError error) throws JaxmppException {		
					synchronized (userJaxmpp1) {
						userJaxmpp1.notify();
					}
					userJaxmpp1.disconnect(true);
				}
				
			});
			userJaxmpp1.login(true);
		} catch (Exception ex) {}
		assertFalse(userJaxmpp1.isConnected());				
		
		button = form.getInputByName("submit");		
		p = button.click();
		
		form = p.getForms().get(0);
		form.getInputByValue("Operation successful");
	}	
	
	@Test(groups = { "HTTP - Admin" }, description = "Check get user info - user online")
	public void testGetUserInfoUserOnline() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check get user info - online");
		userJid1 = createUserAccount(getUserPrefix());
		userJaxmpp1 = createJaxmpp(LOG_PREFIX_KEY, userJid1);
		userJaxmpp1.login(true);
		assertTrue(userJaxmpp1.isConnected());

		HtmlPage p = webClient.getPage(getAdminUrl());
		HtmlAnchor userGroupLink = p.getAnchorByText("Users");
		p = userGroupLink.click();
		HtmlAnchor getUserLink = p.getAnchorByText("Get User Info");
		p = getUserLink.click();
		
		HtmlForm form = p.getForms().get(0);
		HtmlTextInput accountJid = form.getInputByName("accountjid");
		accountJid.setValueAttribute(userJid1.toString());
		
		HtmlSubmitInput button = form.getInputByName("submit");
		p = button.click();
		
		form = p.getForms().get(0);
		assertEquals(form.getInputByName("JID").getValueAttribute(), "JID: " + userJid1.toString());
		assertEquals(form.getInputByName("Status").getValueAttribute(), "Status: online");
		assertEquals(form.getInputByName("Active connections").getValueAttribute(), "Active connections: 1");
		assertEquals(form.getInputByName("Offline messages: 0").getValueAttribute(), "Offline messages: 0");
		DomNodeList<HtmlElement> tables = form.getElementsByTagName("table");
		//DomNodeList<HtmlElement> tbody = tables.get(0).getElementsByTagName("tbody");
		DomNodeList<HtmlElement> trs = tables.get(0).getElementsByTagName("tr");
		Iterator<DomElement> tds = trs.get(0).getChildElements().iterator();
		assertEquals(tds.next().asText(), ResourceBinderModule.getBindedJID(userJaxmpp1.getSessionObject()).getResource());
		
		trs = tables.get(0).getElementsByTagName("tr");
		tds = trs.get(0).getChildElements().iterator();
		assertEquals(tds.next().asText(), ResourceBinderModule.getBindedJID(userJaxmpp1.getSessionObject()).getResource());
	}	
	
	@Test(groups = { "HTTP - Admin" }, description = "Check get user info - user offline")
	public void testGetUserInfoUserOffline() throws IOException, TigaseStringprepException, JaxmppException, InterruptedException {
		System.out.println("##### Check get user info - offline");
		userJid1 = createUserAccount(getUserPrefix());
		userJaxmpp1 = createJaxmpp(LOG_PREFIX_KEY, userJid1);
		userJaxmpp1.login(true);
		assertTrue(userJaxmpp1.isConnected());
		String userJid1Resource = ResourceBinderModule.getBindedJID(userJaxmpp1.getSessionObject()).getResource();
		userJaxmpp1.disconnect(true);

		userJid2 = createUserAccount(getUserPrefix());
		userJaxmpp2 = createJaxmpp(LOG_PREFIX_KEY, userJid2);
		userJaxmpp2.login(true);
		JID to = JID.jidInstance(userJid1);
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
		accountJid.setValueAttribute(userJid1.toString());
		
		HtmlSubmitInput button = form.getInputByName("submit");
		p = button.click();
		
		form = p.getForms().get(0);
		assertEquals(form.getInputByName("JID").getValueAttribute(), "JID: " + userJid1.toString());
		assertEquals(form.getInputByName("Status").getValueAttribute(), "Status: offline");
		assertEquals(form.getInputByName("Offline messages: 2").getValueAttribute(), "Offline messages: 2");
		DomNodeList<HtmlElement> tables = form.getElementsByTagName("table");
		//DomNodeList<HtmlElement> tbody = tables.get(0).getElementsByTagName("tbody");
		DomNodeList<HtmlElement> trs = tables.get(0).getElementsByTagName("tr");
		if (!trs.isEmpty()) {
			// in some cases this might not be saved to database yet
			Iterator<DomElement> tds = trs.get(0).getChildElements().iterator();
			assertEquals(tds.next().asText(), userJid1Resource);
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
