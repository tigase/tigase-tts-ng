/*
 * Test2936.java
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.xml.J2seElement;
import tigase.tests.AbstractTest;
import tigase.xml.DomBuilderHandler;
import tigase.xml.SimpleParser;

/**
 *
 * @author andrzej
 */
public class Test2936 extends AbstractTest {
	
	private static final String USER_PREFIX = "http_";
	
	private CloseableHttpClient httpClient;
	private BareJID userJid;
	private Jaxmpp userJaxmpp;
	
	@BeforeMethod
	@Override
	public void setUp() throws Exception {
		super.setUp();	
		Jaxmpp adminJaxmpp = createJaxmppAdmin();
        HttpHost target = new HttpHost(getDomain(0), Integer.parseInt(getHttpPort()), "http");
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(target.getHostName(), target.getPort()),
                new UsernamePasswordCredentials(adminJaxmpp.getSessionObject().getUserProperty(SessionObject.USER_BARE_JID).toString(), 
						adminJaxmpp.getSessionObject().getUserProperty(SessionObject.PASSWORD)));
		httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(5000).build()).build();
		userJid = createUserAccount(USER_PREFIX);
		userJaxmpp = createJaxmpp(USER_PREFIX, userJid);
	}
	
	@AfterMethod
	public void cleanUp() throws Exception {
		httpClient.close();
		removeUserAccount(userJaxmpp);
	}
	
	@Test(groups = { "HTTP - REST API" }, description = "Sending XMPP messages using HTTP REST API")
	public void testSendingXMPPMessages() throws Exception {
		final Mutex mutex = new Mutex();
		userJaxmpp.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, 
				new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					String msg = convertMessageToStr(stanza);
					mutex.notify("message:received:" + msg);
				} catch (XMLException ex) {
					Logger.getLogger(Test2936.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
					
		});
		userJaxmpp.login(true);		
		
		HttpHost target = new HttpHost(getDomain(0), Integer.parseInt(getHttpPort()), "http");
		HttpClientContext localContext = HttpClientContext.create();
		AuthCache authCache = new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(target, basicAuth);
		localContext.setAuthCache(authCache);
		
		String server = getInstanceHostname();
		HttpPost postRequest = new HttpPost("/rest/stream/");	
		postRequest.addHeader("Api-Key", getApiKey());
		
		String body = "Test message " + UUID.randomUUID().toString();
		
		Element messageToSendEl = ElementFactory.create("message");
		messageToSendEl.setAttribute("xmlns", "jabber:client");
		messageToSendEl.setAttribute("type", "chat");
		messageToSendEl.setAttribute("to", ResourceBinderModule.getBindedJID(userJaxmpp.getSessionObject()).toString());
		
		Element bodyEl = ElementFactory.create("body", body, null);	
		messageToSendEl.addChild(bodyEl);
		String msg = convertMessageToStr(messageToSendEl);
		
		StringEntity entity = new StringEntity(messageToSendEl.getAsString());
		entity.setContentType("application/xml");
		postRequest.setEntity(entity);
		
		HttpResponse response = httpClient.execute(target, postRequest, localContext);
		
		assertEquals(response.getStatusLine().getStatusCode(), 200);
	
		mutex.waitFor(20*1000, "message:received:" + msg);
		assertTrue(mutex.isItemNotified("message:received:" + msg));
	}	
	
	private String convertMessageToStr(Element msg) throws XMLException {
		return "" + msg.getAttribute("type") + ":" + msg.getAttribute("to") + ":" + msg.getFirstChild("body").getValue();
	}

	private String inputStreamToString(InputStream is) throws IOException {
		Reader reader = new InputStreamReader(is);
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[1024];
		int read = 0;
		while ((read = reader.read(buf)) >= 0) {
			sb.append(buf, 0, read);
		}
		return sb.toString();
	}
	
	private Queue<Element> inputStreamToXml(InputStream is) throws IOException {
		Reader reader = new InputStreamReader(is);
		DomBuilderHandler handler = new DomBuilderHandler();
		SimpleParser parser = new SimpleParser();
		char[] buf = new char[1024];
		int read = 0;
		while ((read = reader.read(buf)) >= 0) {
			parser.parse(handler, buf, 0, read);
		}		
		
		Queue<tigase.xml.Element> elems = handler.getParsedElements();
		Queue<Element> res = new ArrayDeque<>();
		tigase.xml.Element elem;
		while ((elem = elems.poll()) != null) {
			res.offer(new J2seElement(elem));
		}
		
		return res;
	}	
	
}
