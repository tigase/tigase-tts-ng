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
package tigase.tests.setup;

import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.testng.Assert.*;

public class TestSetup {

	private DefaultCredentialsProvider credentialProvider;
	private WebClient webClient;
	private URL setupUrl;
	private HtmlPage page;

	@BeforeClass
	public void setUp() throws MalformedURLException {
		setupUrl = new URL("http://localhost:8080/setup/");
		credentialProvider = new DefaultCredentialsProvider();
		credentialProvider.addCredentials("admin", "tigase");
		webClient = new WebClient();
	}

	@AfterClass
	public void tearDown() {
		
	}

	@Test
	public void authenticate() throws IOException {
		try {
			page = webClient.getPage(setupUrl);
			assertNull(page);
		} catch (FailingHttpStatusCodeException ex) {
			assertEquals(ex.getStatusCode(), 401);
		}
		webClient.setCredentialsProvider(credentialProvider);
		page = webClient.getPage(setupUrl);
	}

	@Test(dependsOnMethods = { "authenticate" })
	public void testAboutSoftwarePage() throws IOException {
		assertNotNull(page);
		assertTrue(page.isHtmlPage());
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: About software");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testAboutSoftwarePage"})
	public void testACSInfoPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Advanced Clustering Strategy information");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlInput input = form.getInputByName("2_acsName");
		assertNotNull(input);
		input.setValueAttribute("Test, Inc.");
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testACSInfoPage"})
	public void testBasicSetupPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Basic Tigase server configuration");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlButton next = form.getButtonByName("next");
		HtmlSelect select = form.getSelectByName("3_configType");
		assertNotNull(select);
		assertEquals(select.getDefaultValue(), "default");
		HtmlInput input = form.getInputByName("3_virtualDomain");
		assertNotNull(input);
		input = form.getInputByName("3_admins");
		assertNotNull(input);
		input = form.getInputByName("3_adminPwd");
		assertNotNull(input);
		select = form.getSelectByName("3_dbType");
		assertNotNull(select);
		assertEquals(select.getDefaultValue(), "derby");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testBasicSetupPage"})
	public void testConnectivityPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Connectivity");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlInput input = form.getInputByName("4_c2s");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("4_bosh");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("4_ws2s");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("4_s2s");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("4_ext");
		assertNotNull(input);
		assertEquals(input.isChecked(), false);
		input = form.getInputByName("4_http");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testConnectivityPage"})
	public void testFeaturesPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Features");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlInput input = (HtmlInput) page.getBody().getElementsByAttribute("input", "name", "5_clusterMode").get(0);
		assertNotNull(input);
		assertEquals(input.isChecked(), false);
		input = (HtmlInput) page.getBody().getElementsByAttribute("input", "name", "5_acsComponent").get(0);
		assertNotNull(input);
		assertEquals(input.isChecked(), false);
		input = form.getInputByName("5_muc");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("5_pubsub");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("5_message-archive");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("5_urn:xmpp:push:0");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("5_upload");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("5_message-carbons");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("5_urn:xmpp:csi:0");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		input = form.getInputByName("motd");
		assertNotNull(input);
		assertEquals(input.isChecked(), false);
		input = form.getInputByName("jabber:iq:last-marker");
		assertNotNull(input);
		assertEquals(input.isChecked(), false);
		input = form.getInputByName("5_spam-filter");
		assertNotNull(input);
		assertEquals(input.isChecked(), true);
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testFeaturesPage"})
	public void testDatabaseConfigurationPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Database configuration");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlInput input = form.getInputByName("6_dbName");
		assertNotNull(input);
		assertEquals(input.getValueAttribute(), "tigasedb");
		input = form.getInputByName("6_dbHostname");
		assertNotNull(input);
		assertEquals(input.getValueAttribute(), "localhost");
		input = form.getInputByName("6_dbUser");
		assertNotNull(input);
		assertEquals(input.getValueAttribute(), "tigase_user");
		input = form.getInputByName("6_dbPass");
		assertNotNull(input);
		assertEquals(input.getValueAttribute(), "tigase_pass");
		input = form.getInputByName("6_rootUser");
		assertNotNull(input);
		assertEquals(input.getValueAttribute(), "root");
		input = form.getInputByName("6_rootPass");
		assertNotNull(input);
		assertEquals(input.getValueAttribute(), "root");
		input = form.getInputByName("6_useSSL");
		assertNotNull(input);
		assertEquals(input.isChecked(), false);
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testDatabaseConfigurationPage"})
	public void testDatabaseConnectivityPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Database connectivity check");

		assertEquals(page.getElementsByName("h4").stream().map(x -> x.getTextContent()).map(String::trim).filter(x -> "ERROR".equalsIgnoreCase(x)).findFirst().isPresent(), false);

		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testDatabaseConnectivityPage"})
	public void testSetupSecurityPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Setup security");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlInput input = form.getInputByName("8_setupUser");
		assertNotNull(input);
		input = form.getInputByName("8_setupPassword");
		assertNotNull(input);
		HtmlButton next = form.getButtonByName("next");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testSetupSecurityPage"})
	public void testSaveConfigurationPage() throws IOException {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Saving configuration");
		HtmlForm form = page.getForms().get(0);
		assertNotNull(form);
		HtmlTextArea textArea = form.getTextAreaByName("config");
		assertNotNull(textArea);
		assertNotNull(textArea.getText());
		assertFalse(textArea.getText().trim().isEmpty());
		HtmlButton next = form.getButtonByName("save");
		page = next.click();
	}

	@Test(dependsOnMethods = {"testSaveConfigurationPage"})
	public void testFinishedPage() {
		assertNotNull(page);
		assertEquals(page.getTitleText(), "Tigase XMPP Server: Setup: Finished");
	}
}
