package tigase.tests;

import java.io.File;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;

import org.testng.Assert;
import org.testng.annotations.Test;

import tigase.cert.CertificateEntry;
import tigase.cert.CertificateUtil;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

public class Test2939 extends AbstractTest {

	/**
	 * Number of domain. Domain must be separately configured for this test!
	 */
	private static final int DOMAIN_NUMBER = 1;

	@Test(groups = { "TLS - Client Cert" }, description = "Two-way TLS with client certificate")
	public void testConnectionWithCertificate() throws Exception {
		Logger logger = Logger.getLogger("tigase.jaxmpp");
		Handler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
		logger.setLevel(Level.ALL);

		final String domain = getDomain(DOMAIN_NUMBER);
		String u = props.getProperty("test.admin.username");
		String p = props.getProperty("test.admin.password");

		final BareJID userJID = BareJID.bareJIDInstance(u, domain);

		Jaxmpp jaxmpp = createJaxmpp("admin");
		jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, Boolean.TRUE);

		jaxmpp.getConnectionConfiguration().setUserJID(userJID);
		jaxmpp.getConnectionConfiguration().setUserPassword(p);
		String instanceHostname = getInstanceHostname();
		if (instanceHostname != null) {
			jaxmpp.getConnectionConfiguration().setServer(instanceHostname);
		}

		URL url = getClass().getResource("/client.pem");
		CertificateEntry ce = CertificateUtil.loadCertificate(new File(url.toURI()));
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, "".toCharArray());
		keyStore.setKeyEntry("client", ce.getPrivateKey(), "".toCharArray(), ce.getCertChain());
		kmf.init(keyStore, "".toCharArray());

		jaxmpp.getSessionObject().setProperty(SocketConnector.KEY_MANAGERS_KEY, kmf.getKeyManagers());

		try {
			jaxmpp.login(true);
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		} finally {
			Assert.assertNotNull(ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()));
		}
	}

	@Test(groups = { "TLS - Client Cert" }, description = "Two-way TLS without client certificate")
	public void testConnectionWithoutCertificate() throws Exception {
		final String domain = getDomain(DOMAIN_NUMBER);
		String u = props.getProperty("test.admin.username");
		String p = props.getProperty("test.admin.password");

		final BareJID userJID = BareJID.bareJIDInstance(u, domain);

		Jaxmpp jaxmpp = createJaxmpp("admin");
		jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, Boolean.TRUE);

		jaxmpp.getConnectionConfiguration().setUserJID(userJID);
		jaxmpp.getConnectionConfiguration().setUserPassword(p);
		String instanceHostname = getInstanceHostname();
		if (instanceHostname != null) {
			jaxmpp.getConnectionConfiguration().setServer(instanceHostname);
		}

		try {
			jaxmpp.login(true);
		} catch (Exception e) {
		} finally {
			Assert.assertNull(ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()));
		}
	}

	@Test(groups = { "TLS - Client Cert" }, description = "Two-way TLS with wrong client certificate")
	public void testConnectionWithWrongCertificate() throws Exception {
		final String domain = getDomain(DOMAIN_NUMBER);
		String u = props.getProperty("test.admin.username");
		String p = props.getProperty("test.admin.password");

		final BareJID userJID = BareJID.bareJIDInstance(u, domain);

		Jaxmpp jaxmpp = createJaxmpp("admin");
		jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, Boolean.TRUE);

		jaxmpp.getConnectionConfiguration().setUserJID(userJID);
		jaxmpp.getConnectionConfiguration().setUserPassword(p);
		String instanceHostname = getInstanceHostname();
		if (instanceHostname != null) {
			jaxmpp.getConnectionConfiguration().setServer(instanceHostname);
		}

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, "".toCharArray());
		KeyPair keyPair = CertificateUtil.createKeyPair(1024, "");
		X509Certificate c = CertificateUtil.createSelfSignedCertificate("alice@coffeebean.local", "domain", "org", "org", "tr",
				"kp", "PL", keyPair);
		keyStore.setKeyEntry("client", keyPair.getPrivate(), "".toCharArray(), new Certificate[] { c });
		kmf.init(keyStore, "".toCharArray());

		jaxmpp.getSessionObject().setProperty(SocketConnector.KEY_MANAGERS_KEY, kmf.getKeyManagers());

		try {
			jaxmpp.login(true);
		} catch (Exception e) {
		} finally {
			Assert.assertNull(ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()));
		}
	}
}
