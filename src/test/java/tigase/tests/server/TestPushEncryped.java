/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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

import groovy.json.JsonSlurper;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.TextSingleField;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractIQModule;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.push.PushNotificationModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractSkippableTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static org.testng.AssertJUnit.assertTrue;

/**
 * Test is responsible for testing Push support.
 */
public class TestPushEncryped
		extends AbstractSkippableTest {

	private Boolean isComponentAvailable;

	private Account user1;
	private Account user2;
	private Account pushComponent;
	private Jaxmpp user1Jaxmpp;
	private Jaxmpp user2Jaxmpp;
	private Jaxmpp pushJaxmpp;

	private SecretKey secretKey;
	
	public void enable(Jaxmpp jaxmpp, boolean away) throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);
		iq.setId(UIDGenerator.next());

		Element enable = ElementFactory.create("enable");
		enable.setXMLNS("urn:xmpp:push:0");
		enable.setAttribute("jid", ResourceBinderModule.getBindedJID(pushJaxmpp.getSessionObject()).toString());
		String node = "test-node";
		if (node != null) {
			enable.setAttribute("node", node);
		}
		if (away) {
			enable.setAttribute("away", "true");
		}

		String encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
		Element encrypt = ElementFactory.create("encrypt", encKey, "tigase:push:encrypt:0");
		encrypt.setAttribute("alg", "aes-128-gcm");
		enable.addChild(encrypt);
		iq.addChild(enable);
		jaxmpp.getContext().getWriter().write(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("push:enabled:error:" + error, "push:enabled");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("push:enabled:success", "push:enabled");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("push:enabled:timeout", "push:enabled");
			}
		});
		mutex.waitFor(10 * 1000, "push:enabled");
		Assert.assertTrue(mutex.isItemNotified("push:enabled:success"));
	}
	
	@BeforeClass
	public void setUp() throws Exception {                                                                    
		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(128);
		secretKey = keyGenerator.generateKey();

		user1 = this.createAccount().setLogPrefix("push1-").build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new PushNotificationModule());
			return jaxmpp;
		}).setConnected(true).build();
		pushComponent = this.createAccount().setLogPrefix("push-component-").build();
		pushJaxmpp = pushComponent.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new DummyPushModule(secretKey));
			return jaxmpp;
		}).setConnected(true).build();
		user2 = this.createAccount().setLogPrefix("push2-").build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();

		enable(user1Jaxmpp, false);

		user1Jaxmpp.disconnect(true);
		Thread.sleep(100);
	}
	
	@Test
	public void testPushDeliveryOffline() throws Exception {
		final Mutex mutex = new Mutex();

		ensureDisconnected(user1Jaxmpp);

		String body = "Some body - " + UUID.randomUUID().toString();

		pushJaxmpp.getEventBus()
				.addHandler(DummyPushModule.PushReceivedHandler.PushReceivedEvent.class,
							new DummyPushModule.PushReceivedHandler() {
								@Override
								public void receivedPush(SessionObject sessionObject, Element pushElement, String body,
														 String nickname) {
									mutex.notify("push:received:body:" + body, "push:received:nickname:" + nickname,
												 "push:received");
								}
							});

		Message msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		Assert.assertTrue(mutex.isItemNotified("push:received"));
		Assert.assertTrue(mutex.isItemNotified("push:received:body:" + body));
	}

	@Test
	public void testPushDeliveryOnline() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(user1Jaxmpp);

		String body = "Some body - " + UUID.randomUUID().toString();

		pushJaxmpp.getEventBus()
				.addHandler(DummyPushModule.PushReceivedHandler.PushReceivedEvent.class,
							new DummyPushModule.PushReceivedHandler() {
								@Override
								public void receivedPush(SessionObject sessionObject, Element pushElement, String body,
														 String nickname) {
									mutex.notify("push:received:body:" + body, "push:received:nickname:" + nickname,
												 "push:received");
								}
							});

		Message msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		Assert.assertFalse(mutex.isItemNotified("push:received"));
	}

	@Test
	public void testPushDeliveryOnlineAway() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(user1Jaxmpp);

		enable(user1Jaxmpp, false);

		user1Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.xa, null, 10);

		Thread.sleep(100);

		String body = "Some body - " + UUID.randomUUID().toString();

		pushJaxmpp.getEventBus()
				.addHandler(DummyPushModule.PushReceivedHandler.PushReceivedEvent.class,
							new DummyPushModule.PushReceivedHandler() {
								@Override
								public void receivedPush(SessionObject sessionObject, Element pushElement, String body,
														 String nickname) {
									mutex.notify("push:received:body:" + body, "push:received:nickname:" + nickname,
												 "push:received");
								}
							});

		Message msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		Assert.assertFalse(mutex.isItemNotified("push:received"));

		enable(user1Jaxmpp, true);

		msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		Assert.assertTrue(mutex.isItemNotified("push:received"));
		Assert.assertTrue(mutex.isItemNotified("push:received:body:" + body));
	}

	@Test
	public void testSupportAdvertisement() throws Exception {
		ensureConnected(user1Jaxmpp);

		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(JID.jidInstance(user1Jaxmpp.getSessionObject().getUserBareJid()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						if (identities != null) {
							identities.forEach(identity -> mutex.notify("discovery:identity:" + identity.getCategory() + ":" + identity.getType()));
						}
						if (features != null) {
							features.forEach(feature -> mutex.notify("discovery:feature:" + feature));
						}
						mutex.notify("discovery:completed:success", "discovery:completed");
					}

					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("discovery:completed:error", "discovery:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovery:completed:timeout", "discovery:completed");
					}
				});

		mutex.waitFor(10 * 1000, "discovery:completed");
		assertTrue(mutex.isItemNotified("discovery:completed:success"));
		assertTrue(mutex.isItemNotified("discovery:identity:account:registered"));
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:push:0"));
		assertTrue(mutex.isItemNotified("discovery:feature:tigase:push:encrypt:0"));
		assertTrue(mutex.isItemNotified("discovery:feature:tigase:push:encrypt:aes-128-gcm"));
	}


	@Override
	protected JID getComponentJID() {
		return null;
	}

	@Override
	protected String getComponentName() {
		return null;
	}

	@Override
	protected boolean isComponentAvailable() {
		if (isComponentAvailable != null) {
			return isComponentAvailable;
		}
		final Mutex mutex = new Mutex();
		try {
			Jaxmpp jaxmpp = getJaxmppAdmin();
			jaxmpp.getModulesManager()
					.getModule(DiscoveryModule.class)
					.getInfo(JID.jidInstance(jaxmpp.getSessionObject().getUserBareJid()),
							 new DiscoveryModule.DiscoInfoAsyncCallback(null) {
								 @Override
								 protected void onInfoReceived(String s,
															   Collection<DiscoveryModule.Identity> identities,
															   Collection<String> features) throws XMLException {
									 if (identities != null) {
										 identities.forEach(identity -> mutex.notify(
												 "discovery:identity:" + identity.getCategory() + ":" +
														 identity.getType()));
									 }
									 if (features != null) {
										 features.forEach(feature -> mutex.notify("discovery:feature:" + feature));
									 }
									 mutex.notify("discovery:completed:success", "discovery:completed");
								 }

								 @Override
								 public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
										 throws JaxmppException {
									 mutex.notify("discovery:completed:error", "discovery:completed");
								 }

								 @Override
								 public void onTimeout() throws JaxmppException {
									 mutex.notify("discovery:completed:timeout", "discovery:completed");
								 }
							 });

			mutex.waitFor(10 * 1000, "discovery:completed");
			isComponentAvailable = mutex.isItemNotified("discovery:completed:success") &&
					mutex.isItemNotified("discovery:identity:account:registered") &&
					mutex.isItemNotified("discovery:feature:urn:xmpp:push:0") &&
					mutex.isItemNotified("discovery:feature:tigase:push:encrypt:0");
		} catch (Exception ex) {
			isComponentAvailable = false;
		}
		return isComponentAvailable;
	}

	private static class DummyPushModule
			extends AbstractIQModule {

		private static final Criteria CRITERIA = ElementCriteria.name("iq")
				.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"));

		private final SecretKey secretKey;

		DummyPushModule(SecretKey secretKey) {
			this.secretKey = secretKey;
		}

		@Override
		public Criteria getCriteria() {
			return CRITERIA;
		}

		@Override
		public String[] getFeatures() {
			return new String[0];
		}

		@Override
		protected void processGet(IQ element) throws JaxmppException {
			// nothing to do...
		}

		@Override
		protected void processSet(IQ element) throws JaxmppException {
			Element pushElement = element.getFirstChild("pubsub")
					.getFirstChild("publish")
					.getFirstChild("item")
					.getFirstChild("notification");

			Element encrypted = pushElement.getFirstChild("encrypted");
			if (encrypted != null) {
				String data = encrypted.getValue();
				String iv = encrypted.getAttribute("iv");
				if (data != null) {
					try {
						GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, Base64.getDecoder().decode(iv));
						Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
						cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
						Map<String, Object> result = (Map<String, Object>) new JsonSlurper().parse(
								cipher.doFinal(Base64.getDecoder().decode(data)), "UTF-8");
						context.getEventBus()
								.fire(new DummyPushModule.PushReceivedHandler.PushReceivedEvent(context.getSessionObject(), pushElement, (String) result.get("message"),
																								(String) result.get("nickname")));
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}
			Element x = pushElement.getFirstChild("x");
			if (x != null) {
				JabberDataElement dataElement = new JabberDataElement(x);
				TextSingleField field = dataElement.getField("message-count");
				if (field != null && "0".equals(field.getFieldValue())) {
					return;
				}

			}
			context.getEventBus()
					.fire(new DummyPushModule.PushReceivedHandler.PushReceivedEvent(context.getSessionObject(), pushElement, null,
																									  null));
		}

		private interface PushReceivedHandler
				extends EventHandler {

			void receivedPush(SessionObject sessionObject, Element pushElement, String body, String nickname);

			class PushReceivedEvent
					extends JaxmppEvent<TestPushEncryped.DummyPushModule.PushReceivedHandler> {

				private final Element pushElement;
				private final String body;
				private final String nickname;

				private PushReceivedEvent(SessionObject sessionObject, Element pushElement, String body,
										  String nickname) {
					super(sessionObject);
					this.pushElement = pushElement;
					this.body = body;
					this.nickname = nickname;
				}

				@Override
				public void dispatch(TestPushEncryped.DummyPushModule.PushReceivedHandler handler) throws Exception {
					handler.receivedPush(sessionObject, pushElement, body, nickname);
				}
			}
		}
	}

}
