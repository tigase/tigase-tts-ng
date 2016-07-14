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
package tigase.tests.pubsub;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;

/**
 * Test is responsible for testing PubSub component node creation
 * manipulation and removal including publication and retraction
 * of PubSub node items using PubSub protocol and AdHoc commands.
 *
 * This test is executed on one or many cluster nodes and during
 * execution checks propagation of changes between cluster nodes.
 *
 * Created by andrzej on 10.07.2016.
 */
public class TestRestApi extends TestPubSubAbstract {

	private static final int SECOND = 1000;
	private CloseableHttpClient httpClient;
	private HttpClientContext localContext;
	private BareJID adminBareJid;

	@BeforeClass
	public void setUp() throws Exception {
		super.setUp();

		// initialize HTTP client
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		localContext = HttpClientContext.create();
		int timeout = 15;
		httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
				.setDefaultRequestConfig(RequestConfig.custom()
						.setSocketTimeout(timeout * SECOND)
						.setConnectTimeout(timeout * SECOND)
						.setConnectionRequestTimeout(timeout * SECOND)
						.build()).build();
	}

	@AfterClass
	public void tearDown() throws Exception {
		super.tearDown();
		httpClient.close();
		httpClient = null;
		localContext = null;
	}

	// HTTP API based implementation
	public void createNode(String hostname, BareJID owner, String nodeName, String name, boolean collection) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("owner", owner.toString(), null));

		Element pubsub = ElementFactory.create("pubsub");
		pubsub.setAttribute("prefix", "true");
		pubsub.addChild(ElementFactory.create("node_type", collection ? "collection" : "leaf", null));
		pubsub.addChild(ElementFactory.create("title", name, null));
		command.addChild(pubsub);

		executeHttpApiRequest(hostname, "create-node", command);
	}

	public void deleteNode(String hostname, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		executeHttpApiRequest(hostname, "delete-node", command);
	}

	public void subscribeNode(String hostname, BareJID jid, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element jids = ElementFactory.create("jids");
		jids.addChild(ElementFactory.create("value", jid.toString(), null));
		command.addChild(jids);

		executeHttpApiRequest(hostname, "subscribe-node", command);
	}

	public void unsubscribeNode(String hostname, BareJID jid, String nodeName) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));

		Element jids = ElementFactory.create("jids");
		jids.addChild(ElementFactory.create("value", jid.toString(), null));
		command.addChild(jids);

		executeHttpApiRequest(hostname, "unsubscribe-node", command);
	}

	public void publishItemToNode(String hostname, BareJID owner, String nodeName, String itemId, Element payload) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));
		Element entry = ElementFactory.create("entry");
		entry.addChild(payload);
		command.addChild(entry);

		executeHttpApiRequest(hostname, "publish-item", command);
	}

	public void retractItemFromNode(String hostname, String nodeName, String itemId) throws Exception {
		Element command = ElementFactory.create("data");

		command.addChild(ElementFactory.create("node", nodeName, null));
		command.addChild(ElementFactory.create("item-id", itemId, null));

		executeHttpApiRequest(hostname, "delete-item", command);
	}

	/** This is not available in HTTP API - we are doing this using PubSub protocol */
	public void configureNode(String hostname, String nodeName, String parentNode) throws JaxmppException, InterruptedException {
		Jaxmpp jaxmpp = jaxmpps.get(hostname);
		JabberDataElement nodeCfg = new JabberDataElement(XDataType.submit);
		nodeCfg.addTextSingleField("pubsub#collection", parentNode);
		jaxmpp.getModule(PubSubModule.class).configureNode(pubsubJid.getBareJid(), nodeName, nodeCfg, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {

			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("configured:node:" + nodeName + ":" + parentNode);
			}

			@Override
			public void onTimeout() throws JaxmppException {

			}
		});
		mutex.waitFor(10 * 1000, "configured:node:" + nodeName + ":" + parentNode);
		assertTrue("Configuration of node " + nodeName + " on " + jaxmpp.getSessionObject().getProperty("socket#ServerHost") + " failed", mutex.isItemNotified("configured:node:" + nodeName + ":" + parentNode));
	}

	private String executeHttpApiRequest(String hostname, String action, Element command) throws IOException, XMLException {
		HttpHost target = new HttpHost( hostname, Integer.parseInt( getHttpPort() ), "http" );
		HttpPost postRequest = new HttpPost( "/rest/pubsub/" + pubsubJid + "/" + action);
		postRequest.addHeader( "Api-Key", getApiKey() );

		StringEntity entity = new StringEntity( command.getAsString() );
		entity.setContentType( "application/xml" );
		postRequest.setEntity( entity );

		HttpResponse response = null;
		try {
			response = httpClient.execute( target, postRequest, localContext );
		} catch ( Exception ex ) {
			fail( ex );
		}

		if ( response == null ){
			Assert.fail( "Request response not received" );
			return null;
		}

		String responseContent = response.getEntity() != null
				? inputStreamToString( response.getEntity().getContent() ) : "";
		Assert.assertEquals( response.getStatusLine().getStatusCode(), 200 );

		boolean responseContains = responseContent.toLowerCase().contains( "Operation successful".toLowerCase() );
		log("got response:" + responseContent);
		Assert.assertTrue( responseContains, "Publishing was successful" );

		return responseContent;
	}

	private String inputStreamToString( InputStream is ) throws IOException {
		Reader reader = new InputStreamReader( is );
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[ 1024 ];
		int read = 0;
		while ( ( read = reader.read( buf ) ) >= 0 ) {
			sb.append( buf, 0, read );
		}
		return sb.toString();
	}

}
