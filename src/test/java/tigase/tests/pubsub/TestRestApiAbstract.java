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
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.j2se.xml.J2seElement;
import tigase.xml.DomBuilderHandler;
import tigase.xml.SingletonFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Created by andrzej on 20.11.2016.
 */
public abstract class TestRestApiAbstract extends TestPubSubAbstract {

	protected static final int SECOND = 1000;
	protected CloseableHttpClient httpClient;
	protected HttpClientContext localContext;
	protected BareJID adminBareJid;

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

	protected String executeHttpApiRequest(String hostname, String action, String command, String contentType) throws IOException,
																								   XMLException {
		HttpHost target = new HttpHost(hostname, Integer.parseInt(getHttpPort() ), "http" );
		HttpPost postRequest = new HttpPost("/rest/pubsub/" + pubsubJid + "/" + action);
		postRequest.addHeader( "Api-Key", getApiKey() );

		StringEntity entity = new StringEntity(command);
		entity.setContentType( contentType );
		postRequest.setEntity( entity );

		HttpResponse response = null;
		try {
			response = httpClient.execute( target, postRequest, localContext );
		} catch ( Exception ex ) {
			fail( ex );
		}

		if ( response == null ){
			Assert.fail("Request response not received" );
			return null;
		}

		String responseContent = response.getEntity() != null
								 ? inputStreamToString( response.getEntity().getContent() ) : "";
		Assert.assertEquals( response.getStatusLine().getStatusCode(), 200 );

		return responseContent;
	}

	protected String inputStreamToString( InputStream is ) throws IOException {
		Reader reader = new InputStreamReader(is );
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[ 1024 ];
		int read = 0;
		while ( ( read = reader.read( buf ) ) >= 0 ) {
			sb.append( buf, 0, read );
		}
		return sb.toString();
	}

	protected static Element parseXML(String result) {
		DomBuilderHandler handler = new DomBuilderHandler();
		SingletonFactory.getParserInstance().parse(handler, result.toCharArray(), 0, result.length());

		tigase.xml.Element response = handler.getParsedElements().poll();
		return response == null ? null : new J2seElement(response);
	}

}
