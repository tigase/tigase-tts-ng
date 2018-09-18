/*
 * AccountsManager.java
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
package tigase.tests.utils;

import org.testng.Assert;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageCarbonsModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.UnifiedRegistrationForm;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.MessageArchivingModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;
import static tigase.tests.AbstractTest.LOG_PREFIX_KEY;

/**
 * Created by andrzej on 22.04.2017.
 */
public class AccountsManager
		extends AbstractManager {

	private final static TrustManager[] dummyTrustManagers = new X509TrustManager[]{new X509TrustManager() {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}};
	private final ConcurrentHashMap<Object, Set<Account>> accounts = new ConcurrentHashMap<>();

	public AccountsManager(AbstractTest abstractTest) {
		super(abstractTest);
	}

	public void add(Account account) {
		Object key = getScopeKey();
		add(account, key);
	}

	public void add(Account account, Object key) {
		if (accounts.computeIfAbsent(key, (k) -> new CopyOnWriteArraySet<>()).add(account)) {
			System.out.println("created account = " + account);
		}
	}

	public Jaxmpp createJaxmpp(String logPrefix) {
		try {
			Jaxmpp jaxmpp1 = new Jaxmpp();
			jaxmpp1.getSessionObject().setProperty(SocketConnector.TRUST_MANAGERS_KEY, dummyTrustManagers);
			if (logPrefix != null) {
				jaxmpp1.getSessionObject().setUserProperty(LOG_PREFIX_KEY, logPrefix);
			}
			jaxmpp1.getSessionObject().setUserProperty(SocketConnector.COMPRESSION_DISABLED_KEY, Boolean.TRUE);
			jaxmpp1.getEventBus().addListener(test.connectorListener);

			jaxmpp1.getModulesManager().register(new InBandRegistrationModule());
			jaxmpp1.getModulesManager().register(new MessageModule());
			jaxmpp1.getModulesManager().register(new MessageCarbonsModule());
			jaxmpp1.getModulesManager().register(new MucModule());
			jaxmpp1.getModulesManager().register(new AdHocCommansModule());
			jaxmpp1.getModulesManager().register(new RosterModule());
			jaxmpp1.getModulesManager().register(new MessageArchivingModule());
			jaxmpp1.getModulesManager().register(new PubSubModule());
			jaxmpp1.getModulesManager().register(new VCardModule());

			tigase.jaxmpp.j2se.Presence.initialize(jaxmpp1);

			return jaxmpp1;
		} catch (JaxmppException e) {
			test.fail(e);
			throw new RuntimeException(e);
		}
	}

	public Account registerAccount(AccountBuilder builder, Account account)
			throws JaxmppException, InterruptedException {
		final String server = test.getInstanceHostname();
		final Jaxmpp jaxmpp1 = createJaxmpp(builder.getLogPrefix());
		jaxmpp1.getEventBus().addListener(new EventListener() {

			@Override
			public void onEvent(Event<? extends EventHandler> event) {
				log(event != null ? event.toString() : "null event!");
			}
		});
		jaxmpp1.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		if (server != null) {
			jaxmpp1.getConnectionConfiguration().setServer(server);
		}
		jaxmpp1.getConnectionConfiguration().setDomain(builder.getDomain());
		jaxmpp1.getSessionObject().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.TRUE);
		jaxmpp1.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);

		final Mutex mutex = new Mutex();
		jaxmpp1.getEventBus()
				.addHandler(InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
							new InBandRegistrationModule.ReceivedRequestedFieldsHandler() {

								@Override
								public void onReceivedRequestedFields(SessionObject sessionObject, IQ responseStanza,
																	  UnifiedRegistrationForm form) {

									try {
										jaxmpp1.getModule(InBandRegistrationModule.class)
												.register(builder.getUsername(), builder.getPassword(),
														  builder.getEmail(), new AsyncCallback() {

															@Override
															public void onError(Stanza responseStanza,
																				XMPPException.ErrorCondition error)
																	throws JaxmppException {
																mutex.notify("registration");
																log("Account registration error: " + error);
																Assert.fail("Account registration error: " + error);
															}

															@Override
															public void onSuccess(Stanza responseStanza)
																	throws JaxmppException {
																mutex.notify("registrationSuccess");
																mutex.notify("registration");
															}

															@Override
															public void onTimeout() throws JaxmppException {
																mutex.notify("registration");
																log("Account registration failed.");
																Assert.fail("Account registration failed.");
															}
														});
									} catch (JaxmppException e) {
										AbstractTest.fail(e);
									}

								}

							});

		jaxmpp1.login(false);
		mutex.waitFor(1000 * 30, "registration");

		assertTrue("Registration failed!", mutex.isItemNotified("registrationSuccess"));
		jaxmpp1.disconnect(true);

		add(account);
		return account;
	}

	public void remove(Account account) {
		Object key = getScopeKey();
		if (accounts.computeIfAbsent(key, (k) -> new CopyOnWriteArraySet<>()).remove(account)) {
			System.out.println("removed account = " + account);
		}
	}

	protected void scopeFinished(Object key) {
		accounts.getOrDefault(key, new HashSet<>()).forEach(account -> {
			try {
				unregisterAccount(account);
			} catch (JaxmppException | InterruptedException e) {
				Logger.getLogger("tigase").log(Level.WARNING, "failed to remove account " + account, e);
			}
		});
	}

	public void unregisterAccount(BareJID jid) throws JaxmppException, InterruptedException {
		Object key = getScopeKey();
		Account account = accounts.getOrDefault(key, new HashSet<>())
				.stream()
				.filter(acc -> jid.equals(acc.getJid()))
				.findFirst()
				.get();
		unregisterAccount(account);
	}

	public void unregisterAccount(Account account) throws JaxmppException, InterruptedException {
		final Jaxmpp jaxmpp = createJaxmpp(account.getLogPrefix());
		jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);

		final String server = test.getInstanceHostname();
		if (server != null) {
			jaxmpp.getConnectionConfiguration().setServer(server);
		}
		jaxmpp.getConnectionConfiguration().setUserJID(account.getJid());
		jaxmpp.getConnectionConfiguration().setUserPassword(account.getPassword());
		jaxmpp.getSessionObject().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.TRUE);

		jaxmpp.login(true);
		unregisterAccount(jaxmpp, account);
	}

	public void unregisterAccount(Jaxmpp jaxmpp) throws JaxmppException, InterruptedException {
		Object key = getScopeKey();
		final BareJID userJid = jaxmpp.getSessionObject().getUserBareJid();
		Account account = accounts.getOrDefault(key, new HashSet<>())
				.stream()
				.filter(acc -> userJid.equals(acc.getJid()))
				.findFirst()
				.orElse(null);
		unregisterAccount(jaxmpp, account);
	}

	private void unregisterAccount(final Jaxmpp jaxmpp, final Account account)
			throws JaxmppException, InterruptedException {
		if (test.getAdminAccount() == account) {
			// removing admin account skipped!
			return;
		}

		if (test.getAdminAccount() != null) {
			log("Remove account as admin: " + account.getJid());
			Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
			if (!adminJaxmpp.isConnected()) {
				adminJaxmpp.login(true);
			}

			JabberDataElement data = new JabberDataElement(XDataType.submit);
			data.addHiddenField("FORM_TYPE", "http://jabber.org/protocol/admin");
			data.addJidMultiField("accountjids", JID.jidInstance(account.getJid()));

			final Mutex mutex = new Mutex();
			jaxmpp.getModule(AdHocCommansModule.class)
					.execute(JID.jidInstance("sess-man", test.getAdminAccount().getJid().getDomain()),
							 "http://jabber.org/protocol/admin#delete-user", Action.execute, data, new AsyncCallback() {
								@Override
								public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										throws JaxmppException {
									mutex.notify("account:removed:error:" + error.getElementName());
									mutex.notify("account:removed");
								}

								@Override
								public void onSuccess(Stanza responseStanza) throws JaxmppException {
									mutex.notify("account:removed");
								}

								@Override
								public void onTimeout() throws JaxmppException {
									mutex.notify("account:removed");
								}
							});

			mutex.waitFor(30 * 1000, "account:removed");
		} else {
			log("Remove own account as user: " + account.getJid());
			final Mutex mutex = new Mutex();
			final BareJID userJid = account.getJid();
			final JaxmppCore.LoggedOutHandler disconnectionHandler = sessionObject -> {
				log("Disconnected! " + userJid);
				mutex.notifyForce();
			};
			try {

				final JID jid1 = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());
				if (jid1 == null && !jaxmpp.isConnected()) {
					jaxmpp.login(true);
				}
				final JID jid = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());

				jaxmpp.getEventBus().addHandler(JaxmppCore.LoggedOutHandler.LoggedOutEvent.class, disconnectionHandler);

				jaxmpp.getModule(InBandRegistrationModule.class).removeAccount(new AsyncCallback() {

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						log("! Account " + userJid + " removing error: " + error);
						mutex.notify("removed");
						Assert.fail("Account removing error: " + error);
					}

					@Override
					public void onSuccess(Stanza responseStanza) throws JaxmppException {
						log("! Account " + userJid + " removing!! SUCCESS");
						mutex.notify("removedSuccess");
						mutex.notify("removed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						log("Account " + userJid + " removing failed. - timeout");
						mutex.notify("removed");
						Assert.fail("Account removing failed.");
					}
				});
				mutex.waitFor(1000 * 60 * 2, "removed");

				// assertTrue("Account not removed!",
				// mutex.isItemNotified("removedSuccess"));

				jaxmpp.disconnect();
				account.scopeFinished();
				remove(account);

				account.accountUnregistered();
			} finally {
				jaxmpp.getEventBus().remove(JaxmppCore.LoggedOutHandler.LoggedOutEvent.class, disconnectionHandler);
			}
		}
	}
}
