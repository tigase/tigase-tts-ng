/*
 * TestPush.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2019 "Tigase, Inc." <office@tigase.com>
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
import org.testng.annotations.BeforeMethod;
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

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.testng.AssertJUnit.assertTrue;

/**
 * Test is responsible for testing Push support.
 */
public class TestPush
		extends AbstractSkippableTest {

	private Boolean isComponentAvailable;

	private Account user1;
	private Account user2;
	private Account pushComponent;
	private Jaxmpp user1Jaxmpp;
	private Jaxmpp user2Jaxmpp;
	private Jaxmpp pushJaxmpp;


	@BeforeMethod
	public void setUp() throws Exception {
		user1 = this.createAccount().setLogPrefix("push1-").build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new PushNotificationModule());
			return jaxmpp;
		}).setConnected(true).build();
		pushComponent = this.createAccount().setLogPrefix("push-component-").build();
		pushJaxmpp = pushComponent.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new DummyPushModule());
			return jaxmpp;
		}).setConnected(true).build();
		user2 = this.createAccount().setLogPrefix("push2-").build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();

		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModule(PushNotificationModule.class)
				.enable(ResourceBinderModule.getBindedJID(pushJaxmpp.getSessionObject()), "test-node",
						new AsyncCallback() {
							@Override
							public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									throws JaxmppException {
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

		user1Jaxmpp.disconnect(true);
		Thread.sleep(100);
	}

	@Test
	public void testPushDeliveryOffline() throws Exception {
		final Mutex mutex = new Mutex();

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

		Jaxmpp userJaxmpp2 = user1.createJaxmpp().setConnected(true).build();

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

		Account user1 = createAccount().setRegister(true).setLogPrefix("push3-").build();
		Jaxmpp userJaxmpp2 = user1.createJaxmpp().setConnected(true).build();

		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);
		iq.setId(UIDGenerator.next());

		Element enable = ElementFactory.create("enable");
		enable.setXMLNS("urn:xmpp:push:0");
		enable.setAttribute("jid", ResourceBinderModule.getBindedJID(pushJaxmpp.getSessionObject()).toString());
		enable.setAttribute("node", "test-node");
		iq.addChild(enable);
		userJaxmpp2.getContext().getWriter().write(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
					throws JaxmppException {
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

		userJaxmpp2.getModule(PresenceModule.class).setPresence(Presence.Show.xa, null, 10);

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

		enable.setAttribute("away", "true");
		userJaxmpp2.getContext().getWriter().write(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
					throws JaxmppException {
				mutex.notify("push:enabled:2:error:" + error, "push:enabled:2");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("push:enabled:2:success", "push:enabled:2");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("push:enabled:2:timeout", "push:enabled:2");
			}
		});

		mutex.waitFor(10 * 1000, "push:enabled:2");
		Assert.assertTrue(mutex.isItemNotified("push:enabled:2:success"));

		msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		Assert.assertTrue(mutex.isItemNotified("push:received"));
		Assert.assertTrue(mutex.isItemNotified("push:received:body:" + body));
	}

	@Test
	public void testAutomaticDisablingOnFailure() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		String body = "Some body - " + UUID.randomUUID().toString();

		AtomicInteger counter = new AtomicInteger(0);

		pushJaxmpp.getEventBus()
				.addHandler(DummyPushModule.PushReceivedHandler.PushReceivedEvent.class,
							new DummyPushModule.PushReceivedHandler() {
								@Override
								public void receivedPush(SessionObject sessionObject, Element pushElement, String body,
														 String nickname) {
									mutex.notify("push:received:" + counter.get() + ":body:" + body, "push:received:" + counter.get() + ":nickname:" + nickname,
												 "push:received:" + counter.get());
								}
							});

		counter.incrementAndGet();

		Message msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received:1");
		Assert.assertTrue(mutex.isItemNotified("push:received:1"));
		Assert.assertTrue(mutex.isItemNotified("push:received:1:body:" + body));

		pushJaxmpp.getModulesManager().getModule(DummyPushModule.class).resultGenerator = DummyPushModule.RETURN_ERROR;

		counter.incrementAndGet();

		msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received:2");
		Assert.assertTrue(mutex.isItemNotified("push:received:2"));
		Assert.assertTrue(mutex.isItemNotified("push:received:2:body:" + body));

		counter.incrementAndGet();

		// added processing time, to give server time to process an error response from the Push component
		Thread.sleep(1000);

		msg = Message.create();
		msg.setTo(JID.jidInstance(user1.getJid()));
		msg.addChild(ElementFactory.create("body", body, null));
		user2Jaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received:3");
		Assert.assertFalse(mutex.isItemNotified("push:received:3"));
		Assert.assertFalse(mutex.isItemNotified("push:received:3:body:" + body));
	}

	@Test
	public void testSupportAdvertisement() throws Exception {
		Jaxmpp user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		assertTrue(user1Jaxmpp.isConnected());
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
					mutex.isItemNotified("discovery:feature:urn:xmpp:push:0");
		} catch (Exception ex) {
			isComponentAvailable = false;
		}
		return isComponentAvailable;
	}

	private static class DummyPushModule
			extends AbstractIQModule {

		private static final Criteria CRITERIA = ElementCriteria.name("iq")
				.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"));

		private static Function<IQ, Element> RETURN_OK = stanza -> {
			try {
				Element result = ElementFactory.create(stanza.getName(), null, null);
				result.setAttribute("type", "result");
				result.setAttribute("to", stanza.getAttribute("from"));
				result.setAttribute("id", stanza.getAttribute("id"));
				return result;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		};

		private static Function<IQ, Element> RETURN_ERROR = stanza -> {
			try {
				Element result = ElementFactory.create(stanza.getName(), null, null);
				result.setAttribute("type", "error");
				result.setAttribute("to", stanza.getAttribute("from"));
				result.setAttribute("id", stanza.getAttribute("id"));

				Element error = ElementFactory.create("error", null, null);
				error.addChild(ElementFactory.create("item-not-found", null, "urn:ietf:params:xml:ns:xmpp-stanzas"));
				result.addChild(error);
				return result;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		};

		private Function<IQ, Element> resultGenerator = RETURN_OK;

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
			JabberDataElement data = new JabberDataElement(pushElement.getFirstChild("x"));
			String body = ((TextSingleField) data.getField("last-message-body")).getFieldValue();
			Element groupchat = pushElement.getFirstChild("groupchat");
			Element nicknameEl = groupchat != null ? groupchat.getFirstChild("nickname") : null;
			String nickname = nicknameEl != null ? nicknameEl.getValue() : null;
			context.getEventBus()
					.fire(new DummyPushModule.PushReceivedHandler.PushReceivedEvent(context.getSessionObject(), pushElement, body,
																									  nickname));
			context.getWriter().write(resultGenerator.apply(element));
		}

		private interface PushReceivedHandler
				extends EventHandler {

			void receivedPush(SessionObject sessionObject, Element pushElement, String body, String nickname);

			class PushReceivedEvent
					extends JaxmppEvent<DummyPushModule.PushReceivedHandler> {

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
				public void dispatch(DummyPushModule.PushReceivedHandler handler) throws Exception {
					handler.receivedPush(sessionObject, pushElement, body, nickname);
				}
			}
		}
	}

}
