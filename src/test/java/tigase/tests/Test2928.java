/*
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

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static tigase.TestLogger.log;

public class Test2928 extends AbstractTest {

	public enum DomainFilterPolicy {

		ALL, LOCAL, OWN, LIST, BLACKLIST, CUSTOM, BLOCK;
	}

	private CloseableHttpClient httpClient;
	private HttpHost target;
	private HttpClientContext localContext;

	BareJID adminJID;
	Jaxmpp adminJaxmpp;

	BareJID userRegularJID;
	Jaxmpp userRegularJaxmpp;

	BareJID userRegular1JID_whitelist;
	Jaxmpp userRegular1Jaxmpp_whitelist;

	BareJID userRegular2JID_blacklist;
	Jaxmpp userRegular2Jaxmpp_blacklist;

	final Mutex mutex = new Mutex();

	@BeforeClass(dependsOnMethods = { "setUp" })
	public void prepareAdmin() throws JaxmppException {
		adminJaxmpp = createJaxmppAdmin();
		adminJID = adminJaxmpp.getSessionObject().getUserBareJid();
		adminJaxmpp.login( true );

		target = new HttpHost( getInstanceHostname(), Integer.parseInt( getHttpPort() ), "http" );
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		final Object adminBareJid = adminJaxmpp.getSessionObject().getUserProperty( SessionObject.USER_BARE_JID );
		final String adminPassword = adminJaxmpp.getSessionObject().getUserProperty( SessionObject.PASSWORD );
		final AuthScope authScope = new AuthScope( target.getHostName(), target.getPort() );
		log("authScope: " + authScope.toString());
		final UsernamePasswordCredentials userPass = new UsernamePasswordCredentials( adminBareJid.toString(), adminPassword );
		log("UsernamePasswordCredentials: " + userPass + " / adminPassword: " + adminPassword );
		credsProvider.setCredentials(authScope, userPass);
		log("credsProvider: " + credsProvider.getCredentials(authScope ));
		httpClient = HttpClients.custom().setDefaultCredentialsProvider( credsProvider )
				.setDefaultRequestConfig( RequestConfig.custom().setSocketTimeout( 5000 ).build() ).build();


		localContext = HttpClientContext.create();
		AuthCache authCache = new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();
		authCache.put( target, basicAuth );
		localContext.setAuthCache( authCache );


	}

	@BeforeMethod
	public void prepareTest() throws JaxmppException, InterruptedException {
		userRegularJaxmpp = prepareUser( getDomain(), "user_regular" );
		userRegularJID = userRegularJaxmpp.getSessionObject().getUserBareJid();

		userRegular1Jaxmpp_whitelist = prepareUser( getDomain(), "user_regular1_whitelist" );
		userRegular1JID_whitelist = userRegular1Jaxmpp_whitelist.getSessionObject().getUserBareJid();
		log( "userRegular1Jaxmpp_whitelist: " + userRegular1Jaxmpp_whitelist );
		log( "userRegular1JID_whitelist: " + userRegular1JID_whitelist );

		userRegular2Jaxmpp_blacklist = prepareUser( getDomain(), "user_regular2_blacklist" );
		userRegular2JID_blacklist = userRegular2Jaxmpp_blacklist.getSessionObject().getUserBareJid();

	}

	private Jaxmpp prepareUser( String domain, String username )
			throws JaxmppException, InterruptedException {
		BareJID tmp = createUserAccount( "user", username + nextRnd().toLowerCase(), domain );
		Jaxmpp cnt = createJaxmpp( tmp.getLocalpart(), tmp );
		cnt.login( true );
		return cnt;
	}

	@AfterMethod
	public void cleanUpTest() throws JaxmppException, InterruptedException {
		tearDownUser( userRegularJaxmpp );
		tearDownUser( userRegular1Jaxmpp_whitelist );
		tearDownUser( userRegular2Jaxmpp_blacklist );

	}

	private void tearDownUser( Jaxmpp user ) throws JaxmppException, InterruptedException {
		if ( null != user ){
			if ( !user.isConnected() ){
				user.login( true );
			}
			removeUserAccount( user );
		}
	}

	@AfterClass
	public void tearDownAdmin() throws JaxmppException, IOException {
		adminJaxmpp.disconnect();
		httpClient.close();
	}


	private void reloginUser( Jaxmpp user ) throws InterruptedException, JaxmppException {
		if ( user.isConnected() ){
			user.disconnect( true );
			Thread.sleep( 5 * 100 );
			user.login( true );
		}
	}

	@Test(
			testName = "#2928: REST API for Setting/Updating Privacy Rules",
			description = "#2928: Test updating configuration",
			enabled = true
	)
	public void testConfigurationUpdating() throws Exception {

		String rulseString = "1|allow|self;2|allow|jid|" + adminJID.toString() + ";4|deny|all";
		updateContactFiltering( userRegularJID, DomainFilterPolicy.CUSTOM, rulseString );

		rulseString = "1|allow|all";
		updateContactFiltering( userRegularJID, DomainFilterPolicy.CUSTOM, rulseString );

	}

	@Test(
			testName = "#2928: REST API for Setting/Updating Privacy Rules - test case",
			description = "#2928: Test message filtering",
			enabled = true
	)
	public void testMessageFiltering() throws Exception {

		testSendAndWait( userRegularJaxmpp, userRegularJaxmpp );
		testSendAndWait( userRegularJaxmpp, userRegular1Jaxmpp_whitelist );
		testSendAndWait( userRegularJaxmpp, userRegular2Jaxmpp_blacklist );

		String rulseString;

		// we only allow self-messages
		rulseString = "1|allow|self;4|deny|all";
		updateContactFiltering( userRegularJID, DomainFilterPolicy.CUSTOM, rulseString );
		// we need to relogin to update policy
		reloginUser( userRegularJaxmpp );

		// works
		testSendAndWait( userRegularJaxmpp, userRegularJaxmpp );

		// fail!
		sendAndFail( userRegularJaxmpp, userRegular1Jaxmpp_whitelist );
		sendAndFail( userRegularJaxmpp, userRegular2Jaxmpp_blacklist );

		// update policy to allow whitelist JID
		rulseString = "1|allow|self;2|allow|jid|" + userRegular1JID_whitelist.toString() + ";4|deny|all";
		updateContactFiltering( userRegularJID, DomainFilterPolicy.CUSTOM, rulseString );
		// we need to relogin to update policy
		reloginUser( userRegularJaxmpp );

		// works
		testSendAndWait( userRegularJaxmpp, userRegularJaxmpp );
		testSendAndWait( userRegularJaxmpp, userRegular1Jaxmpp_whitelist );

		// fail!
		sendAndFail( userRegularJaxmpp, userRegular2Jaxmpp_blacklist );

	}

	public void updateContactFiltering( BareJID user, DomainFilterPolicy policy, String rule ) throws Exception {


		HttpPost postRequest = new HttpPost( "/rest/adhoc/sess-man@" + getDomain( 0 ) );
		postRequest.addHeader( "Api-Key", getApiKey() );

		Element command = createCommand(user, policy, rule);

		StringEntity entity = new StringEntity( command.getAsString() );
		entity.setContentType( "application/xml" );
		postRequest.setEntity( entity );

		log( "postRequest: " + postRequest.toString() );
		log( "command: " + command.getAsString() );
		log( "target: " + target );
		log( "entity: " + entity );
		log( "entity: " + inputStreamToString(entity.getContent()) );

		HttpResponse response = httpClient.execute( target, postRequest, localContext );

		log( "response: " + response.toString() );
		String responseContent = response.getEntity() != null
														 ? inputStreamToString( response.getEntity().getContent() )
														 : "";
		log( "response entity: " + responseContent );

		String val = "to a new value: CUSTOM (domains list: " + rule + ") for user: " + user.toString().toLowerCase();

		assertEquals( response.getStatusLine().getStatusCode(), 200 );
		boolean responseContains = responseContent.contains( val );

		log( "contains: " + responseContains );

		assertTrue( responseContains, "New settings returned" );

	}

	private Element createCommand( BareJID user, DomainFilterPolicy policy, String rule ) throws XMLException {
		Element command = ElementFactory.create( "command" );
		Element node = ElementFactory.create( "node", "user-domain-perm", null );
		Element fields = ElementFactory.create( "fields" );
		Element item = ElementFactory.create( "item" );
		Element var = ElementFactory.create( "var", "jid", null );
		Element value = ElementFactory.create( "value", user.toString(), null );
		item.addChild( var );
		item.addChild( value );
		fields.addChild( item );
		item = ElementFactory.create( "item" );
		var = ElementFactory.create( "var", "fiteringPolicy", null );
		value = ElementFactory.create( "value", policy.toString().toUpperCase(), null );
		fields.addChild( item );
		item.addChild( var );
		item.addChild( value );
		item = ElementFactory.create( "item" );
		var = ElementFactory.create( "var", "filteringList", null );
		value = ElementFactory.create( "value", rule, null );
		fields.addChild( item );
		item.addChild( var );
		item.addChild( value );
		command.addChild( fields );
		command.addChild( node );
		return command;
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
