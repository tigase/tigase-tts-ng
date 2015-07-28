package tigase.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import static tigase.TestLogger.log;

public class Test2939 extends AbstractTest {

	public static byte[] getBytesFromInputStream(InputStream is) throws IOException {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();) {
			byte[] buffer = new byte[0xFFFF];

			for (int len; (len = is.read(buffer)) != -1;)
				os.write(buffer, 0, len);

			os.flush();

			return os.toByteArray();
		}
	}

	/**
	 * Number of domain. Domain must be separately configured for this test!
	 */

	@Test(groups = { "TLS - Client Cert" }, description = "Two-way TLS with client certificate")
	public void testConnectionWithCertificate() throws Exception {
		log("== testConnectionWithCertificate");
		final String domain = props.getProperty("server.client_auth.domain");
		String u = props.getProperty("test.admin.username");
		String p = props.getProperty("test.admin.password");

		Logger logger = Logger.getLogger("tigase.jaxmpp");
		Handler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
		logger.setLevel(Level.ALL);

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

		InputStreamReader crtStream = new InputStreamReader(getClass().getResourceAsStream("/client.pem"));
		CertificateEntry ce = CertificateUtil.parseCertificate(crtStream);
		log(ce.toString());
		crtStream.close();

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, "".toCharArray());
		keyStore.setKeyEntry("client", ce.getPrivateKey(), "".toCharArray(), ce.getCertChain());
		kmf.init(keyStore, "".toCharArray());

		jaxmpp.getSessionObject().setProperty(SocketConnector.KEY_MANAGERS_KEY, kmf.getKeyManagers());

		try {
			jaxmpp.login(true);
		} catch (Exception e) {
			fail(e);
		} finally {
			Assert.assertNotNull(ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()));
		}
	}

	@Test(groups = { "TLS - Client Cert" }, description = "Two-way TLS without client certificate")
	public void testConnectionWithoutCertificate() throws Exception {
		log("== testConnectionWithoutCertificate");
		final String domain = props.getProperty("server.client_auth.domain");
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
		log("== testConnectionWithWrongCertificate");
		final String domain = props.getProperty("server.client_auth.domain");
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
		log(c.toString());
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
