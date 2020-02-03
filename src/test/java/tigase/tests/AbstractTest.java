/*
 * AbstractTest.java
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
package tigase.tests;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.ISuite;
import org.testng.ITestContext;
import org.testng.annotations.*;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.Connector.StanzaSendingHandler.StanzaSendingEvent;
import tigase.jaxmpp.core.client.XMPPException.ErrorCondition;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence.Show;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.utils.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.testng.AssertJUnit.*;
import static tigase.TestLogger.log;

public abstract class AbstractTest {

	public static final String LOG_PREFIX_KEY = "LOG_PREFIX";
	private final static Random randomGenerator = new SecureRandom();
	protected static Properties props;
	private static Account adminAccount;
	private static int counter = 0;
	private static Handler ngLogger = null;
	public final ThreadLocal<Class> CURRENT_CLASS = new ThreadLocal<>();
	public final ThreadLocal<Method> CURRENT_METHOD = new ThreadLocal<>();
	public final ThreadLocal<ISuite> CURRENT_SUITE = new ThreadLocal<>();
	public final AccountsManager accountManager = new AccountsManager(this);
	public final ApiKeyManager apiKeyManager = new ApiKeyManager(this);
	public final PubSubManager pubSubManager = new PubSubManager(this);
	public final VHostManager vHostManager = new VHostManager(this);
	protected boolean connectorLogsEnabled = true;
	public final EventListener connectorListener = new EventListener() {

		@Override
		public void onEvent(Event<? extends EventHandler> event) {
			if (!connectorLogsEnabled) {
				return;
			}
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
	private Jaxmpp jaxmppAdmin;

	public static void fail(Exception e) {
		e.printStackTrace();
		Assert.fail(e.getMessage());
	}

	public static final String nextRnd() {
		int r = randomGenerator.nextInt() & 0x7fffffff;
		return Integer.toString(r, 36) + String.format("%04d", ++counter);
	}

	public Account getAdminAccount() {
		return adminAccount;
	}

	public Jaxmpp getJaxmppAdmin() {
		return jaxmppAdmin;
	}

	public AccountBuilder createAccount() {
		return new AccountBuilder(this);
	}

	public ApiKeyBuilder createRestApiKey() {
		return new ApiKeyBuilder(apiKeyManager, UUID.randomUUID().toString());
	}

	public VHostBuilder createVHost(String domain) {
		return new VHostBuilder(vHostManager, domain);
	}

	public String getHttpPort() {
		return props.getProperty("test.http.port");
	}

	/**
	 * Return randomly chosen domain from list of available domains in "server.domains" property
	 *
	 * @return domain name of random index
	 */
	public String getDomain() {
		return getDomain(-1);
	}

	/**
	 * Return domain of particular index from list of available domains in "server.domains" property
	 *
	 * @param i index of the domain from "server.domains" property
	 *
	 * @return domain name of particular index, if index is missing then domain will be selected randomly
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

	public String[] getDomains() {
		final String domainsProp = props.getProperty("server.domains");
		String[] domains = null;
		if (null != domainsProp) {
			domains = domainsProp.split(",");
		}
		return domains;
	}

	/**
	 * Return randomly chosen domain from list of available domains in "server.cluster.nodes" property
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
	 * Return randomly chosen domain from list of available domains in "server.cluster.nodes" property
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
			throw new RuntimeException(
					"Missing of wrong configuration of server.cluster.nodes - at least one entry needed");
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

	private static GreenMail greenMail;
	private static final AtomicInteger mailServerCounter = new AtomicInteger(0);

	static {
		greenMail = new GreenMail(ServerSetupTest.ALL);
	}

	private static EmailAccount globalServerSenderAccount = null;

	public static EmailAccount getGlobalServerSenderAccount() {
		return globalServerSenderAccount;
	}

	private static EmailAccount globalServerReceiverAccount = null;

	public static EmailAccount getGlobalServerReceiverAccount() {
		return globalServerReceiverAccount;
	}

	private static synchronized void setUpMailServer() {
		if ("true".equals(props.getProperty("mail.useExternalServer"))) {
			return;
		}
		if (mailServerCounter.getAndIncrement() == 0) {
			greenMail.start();
			// Preparing sender account
			String email = props.getProperty("smtp.email");
			String username = props.getProperty("smtp.username");
			String password = props.getProperty("smtp.password");
			String server = props.getProperty("smtp.server");
			if (email != null && username != null && password != null && server != null) {
				globalServerSenderAccount = createEmailAccount(email, username, server, password);
			}

			// Preparing main receiver account
			email = props.getProperty("imap.email");
			username = props.getProperty("imap.username");
			password = props.getProperty("imap.password");
			server = props.getProperty("imap.server");
			if (email != null && username != null && password != null && server != null) {
				globalServerReceiverAccount = createEmailAccount(email, username, server, password);
			}
		}
	}

	private static synchronized void tearDownMailServer() {
		if ("true".equals(props.getProperty("mail.useExternalServer"))) {
			return;
		}
		if (mailServerCounter.decrementAndGet() == 0) {
			greenMail.stop();
			userToEmailMap.clear();
		}
	}

	private static EmailAccount createEmailAccount(String email, String username, String server, String password) {
		EmailAccount emailAccount = new EmailAccount(email, username, server, greenMail.getImaps().getPort(),
													 greenMail.getSmtp().getPort(), password);
		greenMail.setUser(email, username, password);
		userToEmailMap.put(email, emailAccount);
		return emailAccount;
	}

	private static final Map<String, EmailAccount> userToEmailMap = new ConcurrentHashMap<>();

	public static class EmailAccount {

		public final String email;
		public final String username;
		public final String server;
		public final int imapsServerPort;
		public final int smtpServerPort;
		public final String password;

		private EmailAccount(String email, String username, String server, int imapsServerPort, int smtpServerPort,
							 String password) {
			this.email = email;
			this.username = username;
			this.server = server;
			this.smtpServerPort = smtpServerPort;
			this.imapsServerPort = imapsServerPort;
			this.password = password;
		}
	}

	public EmailAccount getEmailAccountForUser(String username) {
		if ("true".equals(props.getProperty("mail.useExternalServer"))) {
			String address = props.getProperty("imap.username");
			if (address != null) {
				String localpart = address;
				String domain = props.getProperty("imap.server");
				if (address.contains("@")) {
					String[] parts = address.split("@");
					localpart = parts[0];
					domain = parts[1];
				}

				return new EmailAccount(
						username == null ? (localpart + "@" + domain) : (localpart + "+" + username + "@" + domain),
						address, domain, -1, -1, (String) props.get("imap.password"));
			}
			return null;
		} else {
			return userToEmailMap.computeIfAbsent(username, key -> {
				String domain = Optional.ofNullable(props.getProperty("imap.server")).orElse("localhost");
				String email = UUID.randomUUID().toString() + "@" + domain;
				EmailAccount emailAccount = new EmailAccount(email, key, domain, greenMail.getImaps().getPort(),
															 greenMail.getSmtp().getPort(),
															 UUID.randomUUID().toString());
				greenMail.setUser(email, key, emailAccount.password);
				return emailAccount;
			});
		}
	}

	public void removeUserAccount(Jaxmpp jaxmpp) throws JaxmppException, InterruptedException {
		accountManager.unregisterAccount(jaxmpp);
	}

	public File getServerConfigBaseDir() {
		String val = System.getProperty("CONFIG_BASE_DIR");
		if (val == null) {
			val = System.getenv("CONFIG_BASE_DIR");
			if (val == null) {
				val = new File("src/test/resources/server").getAbsolutePath();
			}
		}
		return new File(val);
	}

	protected String addVhost(final Jaxmpp adminJaxmpp, final String prefix)
			throws JaxmppException, InterruptedException {
		return vHostManager.addVHost(prefix);
	}

	protected final void changePresenceAndWait(final Jaxmpp from, final Jaxmpp to, final Presence.Show p)
			throws Exception {
		final Mutex mutex = new Mutex();
		final PresenceModule.ContactChangedPresenceHandler handler = new PresenceModule.ContactChangedPresenceHandler() {

			@Override
			public void onContactChangedPresence(SessionObject sessionObject, Presence stanza, JID jid, Show show,
												 String status, Integer priority) throws JaxmppException {
				try {
					boolean v =
							jid.equals(ResourceBinderModule.getBindedJID(from.getSessionObject())) && show.equals(p);
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
			to.getEventBus()
					.addHandler(PresenceModule.ContactChangedPresenceHandler.ContactChangedPresenceEvent.class,
								handler);
			from.getModule(PresenceModule.class).setPresence(p, null, 1);

			mutex.waitFor(1000 * 10, "presence");

			assertTrue("Exchanging presence", mutex.isItemNotified("presence"));
		} finally {
			to.getEventBus()
					.remove(PresenceModule.ContactChangedPresenceHandler.ContactChangedPresenceEvent.class, handler);
		}
	}

	protected void ensureConnected(Jaxmpp jaxmpp) throws JaxmppException {
		if (jaxmpp.isConnected()) {
			return;
		} else {
			jaxmpp.login(true);
			assertTrue(jaxmpp.isConnected());
		}
	}

	protected void ensureDisconnected(Jaxmpp jaxmpp) throws JaxmppException {
		if (jaxmpp.isConnected()) {
			jaxmpp.disconnect(true);
			assertFalse(jaxmpp.isConnected());
		}
	}

	protected void ensureAdminAccountExists() {
		String user = props.getProperty("test.admin.username");
		String pass = props.getProperty("test.admin.password");
		String domain = (String) props.getOrDefault("test.admin.domain", getDomain(0));
		if (null == user) {
			user = "admin";
		}
		if (null == pass) {
			pass = user;
		}
		try {
			AccountBuilder builder = createAccount().setLogPrefix("admin")
					.setUsername(user)
					.setPassword(pass)
					.setDomain(domain)
					.setRegister(false);
			Account adminAccount = builder.build();
			try {
				Jaxmpp jaxmpp = adminAccount.createJaxmpp().setConnected(true).build();
				if (jaxmpp.isConnected()) {
					try {
						jaxmpp.disconnect(true);
					} catch (Throwable ex) {
						// we do not care! we confirmed that admin account exists!
					}
					this.adminAccount = adminAccount;
				}
				return;
			} catch (JaxmppException ex) {
				log("Could not connect with admin account credentials (" + ex.getMessage() +
							"), trying to register account...");
			}

			this.adminAccount = builder.setRegister(true).build();
		} catch (JaxmppException | InterruptedException e) {
			fail(e);
		}
	}

	protected void removeVhost(final Jaxmpp adminJaxmpp, final String VHost)
			throws JaxmppException, InterruptedException {
		vHostManager.removeVHost(VHost);
	}

	protected final void sendAndFail(Jaxmpp from, Jaxmpp to) throws Exception {
		String rnd = nextRnd();
		final Mutex mutex = new Mutex();
		final String uid = nextRnd();
		final Message[] result = new Message[]{null};
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
		assertTrue("Message wasn't blocked: " + rnd, mutex.isItemNotified(rnd + ":" + ErrorCondition.forbidden));
	}

	public abstract static class Response {

		public IQ getRequest() {
			return request;
		}

		private final IQ request;

		protected Response(IQ request, IQ response) {
			this.request = request;
			this.response = response;
		}

		public IQ getResponse() {
			return response;
		}

		private final IQ response;

		public final static class Success
				extends Response {

			public Success(IQ request, IQ response) {
				super(request, response);
			}
		}

		public static class Error
				extends Response {

			public ErrorCondition getError() {
				return error;
			}

			private final ErrorCondition error;

			protected Error(IQ request, IQ response, ErrorCondition error) {
				super(request, response);
				this.error = error;
			}
		}
	}

	protected final Response sendRequest(final Jaxmpp j, final IQ iq) throws JaxmppException, InterruptedException {
		final MutableObject<Response> result = new MutableObject<>(null);
		final Mutex mutex = new Mutex();
		if (iq.getId() == null) {
			iq.setId(nextRnd());
		}
		j.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
				result.setValue(new Response.Error(iq, (IQ) responseStanza, error));
				mutex.notify("error", "iq:" + responseStanza.getId());
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				result.setValue(new Response.Success(iq, (IQ) responseStanza));
				mutex.notify("success", "iq:" + responseStanza.getId());
			}

			@Override
			public void onTimeout() throws JaxmppException {
				result.setValue(new Response.Error(iq, null, ErrorCondition.remote_server_timeout));
				mutex.notify("timeout", "iq:" + iq.getId());
			}
		});
		mutex.waitFor(1000 * 30, "iq:" + iq.getId());

		assertNotNull("There must be any Result!",result.getValue());

		return result.getValue();
	}

	protected final boolean sendAndWait(Jaxmpp j, final IQ iq, final AsyncCallback asyncCallback) throws Exception {
		final Mutex mutex = new Mutex();
		iq.setId(nextRnd());
		j.send(iq, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, ErrorCondition error) throws JaxmppException {
				try {
					asyncCallback.onError(responseStanza, error);
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

		return mutex.isItemNotified("success");
	}

	protected final Message sendAndWait(Jaxmpp from, Jaxmpp to, final Message message) throws Exception {
		message.setTo(ResourceBinderModule.getBindedJID(to.getSessionObject()));

		if (message.getId() == null) {
			message.setId(nextRnd());
		}

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
			from.getEventBus()
					.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, errorHandler);
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
		final Message[] result = new Message[]{null};
		final MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {

			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					if (!uid.equals(stanza.getId())) {
						return;
					}
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
					mutex.notify("msg:" + stanza.getId());
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

			if (mutex.isItemNotified("msg:" + uid)) {
				return result[0];
			} else {
				return null;
			}
		} finally {
			to.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
			from.getEventBus().remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, errorHandler);
		}
	}

	protected void setLoggerLevel(Level lvl, boolean connectorLogsEnabled) throws SecurityException {
		this.connectorLogsEnabled = connectorLogsEnabled;

		Logger log = Logger.getLogger("tigase.jaxmpp");

		if (ngLogger == null) {
			ngLogger = new Handler() {

				@Override
				public void close() throws SecurityException {
				}

				@Override
				public void flush() {
				}

				@Override
				public void publish(LogRecord record) {
					log(record.getSourceClassName() + "." + record.getSourceMethodName() + ": " + record.getMessage());
				}
			};

			log.addHandler(ngLogger);
		}

		ngLogger.setLevel(lvl);
		log.setUseParentHandlers(false);
		log.setLevel(lvl);
	}

	@BeforeSuite
	protected void setupSuite(ITestContext context) throws Exception {
		CURRENT_SUITE.set(context.getSuite());
		System.out.println("setting up suite " + context.getSuite().getName());
		loadProperties();
		setLoggerLevel(Level.FINER, connectorLogsEnabled);
		setUpMailServer();
		ensureAdminAccountExists();
	}

	@AfterSuite
	protected void tearDownSuite(ITestContext context) {
		pubSubManager.scopeFinished();
		vHostManager.scopeFinished();
		apiKeyManager.scopeFinished();
		accountManager.scopeFinished();
		tearDownMailServer();
		CURRENT_SUITE.remove();
	}

	@BeforeClass
	protected void setupClass(ITestContext context) throws JaxmppException {
		CURRENT_CLASS.set(this.getClass());
		System.out.println("setting up for class " + this.getClass().getCanonicalName() + " = " + this.toString());
		if (jaxmppAdmin == null || !jaxmppAdmin.isConnected()) {
			jaxmppAdmin = getAdminAccount().createJaxmpp().setConnected(true).build();
		}
	}

	@AfterClass
	protected void tearDownClass(ITestContext context) {
		System.out.println("tearing down class " + this.getClass().getCanonicalName() + " = " + this.toString());
		pubSubManager.scopeFinished();
		apiKeyManager.scopeFinished();
		accountManager.scopeFinished();
		vHostManager.scopeFinished();
		CURRENT_CLASS.remove();
	}

	@BeforeMethod
	protected void setupMethod(Method method, ITestContext context) throws JaxmppException {
		if (jaxmppAdmin == null || !jaxmppAdmin.isConnected()) {
			jaxmppAdmin = getAdminAccount().createJaxmpp().setConnected(true).build();
		}
		CURRENT_METHOD.set(method);
		System.out.println(
				"setting up for method " + method.getDeclaringClass().getCanonicalName() + "." + method.getName() +
						"()");
	}

	@AfterMethod
	protected void tearDownMethod(Method method, ITestContext context) {
		System.out.println(
				"tearing down method " + method.getDeclaringClass().getCanonicalName() + "." + method.getName() + "()");
		pubSubManager.scopeFinished();
		apiKeyManager.scopeFinished();
		accountManager.scopeFinished();
		vHostManager.scopeFinished();
		CURRENT_METHOD.remove();
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

	private String getProperty(String key) throws IOException {
		if (props == null) {
			loadProperties();
		}
		return props.getProperty(key);
	}

	private void loadProperties() throws IOException {
		if (props == null) {
			InputStream stream = getClass().getResourceAsStream("/server.properties");
			props = new Properties();
			props.load(stream);
			stream.close();

			final Properties sysProps = System.getProperties();
			sysProps.stringPropertyNames()
					.stream()
					.filter(e -> e.matches("(?u)^(imap|test|server)[.].*"))
					.forEach(p -> {
						log("adding system property: " + p + ": " + sysProps.getProperty(p));
						props.setProperty(p, sysProps.getProperty(p));
					});

		}
	}

//	@BeforeSuite
//	public void registerAdmin() throws JaxmppException, InterruptedException, IOException, Exception {
//
//		Boolean register = false;
//		String registerProp = getProperty( "test.admin.register" );
//		if ( registerProp != null ){
//			register = Boolean.valueOf( registerProp.toLowerCase() );
//		}
//
//		Jaxmpp adminJaxmpp;
//		BareJID userAccount;
//		String username = getProperty( "test.admin.username" );
//		String password = getProperty( "test.admin.password" );
//		String domain = getDomain( 0 );
//		if ( register ){
//			createUserAccount( username, username, password, domain, username + "@" + domain );
//			userAccount = BareJID.bareJIDInstance( username, domain );
//			adminJaxmpp = createJaxmpp( username, userAccount, userAccount.getDomain(), password );
//			adminJaxmpp.login( true );
//
//			assertTrue( adminJaxmpp.isConnected(), "contact was not connected" );
//			if ( adminJaxmpp.isConnected() ){
//				adminJaxmpp.disconnect();
//			}
//		}
//	}

}
