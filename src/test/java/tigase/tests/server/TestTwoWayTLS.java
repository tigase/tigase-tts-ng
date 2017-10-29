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
package tigase.tests.server;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.cert.CertificateEntry;
import tigase.cert.CertificateUtil;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractTest;
import tigase.tests.utils.Account;

import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static tigase.TestLogger.log;

public class TestTwoWayTLS
		extends AbstractTest {

	private Jaxmpp jaxmpp;
	private Account user;

	public static byte[] getBytesFromInputStream(InputStream is) throws IOException {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();) {
			byte[] buffer = new byte[0xFFFF];

			for (int len; (len = is.read(buffer)) != -1; ) {
				os.write(buffer, 0, len);
			}

			os.flush();

			return os.toByteArray();
		}
	}

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("admin")
				.setUsername(props.getProperty("test.admin.username"))
				.setDomain(props.getProperty("server.client_auth.domain"))
				.setPassword(props.getProperty("test.admin.password"))
				.build();

		jaxmpp = user.createJaxmpp().setHost(getInstanceHostname()).setConfigurator(jaxmpp -> {
			jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
			jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, Boolean.TRUE);
			jaxmpp.getProperties().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.FALSE);
			return jaxmpp;
		}).build();
	}

	/**
	 * Number of domain. Domain must be separately configured for this test!
	 *
	 * @throws Exception related to loading of the certificate
	 */

	@Test(groups = {"TLS - Client Cert"}, description = "Two-way TLS with client certificate")
	public void testConnectionWithCertificate() throws Exception {
		log("== testConnectionWithCertificate");
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

	@Test(groups = {"TLS - Client Cert"}, description = "Two-way TLS without client certificate")
	public void testConnectionWithoutCertificate() throws Exception {
		log("== testConnectionWithoutCertificate");
		try {
			jaxmpp.login(true);
		} catch (Exception e) {
		} finally {
			Assert.assertNull(ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()));
		}
	}

	@Test(groups = {"TLS - Client Cert"}, description = "Two-way TLS with wrong client certificate")
	public void testConnectionWithWrongCertificate() throws Exception {
		log("== testConnectionWithWrongCertificate");
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, "".toCharArray());
		KeyPair keyPair = CertificateUtil.createKeyPair(1024, "");
		X509Certificate c = CertificateUtil.createSelfSignedCertificate("alice@coffeebean.local", "domain", "org",
																		"org", "tr", "kp", "PL", keyPair);
		log(c.toString());
		keyStore.setKeyEntry("client", keyPair.getPrivate(), "".toCharArray(), new Certificate[]{c});
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
