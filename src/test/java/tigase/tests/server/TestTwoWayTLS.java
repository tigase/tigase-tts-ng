/*
 * TestTwoWayTLS.java
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
package tigase.tests.server;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.cert.CertificateEntry;
import tigase.cert.CertificateUtil;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;
import tigase.tests.utils.AccountBuilder;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static tigase.TestLogger.log;

/**
 * The intention of this test is to check client's certificate validation durint TLS (and not SASL-EXTERNAL).
 * To that end, there is client certificate, which path is configured under `server.client_auth.ca_cert` variable,
 * signed by servers certificate (`src/test/resources/server/certs/root_ca.pem`). VHost is configured to: Require TLS,
 * use configured server certificate as CA and *require* client's certificate.
 *
 * There is no authentication done in the test and only passing of STARTTLS and registration afterwards is checked.
 */
public class TestTwoWayTLS
		extends AbstractTest {

	private AccountBuilder accountBuilder;

	@BeforeClass
	public void createRequiredVHost() throws JaxmppException, InterruptedException {
		String vhost = props.getProperty("server.client_auth.domain");
		String caCertPath = new File(getServerConfigBaseDir(),
									 props.getProperty("server.client_auth.ca_cert")).getAbsolutePath();

		createVHost(vhost).setClientCertCA(caCertPath).setClientCertRequired(true).updateIfExists(true).build();
	}

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		accountBuilder = createAccount().setLogPrefix("two-way-tsl-user")
				.setDomain(props.getProperty("server.client_auth.domain"))
				.setRegister(false);
	}

	/**
	 * Number of domain. Domain must be separately configured for this test!
	 *
	 * @throws Exception related to loading of the certificate
	 */

	@Test(groups = {"TLS - Client Cert"}, description = "Two-way TLS with client certificate")
	public void testConnectionWithCertificate() throws Exception {
		log("== test connection with OK certificate");
		CertificateEntry ce = getCertificateEntry("/client.pem");
		KeyManagerFactory kmf = getKeyManagerFactory(ce);

		final Account account = accountBuilder.setLogPrefix("two-way-tsl-user-OK").build();

		try {
			final boolean registered = registerAccount(account, jaxmpp1 -> jaxmpp1.getSessionObject()
					.setProperty(SocketConnector.KEY_MANAGERS_KEY, kmf.getKeyManagers()));
			org.junit.Assert.assertTrue(registered);
		} catch (Exception e) {
			fail(e);
		}
	}

	@Test(groups = {"TLS - Client Cert"}, description = "Two-way TLS without client certificate")
	public void testConnectionWithoutCertificate() throws Exception {
		log("== testing connection WITHOUT certificate");
		final Account account = accountBuilder.setLogPrefix("two-way-tsl-user-WITHOUT").build();

		final boolean registered = registerAccount(account, jaxmpp1 -> {
		});
		assertFalse(registered);
	}

	@Test(groups = {"TLS - Client Cert"}, description = "Two-way TLS with wrong client certificate")
	public void testConnectionWithWrongCertificate() throws Exception {
		log("== testing connection with WRONG certificate");
		CertificateEntry ce = getSelfSignedCertificateEntry();
		KeyManagerFactory kmf = getKeyManagerFactory(ce);

		final Account account = accountBuilder.setLogPrefix("two-way-tsl-user-WRONG").build();
		final boolean registered = registerAccount(account, jaxmpp1 -> jaxmpp1.getSessionObject()
				.setProperty(SocketConnector.KEY_MANAGERS_KEY, kmf.getKeyManagers()));
		assertFalse(registered);
	}

	public boolean registerAccount(Account account, Consumer<Jaxmpp> consumer)
			throws JaxmppException, InterruptedException {
		final String server = getInstanceHostname();

		final Jaxmpp jaxmpp = account.createJaxmpp()
				.setConnected(false)
				.setHost(getInstanceHostname())
				.setConfigurator(jaxmppClient -> {
					jaxmppClient.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
					jaxmppClient.getProperties()
							.setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, Boolean.TRUE);
					jaxmppClient.getProperties().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.FALSE);
					return jaxmppClient;
				})
				.build();

		consumer.accept(jaxmpp);

//		jaxmpp.getEventBus().addListener(event -> log(event != null ? event.toString() : "null event!"));

		if (server != null) {
			jaxmpp.getConnectionConfiguration().setServer(server);
		}

		final Mutex mutex = new Mutex();
		jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		jaxmpp.getConnectionConfiguration().setDomain(account.getJid().getDomain());
		jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);
//		jaxmpp.getEventBus().addHandler(Connector.DisconnectedHandler.DisconnectedEvent.class, sessionObject -> {
//			log("Disconnected during registration!");
//			mutex.notify("registration");
//		});
		jaxmpp.getEventBus()
				.addHandler(InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
							(sessionObject, responseStanza, form) -> {

								try {
									final String username = account.getJid().getLocalpart();
									jaxmpp.getModule(InBandRegistrationModule.class)
											.register(username, account.getPassword(),
													  getEmailAccountForUser(username).email, new AsyncCallback() {

														@Override
														public void onError(Stanza responseStanza,
																			XMPPException.ErrorCondition error) {
															mutex.notify("registration");
															log("Account registration error: " + error);
															Assert.fail("Account registration error: " + error);
														}

														@Override
														public void onSuccess(Stanza responseStanza) {
															mutex.notify("registrationSuccess");
															mutex.notify("registration");
														}

														@Override
														public void onTimeout() {
															mutex.notify("registration");
															log("Account registration failed.");
															Assert.fail("Account registration failed.");
														}
													});
								} catch (JaxmppException e) {
									AbstractTest.fail(e);
								}

							});

		jaxmpp.login(true);
		mutex.waitFor(1000 * 30, "registration");

		final boolean registrationSuccess = mutex.isItemNotified("registrationSuccess");
		if (jaxmpp.isConnected()) {
			jaxmpp.disconnect(true);
		}
//		if (registrationSuccess) {
//			accountManager.add(account);
//		}
		return registrationSuccess;
	}

	private CertificateEntry getSelfSignedCertificateEntry()
			throws NoSuchAlgorithmException, CertificateException, SignatureException, NoSuchProviderException,
				   InvalidKeyException, IOException {
		KeyPair keyPair = CertificateUtil.createKeyPair(1024, "");
		X509Certificate c = CertificateUtil.createSelfSignedCertificate("alice@coffeebean.local", "domain", "org",
																		"org", "tr", "kp", "PL", keyPair);

		final CertificateEntry certificateEntry = new CertificateEntry();
		certificateEntry.setPrivateKey(keyPair.getPrivate());
		certificateEntry.setCertChain(new Certificate[]{c});
		log(c.toString());
		return certificateEntry;

	}

	private CertificateEntry getCertificateEntry(String fileName)
			throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException {
		InputStreamReader crtStream = new InputStreamReader(getClass().getResourceAsStream(fileName));
		CertificateEntry ce = CertificateUtil.parseCertificate(crtStream);
		crtStream.close();
		log(ce.toString());
		return ce;
	}

	private KeyManagerFactory getKeyManagerFactory(CertificateEntry ce)
			throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException,
				   UnrecoverableKeyException {
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, "".toCharArray());
		keyStore.setKeyEntry("client", ce.getPrivateKey(), "".toCharArray(), ce.getCertChain());
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, "".toCharArray());
		return kmf;
	}
}
