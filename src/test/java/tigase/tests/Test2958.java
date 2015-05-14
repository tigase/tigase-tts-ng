package tigase.tests;

import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.util.DateTimeFormatter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Assert;
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

public class Test2958 extends AbstractTest {

	private static final int SECOND = 1000;
	private static final int MINUTE = SECOND * 60;

	DateTimeFormatter dtf = new DateTimeFormatter();

	private CloseableHttpClient httpClient;
	private HttpHost target;
	private HttpClientContext localContext;

	BareJID adminJID;
	Jaxmpp adminJaxmpp;

	BareJID userRegularJID;
	Jaxmpp userRegularJaxmpp;

	final Mutex mutex = new Mutex();

	@BeforeClass(dependsOnMethods = { "setUp" })
	private void prepareAdmin() throws JaxmppException {
		adminJaxmpp = createJaxmppAdmin();
		adminJID = adminJaxmpp.getSessionObject().getUserBareJid();

		addMessageListener( adminJaxmpp );

		adminJaxmpp.login( true );

		target = new HttpHost( getInstanceHostname(), Integer.parseInt( getHttpPort() ), "http" );
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		final Object adminBareJid = adminJaxmpp.getSessionObject().getUserProperty( SessionObject.USER_BARE_JID );
		final String adminPassword = adminJaxmpp.getSessionObject().getUserProperty( SessionObject.PASSWORD );
		final AuthScope authScope = new AuthScope( target.getHostName(), target.getPort() );
		log( "authScope: " + authScope.toString() );
		final UsernamePasswordCredentials userPass = new UsernamePasswordCredentials( adminBareJid.toString(), adminPassword );
		log( "UsernamePasswordCredentials: " + userPass + " / adminPassword: " + adminPassword );
		credsProvider.setCredentials( authScope, userPass );
		log( "credsProvider: " + credsProvider.getCredentials( authScope ) );
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
	}

	private Jaxmpp prepareUser( String domain, String username )
			throws JaxmppException, InterruptedException {
		BareJID tmp = createUserAccount( "user", username + nextRnd().toLowerCase(), domain );
		Jaxmpp cnt = createJaxmpp( tmp.getLocalpart(), tmp );

		addMessageListener( cnt );

		cnt.login( true );
		return cnt;
	}

	private void addMessageListener( Jaxmpp cnt ) {
		cnt.getEventBus().addHandler( MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
																	new MessageModule.MessageReceivedHandler() {

																		@Override
																		public void onMessageReceived( SessionObject sessionObject, Chat chat, Message stanza ) {
																			try {
																				Element items = stanza.getFirstChild( "event" ).getFirstChild( "items" );
																				Element item = items.getFirstChild( "item" );
																				Element content = item.getFirstChild( "content" );
																				String msg = content.getValue();
																				mutex.notify( sessionObject.getUserBareJid() + ":message:received:" + msg );
																			} catch ( XMLException ex ) {
																				Logger.getLogger( Test2936.class.getName() ).log( Level.SEVERE, null, ex );
																			}
																		}

																	} );
	}

	@AfterMethod
	public void cleanUpTest() throws JaxmppException, InterruptedException {
		tearDownUser( userRegularJaxmpp );

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
	private void tearDownAdmin() throws JaxmppException, IOException {
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
			testName = "#2958: REST API for PubSub Message Expiration - test case",
			description = "#2958: Test skipping expired message",
			enabled = true
	)
	public void testMessageExpiration() throws Exception {

		final String nodeName = "node_" + nextRnd().toLowerCase();

		BareJID pubsubJID = BareJID.bareJIDInstance( "pubsub." + getDomain( 0 ) );
		PubSubModule pubSubModule = adminJaxmpp.getModule( PubSubModule.class );

		pubSubModule.createNode( pubsubJID, nodeName, new AsyncCallback() {

			@Override
			public void onError( Stanza responseStanza, XMPPException.ErrorCondition error ) throws JaxmppException {
				mutex.notify( nodeName + ":create_node" );
			}

			@Override
			public void onSuccess( Stanza responseStanza ) throws JaxmppException {
				mutex.notify( nodeName + ":create_node" );
				mutex.notify( nodeName + ":create_node:success" );
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify( nodeName + ":create_node" );
			}
		} );

		mutex.waitFor( 10 * 1000, nodeName + ":create_node" );
		Assert.assertTrue( "Node created", mutex.isItemNotified( nodeName + ":create_node:success" ) );

		subscribeUser( pubSubModule, pubsubJID, JID.jidInstance( userRegularJID ), nodeName );

		// publishing normal message (to online)
		log( "\n\n\n===== publishing normal message (to online) \n\n\n" );

		String message = nextRnd().toLowerCase();
		publishToPubsub( nodeName, null, null, "content_" + message );

		mutex.waitFor( 10 * 1000, userRegularJID + ":message:received:content_" + message );
		Assert.assertTrue( "Message received", mutex.isItemNotified( userRegularJID + ":message:received:content_" + message ) );

		// publishing already old message - expecting message being filtered out (to online)
		log( "\n\n\n===== publishing already old message - expecting message being filtered out (to online) \n\n\n" );
		message = nextRnd().toLowerCase();
		Date timestamp = new Date( new Date().getTime() - 5 * SECOND );
		publishToPubsub( nodeName, null, timestamp, "content_" + message );

		mutex.waitFor( 5 * 1000, userRegularJID + ":message:received:content_" + message );
		Assert.assertFalse( "Message not received", mutex.isItemNotified( userRegularJID + ":message:received:content_" + message ) );

		// testing offline messages
		// publishing normal message (to offline)
		log( "\n\n\n===== publishing normal message (to offline) \n\n\n" );
		userRegularJaxmpp.disconnect();
		Thread.sleep( 1 * SECOND );

		message = nextRnd().toLowerCase();
		publishToPubsub( nodeName, null, null, "content_" + message );

		userRegularJaxmpp.login( true );
		Thread.sleep( 1 * SECOND );

		mutex.waitFor( 10 * SECOND, userRegularJID + ":message:received:content_" + message );
		Assert.assertTrue( "Message received", mutex.isItemNotified( userRegularJID + ":message:received:content_" + message ) );

		// publishing already old message - expecting message being filtered out (to offline)
		log( "\n\n\n===== publishing already old message - expecting message being filtered out (to offline) \n\n\n" );

		subscribeUser( pubSubModule, pubsubJID, JID.jidInstance( adminJID ), nodeName );

		userRegularJaxmpp.disconnect();
		Thread.sleep( 1 * SECOND );

		message = nextRnd().toLowerCase();

		// publish message with delay 5 seconds into the future
		timestamp = new Date( new Date().getTime() + 5 * SECOND );
		publishToPubsub( nodeName, null, timestamp, "content_" + message );

		// let's wait for regular user untill message expire
		Thread.sleep( 10 * SECOND );

		userRegularJaxmpp.login( true );

		// user back online, admin was online - user should not receive message and admin should
		mutex.waitFor( 5 * 1000, userRegularJID + ":message:received:content_" + message );
		mutex.waitFor( 5 * 1000, adminJID + ":message:received:content_" + message );
		Assert.assertFalse( "Message not received", mutex.isItemNotified( userRegularJID + ":message:received:content_" + message ) );
		Assert.assertTrue( "Message received", mutex.isItemNotified( adminJID + ":message:received:content_" + message ) );

		pubSubModule.deleteNode( pubsubJID, nodeName, new AsyncCallback() {

			@Override
			public void onError( Stanza responseStanza, XMPPException.ErrorCondition error ) throws JaxmppException {
				mutex.notify( nodeName + ":delete_node" );
			}

			@Override
			public void onSuccess( Stanza responseStanza ) throws JaxmppException {
				mutex.notify( nodeName + ":delete_node" );
				mutex.notify( nodeName + ":delete_node:success" );
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify( nodeName + ":delete_node" );
			}
		} );

		mutex.waitFor( 10 * 1000, nodeName + ":delete_node" );
		Assert.assertTrue( "Node created", mutex.isItemNotified( nodeName + ":delete_node:success" ) );

	}

	private void subscribeUser( PubSubModule pubSubModule, BareJID pubsubJID, JID user, final String nodeName ) throws InterruptedException, JaxmppException {
		pubSubModule.subscribe( pubsubJID, nodeName, user, new PubSubModule.SubscriptionAsyncCallback() {

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify( nodeName + ":subscribe_node" );
			}

			@Override
			protected void onEror( IQ response, XMPPException.ErrorCondition errorCondition, PubSubErrorCondition pubSubErrorCondition ) throws JaxmppException {
				mutex.notify( nodeName + ":subscribe_node" );
			}

			@Override
			protected void onSubscribe( IQ response, PubSubModule.SubscriptionElement subscriptionElement ) {
				mutex.notify( nodeName + ":subscribe_node:success" );
				mutex.notify( nodeName + ":subscribe_node" );
			}
		} );

		mutex.waitFor( 10 * 1000, nodeName + ":subscribe_node" );
		Assert.assertTrue( "Node subscribed", mutex.isItemNotified( nodeName + ":subscribe_node:success" ) );
	}

	private void publishToPubsub( String nodeid, String itemId, Date timestamp, String content ) throws Exception {

		HttpPost postRequest = new HttpPost( "/rest/pubsub/pubsub." + getDomain( 0 ) + "/publish-item" );
		postRequest.addHeader( "Api-Key", getApiKey() );

		Element command = createPublishCommand( nodeid, itemId, timestamp, content );

		StringEntity entity = new StringEntity( command.getAsString() );
		entity.setContentType( "application/xml" );
		postRequest.setEntity( entity );

		log( "postRequest: " + postRequest.toString() );
		log( "command: " + command.getAsString() );
		log( "target: " + target );
		log( "entity: " + entity );
		log( "entity: " + inputStreamToString( entity.getContent() ) );

		HttpResponse response = httpClient.execute( target, postRequest, localContext );

		log( "response: " + response.toString() );
		String responseContent = response.getEntity() != null
														 ? inputStreamToString( response.getEntity().getContent() )
														 : "";
		log( "response entity: " + responseContent );

		assertEquals( response.getStatusLine().getStatusCode(), 200 );

		boolean responseContains = responseContent.toLowerCase().contains( "Operation successful".toLowerCase() );
		log( "contains: " + responseContains );
		assertTrue( responseContains, "Publishing was successful" );

	}

	private Element createPublishCommand( String nodeid, String itemId, Date timestamp, String content ) throws XMLException {
		Element data = ElementFactory.create( "data" );
		Element node = ElementFactory.create( "node", nodeid, null );
		data.addChild( node );
		Element itemid = ElementFactory.create( "item-id", itemId, null );
		data.addChild( itemid );
		Element expireat = ElementFactory.create( "expire-at", ( timestamp != null ? dtf.formatDateTime( timestamp ) : null ), null );
		data.addChild( expireat );
		Element entry = ElementFactory.create( "entry" );
		if ( content != null ){
			Element cnt = ElementFactory.create( "content" );
			cnt.setValue( content );
			entry.addChild( cnt );
		}
		data.addChild( entry );
		return data;
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
