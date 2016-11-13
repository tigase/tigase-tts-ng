/*
 * AbstractTest.java
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

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.Connector.StanzaSendingHandler.StanzaSendingEvent;
import tigase.jaxmpp.core.client.XMPPException.ErrorCondition;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.*;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageCarbonsModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.MessageArchivingModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence.Show;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;

public abstract class AbstractTest {

	private static int counter = 0;

	private final static HashSet<BareJID> createdAccounts = new HashSet<BareJID>();

	public static final String LOG_PREFIX_KEY = "LOG_PREFIX";

	private static Handler ngLogger = null;

	private final static Random randomGenerator = new SecureRandom();

	private final static TrustManager[] dummyTrustManagers = new X509TrustManager[] { new X509TrustManager() {

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
	} };

	public static final String nextRnd() {
		int r = randomGenerator.nextInt() & 0x7fffffff;
		return Integer.toString(r, 36) + String.format("%04d", ++counter);
	}

	protected boolean connectorLogsEnabled = true;
	protected final EventListener connectorListener = new EventListener() {

		@Override
		public void onEvent(Event<? extends EventHandler> event) {
			if (!connectorLogsEnabled) return;
			String t = "";
			if (event instanceof JaxmppEvent<?>) {
				JaxmppEvent<?> e = (JaxmppEvent<?>) event;

				String lp = e.getSessionObject().getUserProperty(LOG_PREFIX_KEY);
				if (lp != null) {
					t += lp + " :: ";
				}

				JID binded = ResourceBinderModule.getBindedJID(e.getSessionObject());
				if (binded != null) {
					t += binded.toString();
				} else {
					t += e.getSessionObject().getUserBareJid();
				}

			}

			try {
				if (event instanceof Connector.StanzaSendingHandler.StanzaSendingEvent) {
					Connector.StanzaSendingHandler.StanzaSendingEvent e = (StanzaSendingEvent) event;
					log(t + " >> " + e.getStanza().getAsString());
				} else if (event instanceof Connector.StanzaReceivedHandler.StanzaReceivedEvent) {
					Connector.StanzaReceivedHandler.StanzaReceivedEvent e = (Connector.StanzaReceivedHandler.StanzaReceivedEvent) event;
					log(t + " << " + e.getStanza().getAsString());
				}
			} catch (Exception e) {
				fail(e);
			}
		}
	};

	protected Properties props;

	protected String addVhost(final Jaxmpp adminJaxmpp, final String prefix) throws JaxmppException, InterruptedException {
		final String addVHostCommand = "comp-repo-item-add";
		final String VHost = prefix + "_" + nextRnd().toLowerCase() + "." + getDomain();
		final String mutexCommand = addVHostCommand + "-" + VHost;

		final Mutex mutex = new Mutex();

		final BareJID adminJID = adminJaxmpp.getSessionObject().getUserBareJid();

		log("jaxmppa: " + adminJaxmpp.getSessionObject().getUserBareJid());
		adminJaxmpp.getModule(AdHocCommansModule.class).execute(JID.jidInstance("vhost-man", adminJID.getDomain()),
				addVHostCommand, null, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
						mutex.notify("1:" + mutexCommand, "1:error");
					}

					@Override
					protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
							throws JaxmppException {
						mutex.notify("1:" + mutexCommand, "1:success");

						((TextSingleField) data.getField("Domain name")).setFieldValue(VHost);

						data.setAttribute("type", "submit");

						adminJaxmpp.getModule(AdHocCommansModule.class).execute(
								JID.jidInstance("vhost-man", adminJID.getDomain()), addVHostCommand, null, data,
								new AdHocCommansModule.AdHocCommansAsyncCallback() {

									@Override
									public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											throws JaxmppException {
										mutex.notify("2:" + mutexCommand, "2:error");
									}

									@Override
									protected void onResponseReceived(String sessionid, String node, State status,
											JabberDataElement data) throws JaxmppException {
										FixedField nff = data.getField("Note");
										if (nff != null) {
											mutex.notify(mutexCommand + ":success");
										}
										mutex.notify("2:" + mutexCommand);

									}

									@Override
									public void onTimeout() throws JaxmppException {
										mutex.notify("2:" + mutexCommand, "2:timeout");
									}
								});
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("1:" + mutexCommand, "1:timeout");

					}
				});
		mutex.waitFor(10 * 1000, "1:" + mutexCommand, "2:" + mutexCommand);
		assertTrue(mutex.isItemNotified(addVHostCommand + "-" + VHost + ":success"), "VHost adding failed.");

		return VHost;
	}

	protected final void changePresenceAndWait(final Jaxmpp from, final Jaxmpp to, final Presence.Show p) throws Exception {
		final Mutex mutex = new Mutex();
		final PresenceModule.ContactChangedPresenceHandler handler = new PresenceModule.ContactChangedPresenceHandler() {

			@Override
			public void onContactChangedPresence(SessionObject sessionObject, Presence stanza, JID jid, Show show,
					String status, Integer priority) throws JaxmppException {
				try {
					boolean v = jid.equals(ResourceBinderModule.getBindedJID(from.getSessionObject())) && show.equals(p);
					if (v) {
						log("Received presence change.");
						mutex.notify("presence");
						synchronized (mutex) {
							mutex.notify();
						}
					}
				} catch (Exception e) {
					fail(e);
				}
			}

		};

		try {
			to.getEventBus().addHandler(PresenceModule.ContactChangedPresenceHandler.ContactChangedPresenceEvent.class, handler);
			from.getModule(PresenceModule.class).setPresence(p, null, 1);

			mutex.waitFor(1000 * 10, "presence");

			assertTrue("Exchanging presence", mutex.isItemNotified("presence"));
		} finally {
			to.getEventBus().remove(PresenceModule.ContactChangedPresenceHandler.ContactChangedPresenceEvent.class, handler);
		}
	}

	public Jaxmpp createJaxmpp(final String logPrefix) {
		try {
			Jaxmpp jaxmpp1 = new Jaxmpp();
			jaxmpp1.getSessionObject().setProperty(SocketConnector.TRUST_MANAGERS_KEY, dummyTrustManagers);
			if (logPrefix != null)
				jaxmpp1.getSessionObject().setUserProperty(LOG_PREFIX_KEY, logPrefix);
			jaxmpp1.getSessionObject().setUserProperty(SocketConnector.COMPRESSION_DISABLED_KEY, Boolean.TRUE);
			jaxmpp1.getEventBus().addListener(connectorListener);

			jaxmpp1.getModulesManager().register(new InBandRegistrationModule());
			jaxmpp1.getModulesManager().register(new MessageModule());
			jaxmpp1.getModulesManager().register(new MessageCarbonsModule());
			jaxmpp1.getModulesManager().register(new MucModule());
			jaxmpp1.getModulesManager().register(new AdHocCommansModule());
			jaxmpp1.getModulesManager().register(new RosterModule());
			jaxmpp1.getModulesManager().register(new MessageArchivingModule());
			jaxmpp1.getModulesManager().register(new PubSubModule());

			tigase.jaxmpp.j2se.Presence.initialize(jaxmpp1);

			return jaxmpp1;
		} catch (JaxmppException e) {
			fail(e);
			throw new RuntimeException(e);
		}
	}

	public Jaxmpp createJaxmpp(String logPrefix, BareJID user1JID) {
		return createJaxmpp(logPrefix, user1JID, null, user1JID.getLocalpart());
	}

	public Jaxmpp createJaxmpp(String logPrefix, BareJID user1JID, String domain, String password) {
		return createJaxmpp(logPrefix, user1JID, domain, null, password);
	}

	public Jaxmpp createJaxmpp(String logPrefix, BareJID user1JID, String domain, String host, String password) {
		Jaxmpp jaxmpp1 = createJaxmpp(logPrefix);
		jaxmpp1.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);

		if (null == host) {
			String instanceHostname = getInstanceHostname();
			if (instanceHostname != null) {
				jaxmpp1.getConnectionConfiguration().setServer(instanceHostname);
			}
		} else {
			jaxmpp1.getConnectionConfiguration().setServer(host);
		}

		if (user1JID != null)
			jaxmpp1.getConnectionConfiguration().setUserJID(user1JID);
		if (password != null)
			jaxmpp1.getConnectionConfiguration().setUserPassword(password);
		if (domain != null)
			jaxmpp1.getConnectionConfiguration().setDomain(domain);

		jaxmpp1.getSessionObject().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.TRUE);

		return jaxmpp1;
	}

	public Jaxmpp createJaxmppAdmin() {
		return createJaxmppAdmin(null);
	}

	public Jaxmpp createJaxmppAdmin(String hostname) {
		Jaxmpp adminJaxmpp = null;
		String user = props.getProperty("test.admin.username");
		String pass = props.getProperty("test.admin.password");
		String domain = (String) props.getOrDefault("test.admin.domain", getDomain(0));
		BareJID jidInstance;
		if (null == user) {
			user = "admin";
		}
		if (null == pass) {
			pass = user;
		}
		jidInstance = BareJID.bareJIDInstance(user, domain);
		if (null != jidInstance) {
			adminJaxmpp = createJaxmpp("admin", jidInstance, null, hostname, pass);
		}
		return adminJaxmpp;
	}

	public BareJID createUserAccount(String logPrefix) throws JaxmppException, InterruptedException {
		return createUserAccount(logPrefix, 0);
	}

	public BareJID createUserAccount(String logPrefix, int domainNumber) throws JaxmppException, InterruptedException {
		final String domain = getDomain(domainNumber);
		return createUserAccount(logPrefix, logPrefix + "_" + nextRnd(), domain);
	}

	public BareJID createUserAccount(String logPrefix, final String username, final String domain) throws JaxmppException,
			InterruptedException {
		return createUserAccount(logPrefix, username, domain, username + "@wp.pl");
	}

	public BareJID createUserAccount(String logPrefix, final String username, final String domain, final String email)
			throws JaxmppException, InterruptedException {
		return createUserAccount(logPrefix, username, username, domain, username + "@wp.pl");
	}

	public BareJID createUserAccount(String logPrefix, final String username, final String password,
																																						final String domain, final String email)
			throws JaxmppException, InterruptedException {
		final String server = getInstanceHostname();

		final Jaxmpp jaxmpp1 = createJaxmpp(logPrefix);
		jaxmpp1.getEventBus().addListener(new EventListener() {

			@Override
			public void onEvent(Event<? extends EventHandler> event) {
				log(event != null ? event.toString() : "null event!");
			}
		});
		jaxmpp1.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		if (server != null)
			jaxmpp1.getConnectionConfiguration().setServer(server);
		jaxmpp1.getConnectionConfiguration().setDomain(domain);
		jaxmpp1.getSessionObject().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.TRUE);
		jaxmpp1.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);

		final Mutex mutex = new Mutex();
		jaxmpp1.getEventBus().addHandler(
				InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
				new InBandRegistrationModule.ReceivedRequestedFieldsHandler() {

					@Override
					public void onReceivedRequestedFields(SessionObject sessionObject, IQ responseStanza) {

						try {
							jaxmpp1.getModule(InBandRegistrationModule.class).register(username, password, email,
									new AsyncCallback() {

										@Override
										public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
											mutex.notify("registration");
											log("Account registration error: " + error);
											Assert.fail("Account registration error: " + error);
										}

										@Override
										public void onSuccess(Stanza responseStanza) throws JaxmppException {
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
							fail(e);
						}

					}

				});

		jaxmpp1.login(false);
		mutex.waitFor(1000 * 30, "registration");

		assertTrue("Registration failed!", mutex.isItemNotified("registrationSuccess"));
		jaxmpp1.disconnect();

		createdAccounts.add(BareJID.bareJIDInstance(username, domain));

		return BareJID.bareJIDInstance(username, domain);
	}

	protected void fail(Exception e) {
		e.printStackTrace();
		Assert.fail(e.getMessage());
	}

	public String getHttpPort() {
		return props.getProperty("test.http.port");
	}

	public String getApiKey() {
		return props.getProperty("test.http.api-key");
	}

	/**
	 * Return randomly chosen domain from list of available domains in
	 * "server.domains" property
	 *
	 * @return domain name of random index
	 */
	public String getDomain() {
		return getDomain(-1);
	}

	/**
	 * Return domain of particular index from list of available domains in
	 * "server.domains" property
	 *
	 * @param i
	 *            index of the domain from "server.domains" property
	 *
	 * @return domain name of particular index, if index is missing then domain
	 *         will be selected randomly
	 */
	public String getDomain(int i) {
		final String domainsProp = props.getProperty("server.domains");
		String[] domains = null;
		String domain = null;
		if (null != domainsProp) {
			domains = domainsProp.split(",");
		}
		if (domains != null && domains.length > 0) {
			if (i < 0 || i >= domains.length) {
				Random r = new Random();
				int nextInt = r.nextInt(domains.length);
				domain = domains[nextInt];
			} else {
				domain = domains[i];
			}
		}
		if (null == domain) {
			throw new RuntimeException("Missing of wrong configuration of server.domains - at least one entry needed");
		}
		return domain;
	}

	/**
	 * Return randomly chosen domain from list of available domains in
	 * "server.cluster.nodes" property
	 *
	 * @return random domain name
	 */
	public String[] getInstanceHostnames() {
		final String hostnamesProp = props.getProperty("server.cluster.nodes");
		String[] hostnames = null;
		String hostname = null;
		if (null != hostnamesProp) {
			hostnames = hostnamesProp.split(",");
		}
		return hostnames;
	}

	/**
	 * Return randomly chosen domain from list of available domains in
	 * "server.cluster.nodes" property
	 *
	 * @return random domain name
	 */
	public String getInstanceHostname() {
		final String hostnamesProp = props.getProperty("server.cluster.nodes");
		String[] hostnames = null;
		String hostname = null;
		if (null != hostnamesProp) {
			hostnames = hostnamesProp.split(",");
		}
		if (hostnames != null && hostnames.length > 0) {
			Random r = new Random();
			int nextInt = r.nextInt(hostnames.length);
			hostname = hostnames[nextInt];
		}
		if (null == hostname) {
			throw new RuntimeException("Missing of wrong configuration of server.cluster.nodes - at least one entry needed");
		}
		return hostname;
	}

	public String getWebSocketURI() {
		String port = props.getProperty("test.ws.port");
		String hostname = getInstanceHostname();
		return "ws://" + hostname + ":" + port + "/";
	}

	public String getBoshURI() {
		String port = props.getProperty("test.bosh.port");
		String hostname = getInstanceHostname();
		return "http://" + hostname + ":" + port + "/";
	}

	public void removeUserAccount(final BareJID userJID) throws JaxmppException, InterruptedException {
		final Jaxmpp jaxmpp2 = createJaxmpp(null);
		jaxmpp2.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);

		final String server = getInstanceHostname();
		if (server != null) {
			jaxmpp2.getConnectionConfiguration().setServer(server);
		}
		jaxmpp2.getConnectionConfiguration().setUserJID(userJID);
		jaxmpp2.getConnectionConfiguration().setUserPassword(userJID.getLocalpart());
		jaxmpp2.getSessionObject().setUserProperty(SocketConnector.TLS_DISABLED_KEY, Boolean.TRUE);

		jaxmpp2.login(true);

		removeUserAccount(jaxmpp2);
	}

	public void removeUserAccount(Jaxmpp jaxmpp) throws JaxmppException, InterruptedException {
		final BareJID userJid = jaxmpp.getSessionObject().getUserBareJid();
		final Mutex mutex = new Mutex();

		final JID jid1 = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());
		if (jid1 == null && !jaxmpp.isConnected())
			jaxmpp.login(true);
		final JID jid = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject());

		if (jid.getLocalpart().equals("admin"))
			throw new RuntimeException("Better not to remove user 'admin', don't you think?");

		final JaxmppCore.LoggedOutHandler disconnectionHandler = new JaxmppCore.LoggedOutHandler() {

			@Override
			public void onLoggedOut(SessionObject sessionObject) {
				log("Disconnected! " + userJid);
				mutex.notifyForce();
			}

		};

		try {
			jaxmpp.getEventBus().addHandler(JaxmppCore.LoggedOutHandler.LoggedOutEvent.class, disconnectionHandler);

			jaxmpp.getModule(InBandRegistrationModule.class).removeAccount(new AsyncCallback() {

				@Override
				public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
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

		} finally {
			jaxmpp.getEventBus().remove(JaxmppCore.LoggedOutHandler.LoggedOutEvent.class, disconnectionHandler);
		}
	}

	protected void removeVhost(final Jaxmpp adminJaxmpp, final String VHost) throws JaxmppException, InterruptedException {
		final String removeVHostCommand = "comp-repo-item-remove";

		final Mutex mutex = new Mutex();

		final BareJID userBareJid = adminJaxmpp.getSessionObject().getUserBareJid();
		adminJaxmpp.getModule(AdHocCommansModule.class).execute(JID.jidInstance("vhost-man", userBareJid.getDomain()),
				removeVHostCommand, null, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
					}

					@Override
					protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
							throws JaxmppException {

						ListSingleField ff = ((ListSingleField) data.getField("item-list"));

						ff.clearOptions();
						ff.setFieldValue(VHost);

						JabberDataElement r = new JabberDataElement(data.createSubmitableElement(XDataType.submit));
						adminJaxmpp.getModule(AdHocCommansModule.class).execute(
								JID.jidInstance("vhost-man", userBareJid.getDomain()), removeVHostCommand, null, r,
								new AdHocCommansModule.AdHocCommansAsyncCallback() {

									@Override
									public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											throws JaxmppException {
									}

									@Override
									protected void onResponseReceived(String sessionid, String node, State status,
											JabberDataElement data) throws JaxmppException {
										FixedField nff = data.getField("Note");
										if (nff != null) {
											mutex.notify("remove:" + VHost + ":" + nff.getFieldValue());
										}
										mutex.notify("domainRemoved:" + VHost);

									}

									@Override
									public void onTimeout() throws JaxmppException {
									}
								});
					}

					@Override
					public void onTimeout() throws JaxmppException {
					}
				});
		mutex.waitFor(10 * 1000, "domainRemoved:" + VHost);

		assertTrue(mutex.isItemNotified("remove:" + VHost + ":Operation successful"), "VHost removal failed.");
	}

	protected final void sendAndFail(Jaxmpp from, Jaxmpp to) throws Exception {
		String rnd = nextRnd();
		final Mutex mutex = new Mutex();
		final String uid = nextRnd();
		final Message[] result = new Message[] { null };
		final MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify(stanza.getId() + ":" + stanza.getErrorCondition());
				} catch (Exception e) {
					fail(e);
				}
			}
		};

		from.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
		try {
			final Message msg1 = Message.create();
			msg1.setTo(ResourceBinderModule.getBindedJID(to.getSessionObject()));
			msg1.setBody(rnd);
			msg1.setType(StanzaType.chat);
			msg1.setId(rnd);

			from.send(msg1);

			mutex.waitFor(5000, msg1.getId() + ":" + ErrorCondition.forbidden);

		} finally {
			from.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
		}
		assertTrue("Message wasn't blocked", mutex.isItemNotified(rnd + ":" + ErrorCondition.forbidden));
	}

	protected final void sendAndWait(Jaxmpp j, final IQ iq, final AsyncCallback asyncCallback) throws Exception {
		final Mutex mutex = new Mutex();
		iq.setId(nextRnd());
		j.send(iq, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
				try {
					asyncCallback.onError(responseStanza, error);
					mutex.notify("error", "iq:" + responseStanza.getId());
				} catch (Exception e) {
					fail(e);
				}
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				try {
					asyncCallback.onSuccess(responseStanza);
					mutex.notify("success", "iq:" + responseStanza.getId());
				} catch (Exception e) {
					fail(e);
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				try {
					asyncCallback.onTimeout();
					mutex.notify("timeout", "iq:" + iq.getId());

				} catch (Exception e) {
					fail(e);
				}
			}
		});

		mutex.waitFor(1000 * 30, "iq:" + iq.getId());
	}

	protected final Message sendAndWait(Jaxmpp from, Jaxmpp to, final Message message) throws Exception {
		message.setTo(ResourceBinderModule.getBindedJID(to.getSessionObject()));

		if (message.getId() == null)
			message.setId(nextRnd());

		final Mutex mutex = new Mutex();
		final Message[] result = new Message[1];
		final MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				log("!!!!!?>" + stanza);
				try {
					result[0] = stanza;
					mutex.notify("msg:" + stanza.getId());
				} catch (Exception e) {
					fail(e);
				}

			}

		};
		final MessageModule.MessageReceivedHandler errorHandler = new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("msg:" + message.getId());
				} catch (Exception e) {
					fail(e);
				}
			}
		};

		try {
			to.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			from.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, errorHandler);
			from.send(message);

			mutex.waitFor(60 * 1000, "msg:" + message.getId());

			return result[0];
		} finally {
			to.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			from.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, errorHandler);
		}
	}

	protected final Message sendAndWait(Jaxmpp from, Jaxmpp to, final String message) throws Exception {
		final Mutex mutex = new Mutex();
		final String uid = nextRnd();
		final Message[] result = new Message[] { null };
		final MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					result[0] = stanza;
					mutex.notify("msg:" + uid);
				} catch (Exception e) {
					fail(e);
				}
			}
		};
		final MessageModule.MessageReceivedHandler errorHandler = new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					mutex.notify("msg:" + uid);
				} catch (Exception e) {
					fail(e);
				}
			}
		};

		to.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
		from.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, errorHandler);
		try {
			final Message msg1 = Message.create();
			msg1.setTo(ResourceBinderModule.getBindedJID(to.getSessionObject()));
			msg1.setBody(message);
			msg1.setType(StanzaType.chat);
			msg1.setId(uid);

			from.send(msg1);

			mutex.waitFor(5 * 1000, "msg:" + uid);

			return result[0];
		} finally {
			to.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			from.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, errorHandler);
		}
	}

	
	protected void setLoggerLevel( Level lvl, boolean connectorLogsEnabled ) throws SecurityException {
		this.connectorLogsEnabled = connectorLogsEnabled;

		Logger log = Logger.getLogger( "tigase.jaxmpp" );

		if ( ngLogger == null ){
			ngLogger = new Handler() {

				@Override
				public void close() throws SecurityException {
				}

				@Override
				public void flush() {
				}

				@Override
				public void publish( LogRecord record ) {
					log( record.getSourceClassName() + "." + record.getSourceMethodName() + ": " + record.getMessage() );
				}
			};

			log.addHandler( ngLogger );
		}

		ngLogger.setLevel( lvl );
		log.setUseParentHandlers( false );
		log.setLevel( lvl );
	}

	@BeforeSuite
	@BeforeClass(alwaysRun = true)
	@BeforeGroups
	@BeforeMethod
	protected void setUp() throws Exception {
		loadProperties();
		setLoggerLevel( Level.ALL, connectorLogsEnabled );
	}

	private String getProperty(String key) throws IOException {
		if ( this.props == null ){
			loadProperties();
		}
		return this.props.getProperty( key );
	}
	
	private void loadProperties() throws IOException {
		if ( this.props == null ){
			InputStream stream = getClass().getResourceAsStream( "/server.properties" );
			this.props = new Properties();
			this.props.load( stream );
			stream.close();
		}
	}

	protected final void testSendAndWait(Jaxmpp from, Jaxmpp to) throws Exception {
		String rnd = nextRnd();
		Message x = sendAndWait(from, to, rnd);
		assertFalse("Sending message", x == null || x.getBody() == null || !x.getBody().equals(rnd));
	}

	protected final void testSubscribeAndWait(final Jaxmpp from, final Jaxmpp to) throws Exception {
		final Mutex mutex = new Mutex();
		final PresenceModule.SubscribeRequestHandler listener = new PresenceModule.SubscribeRequestHandler() {

			@Override
			public void onSubscribeRequest(SessionObject sessionObject, Presence stanza, BareJID jid) {
				try {
					boolean v = jid.equals(ResourceBinderModule.getBindedJID(from.getSessionObject()).getBareJid());
					if (v) {
						log("Received subscription request: " + jid);
						to.getModule(PresenceModule.class).subscribed(JID.jidInstance(jid));
						mutex.notify("presenceReceived");
					}
				} catch (Exception e) {
					fail(e);
				}

			}

		};

		try {
			to.getEventBus().addHandler(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, listener);
			from.getModule(PresenceModule.class).subscribe(ResourceBinderModule.getBindedJID(to.getSessionObject()));

			mutex.waitFor(1000 * 10, "presenceReceived");

			assertTrue("Subscribing", mutex.isItemNotified("presenceReceived"));

		} finally {
			to.getEventBus().remove(PresenceModule.SubscribeRequestHandler.SubscribeRequestEvent.class, listener);
		}
	}

	@BeforeSuite
	public void registerAdmin() throws JaxmppException, InterruptedException, IOException, Exception {

		Boolean register = false;
		String registerProp = getProperty( "test.admin.register" );
		if ( registerProp != null ){
			register = Boolean.valueOf( registerProp.toLowerCase() );
		}

		Jaxmpp adminJaxmpp;
		BareJID userAccount;
		String username = getProperty( "test.admin.username" );
		String password = getProperty( "test.admin.password" );
		String domain = getDomain( 0 );
		if ( register ){
			createUserAccount( username, username, password, domain, username + "@" + domain );
			userAccount = BareJID.bareJIDInstance( username, domain );
			adminJaxmpp = createJaxmpp( username, userAccount, userAccount.getDomain(), password );
			adminJaxmpp.login( true );

			assertTrue( adminJaxmpp.isConnected(), "contact was not connected" );
			if ( adminJaxmpp.isConnected() ){
				adminJaxmpp.disconnect();
			}
		}
	}

}
