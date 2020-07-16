/*
 * TestRestUserStatus.java
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

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;
import tigase.tests.utils.ApiKey;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestRestUserStatus extends AbstractTest {

	private ApiKey apiKey;
	private CloseableHttpClient httpClient;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeMethod
	public void setup() throws Exception {
		user1 = createAccount().setLogPrefix("XX").build();
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();

		user2 = createAccount().setLogPrefix("XX").build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();

		testSubscribeAndWait(user1Jaxmpp, user2Jaxmpp);
		testSubscribeAndWait(user2Jaxmpp, user1Jaxmpp);

		//user2Jaxmpp.disconnect(true);

		HttpHost target = new HttpHost(getInstanceHostname(), Integer.parseInt(getHttpPort()), "http");
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(target.getHostName(), target.getPort()),
									 new UsernamePasswordCredentials(getAdminAccount().getJid().toString(),
																	 getAdminAccount().getPassword()));

		for (String hostname : getInstanceHostnames()) {
			credsProvider.setCredentials(new AuthScope(hostname, target.getPort()),
										 new UsernamePasswordCredentials(getAdminAccount().getJid().toString(),
																		 getAdminAccount().getPassword()));
		}

		for (String hostname : getInstanceHostnames()) {
			credsProvider.setCredentials(new AuthScope(hostname, 8380),
										 new UsernamePasswordCredentials(getAdminAccount().getJid().toString(),
																		 getAdminAccount().getPassword()));
		}

		httpClient = HttpClients.custom()
				.setDefaultCredentialsProvider(credsProvider)
				.setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(5000).build())
				.build();

		apiKey = createRestApiKey().build();

		Thread.sleep(200);
		user2Jaxmpp.disconnect(true);
	}

	@AfterMethod
	public void cleanUp() throws Exception {
		httpClient.close();
	}


	@Test
	public void testBasic() throws IOException, InterruptedException, XMLException {
		final Mutex mutex = new Mutex();
		
		user1Jaxmpp.getModule(PresenceModule.class)
				.addContactChangedPresenceHandler((sessionObject, stanza, jid, show, status, priority) -> {
					mutex.notify("presence:received:" + jid + ":" + show + ":" + priority + ":" + status);
				});

		changePresence(user2.getJid(), "test1", Presence.Show.online, null, null);

		String item = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.online + ":" + (-1) + ":" + null;
		mutex.waitFor(10 * 1000,item);
		assertTrue(mutex.isItemNotified(item));

		Map<String, Presence> presenceMap = user1Jaxmpp.getModule(PresenceModule.class)
				.getPresenceStore()
				.getPresences(user2.getJid());

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);

		changePresence(user2.getJid(), "test1", Presence.Show.offline, null, null);

		item = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.offline + ":" + (-1) + ":" + null;
		mutex.waitFor(10 * 1000,item);
		assertTrue(mutex.isItemNotified(item));

		presenceMap = user1Jaxmpp.getModule(PresenceModule.class)
				.getPresenceStore()
				.getPresences(user2.getJid());

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.offline);
	}                                                    

	@Test
	public void testAdvanced() throws IOException, InterruptedException, JaxmppException {
		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModule(PresenceModule.class)
				.addContactChangedPresenceHandler((sessionObject, stanza, jid, show, status, priority) -> {
					mutex.notify("presence:received:" + jid + ":" + show + ":" + priority + ":" + status);
				});

		changePresence(user2.getJid(), "test1", Presence.Show.online, null, null);

		String item = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.online + ":" + (-1) + ":" + null;
		mutex.waitFor(10 * 1000,item);
		assertTrue(mutex.isItemNotified(item));

		Map<String, Presence> presenceMap = user1Jaxmpp.getModule(PresenceModule.class)
				.getPresenceStore()
				.getPresences(user2.getJid());

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);

		user2Jaxmpp.getConnectionConfiguration().setResource("test2");
		user2Jaxmpp.login(true);

		String item2 = "presence:received:" + user2.getJid() + "/test2" + ":" + Presence.Show.online + ":" + 0 + ":" + null;
		mutex.waitFor(10 * 1000, item2);
		mutex.isItemNotified(item2);

		Thread.sleep(100);

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online, presenceMap.get("test1").getAsString());
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		changePresence(user2.getJid(), "test3", Presence.Show.away, "Test 1", -2);

		String item3 = "presence:received:" + user2.getJid() + "/test3" + ":" + Presence.Show.away + ":" + (-2) + ":" + "Test 1";
		mutex.waitFor(10 * 1000,item3);
		assertTrue(mutex.isItemNotified(item3));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 1");

		changePresence(user2.getJid(), "test1", Presence.Show.offline, null, null);
		String item4 = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.offline + ":" + (-1) + ":" + null;
		mutex.waitFor(10 * 1000,item4);
		assertTrue(mutex.isItemNotified(item4));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.offline);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 1");
	}

	@Test
	public void testAdvancedWithActivity() throws IOException, InterruptedException, JaxmppException {
		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModule(PresenceModule.class)
				.addContactChangedPresenceHandler((sessionObject, stanza, jid, show, status, priority) -> {
					Element activityEl = stanza.getFirstChild("activity");
					Element textEl = activityEl != null ? activityEl.getFirstChild("text") : null;
					String text = textEl != null ? textEl.getValue() : null;
					Element categoryEl = activityEl != null ? activityEl.getFirstChild() : null;
					String category = categoryEl != null ? categoryEl.getName() : null;
					Element typeEl = categoryEl != null ? categoryEl.getFirstChild() : null;
					String type = typeEl != null ? typeEl.getName() : null;

					mutex.notify("presence:received:" + jid + ":" + show + ":" + priority + ":" + status + ":" + category + ":" + type + ":" + text);
				});

		changePresence(user2.getJid(), "test1", Presence.Show.online, null, null);

		String item = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.online + ":" + (-1) + ":" + null + ":" + null + ":" + null + ":" + null;
		mutex.waitFor(10 * 1000,item);
		assertTrue(mutex.isItemNotified(item));

		Map<String, Presence> presenceMap = user1Jaxmpp.getModule(PresenceModule.class)
				.getPresenceStore()
				.getPresences(user2.getJid());

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);

		user2Jaxmpp.getConnectionConfiguration().setResource("test2");
		user2Jaxmpp.login(true);

		String item2 = "presence:received:" + user2.getJid() + "/test2" + ":" + Presence.Show.online + ":" + 0 + ":" + null + ":" + null + ":" + null + ":" + null;
		mutex.waitFor(10 * 1000, item2);
		mutex.isItemNotified(item2);

		Thread.sleep(100);

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online, presenceMap.get("test1").getAsString());
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		changePresence(user2.getJid(), "test3", Presence.Show.away, "Test 1", -2, "talking", "on_the_phone", null);

		String item3 = "presence:received:" + user2.getJid() + "/test3" + ":" + Presence.Show.away + ":" + (-2) + ":" + "Test 1:talking:on_the_phone" + ":" + null;
		mutex.waitFor(10 * 1000,item3);
		assertTrue(mutex.isItemNotified(item3));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 1");

		changePresence(user2.getJid(), "test3", Presence.Show.away, "Test 2", -2, "talking", "on_the_phone", "with Jenny");

		item3 = "presence:received:" + user2.getJid() + "/test3" + ":" + Presence.Show.away + ":" + (-2) + ":" + "Test 2:talking:on_the_phone:with Jenny";
		mutex.waitFor(10 * 1000,item3);
		assertTrue(mutex.isItemNotified(item3));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 2");

		changePresence(user2.getJid(), "test1", Presence.Show.offline, null, null, null, null, null);
		String item4 = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.offline + ":" + (-1) + ":" + null + ":" + null + ":" + null + ":" + null;
		mutex.waitFor(10 * 1000,item4);
		assertTrue(mutex.isItemNotified(item4));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.offline);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 2");
	}
	
	@Test
	public void testAdvancedCluster() throws IOException, InterruptedException, JaxmppException {
		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModule(PresenceModule.class)
				.addContactChangedPresenceHandler((sessionObject, stanza, jid, show, status, priority) -> {
					mutex.notify("presence:received:" + jid + ":" + show + ":" + priority + ":" + status);
				});


		String[] hostnames = getInstanceHostnames();
		String primaryHostname = hostnames[0];
		String secondaryHostname = hostnames.length < 2 ? hostnames[0] : hostnames[1];

		changePresence(primaryHostname, user2.getJid(), "test1", Presence.Show.online, null, null);

		String item = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.online + ":" + (-1) + ":" + null;
		mutex.waitFor(10 * 1000,item);
		assertTrue(mutex.isItemNotified(item));

		Map<String, Presence> presenceMap = user1Jaxmpp.getModule(PresenceModule.class)
				.getPresenceStore()
				.getPresences(user2.getJid());

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);

		user2Jaxmpp.getConnectionConfiguration().setResource("test2");
		user2Jaxmpp.getConnectionConfiguration().setServer(secondaryHostname);
		user2Jaxmpp.login(true);

		String item2 = "presence:received:" + user2.getJid() + "/test2" + ":" + Presence.Show.online + ":" + 0 + ":" + null;
		mutex.waitFor(10 * 1000, item2);
		mutex.isItemNotified(item2);

		Thread.sleep(100);

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online, presenceMap.get("test1").getAsString());
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		changePresence(secondaryHostname, user2.getJid(), "test3", Presence.Show.away, "Test 1", -2);

		String item3 = "presence:received:" + user2.getJid() + "/test3" + ":" + Presence.Show.away + ":" + (-2) + ":" + "Test 1";
		mutex.waitFor(10 * 1000,item3);
		assertTrue(mutex.isItemNotified(item3));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.online);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 1");

		changePresence(primaryHostname, user2.getJid(), "test1", Presence.Show.offline, null, null);
		String item4 = "presence:received:" + user2.getJid() + "/test1" + ":" + Presence.Show.offline + ":" + (-1) + ":" + null;
		mutex.waitFor(10 * 1000,item4);
		assertTrue(mutex.isItemNotified(item4));

		assertTrue(presenceMap.containsKey("test1"));
		assertTrue(presenceMap.get("test1").getShow() == Presence.Show.offline);
		assertTrue(presenceMap.containsKey("test2"));
		assertTrue(presenceMap.get("test2").getShow() == Presence.Show.online);

		assertTrue(presenceMap.containsKey("test3"));
		assertTrue(presenceMap.get("test3").getShow() == Presence.Show.away);
		assertEquals(presenceMap.get("test3").getPriority(), new Integer(-2));
		assertEquals(presenceMap.get("test3").getStatus(), "Test 1");
	}

	private void changePresence(BareJID user, String resource, Presence.Show show, String status, Integer priority)
			throws IOException {
		String hostname = getInstanceHostnames()[0];
		changePresence(hostname, user, resource, show, status, priority);
	}

	private void changePresence(BareJID user, String resource, Presence.Show show, String status, Integer priority, String activityCategory, String activityType, String activityText)
			throws IOException {
		String hostname = getInstanceHostnames()[0];
		changePresence(hostname, user, resource, show, status, priority, activityCategory, activityType, activityText);
	}
	
	private void changePresence(String hostname, BareJID user, String resource, Presence.Show show, String status, Integer priority)
			throws IOException {
		changePresence(hostname, user, resource, show, status, priority, null, null, null);
	}
	
	private void changePresence(String hostname, BareJID user, String resource, Presence.Show show, String status, Integer priority, String activityCategory, String activityType, String activityText)
			throws IOException {
		int port = Integer.parseInt(getHttpPort());
		HttpHost target = new HttpHost(hostname, port, "http");

		HttpPost postRequest = new HttpPost(
				(resource != null) ? ("/rest/user/" + user.toString() + "/status/" + resource) : ("/rest/user/" + user.toString() + "/status"));
		postRequest.addHeader("Api-Key", apiKey.getKey());

		Map<String, Object> params = new HashMap<>();
		switch (show) {
			case offline:
				params.put("available", "false");
				break;
			default:
				params.put("available", "true");
				if (show != Presence.Show.online) {
					params.put("show", show.name());
				}
		}

		if (status != null) {
			params.put("status", status);
		}
		if (priority != null) {
			params.put("priority", String.valueOf(priority));
		}
		if (activityCategory != null) {
			Map<String, String> activity = new HashMap<>();
			activity.put("category", activityCategory);
			if (activityType != null) {
				activity.put("type", activityType);
			}
			params.put("activity", activity);
			if (activityText != null) {
				activity.put("text", activityText);
			}
		}

		String content = new JsonBuilder(params).toString();
		StringEntity entity = new StringEntity(content);
		entity.setContentType("application/json");
		postRequest.setEntity(entity);

		System.out.println("sending request:" + target + " - " + postRequest);

		HttpResponse response = httpClient.execute(target, postRequest);

		assertEquals(response.getStatusLine().getStatusCode(), 200, response.toString());

		Map<String, Object> result = (Map<String, Object>) new JsonSlurper().parse(response.getEntity().getContent(),
																				   "UTF-8");

		assertEquals(((Map<String, Object>) result.get("status")).get("success"), true);
	}
}
