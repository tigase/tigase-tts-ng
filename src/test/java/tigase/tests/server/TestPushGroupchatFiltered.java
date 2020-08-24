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
import tigase.jaxmpp.core.client.xmpp.modules.push.PushNotificationModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractSkippableTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Test is responsible for testing Push support.
 */
public class TestPushGroupchatFiltered
		extends AbstractSkippableTest {

	private Boolean isComponentAvailable;

	private Account user;
	private Account pushComponent;
	private Account mucComponent;
	private Jaxmpp userJaxmpp;
	private Jaxmpp mucJaxmpp;
	private Jaxmpp pushJaxmpp;

	public void enable(Jaxmpp jaxmpp, boolean away, List<Filter> filters) throws JaxmppException, InterruptedException {
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
		if (filters != null) {
			enable.addChild(ElementFactory.create("groupchat", null, "tigase:push:filter:groupchat:0"));
			for (Filter filter : filters) {
				if (filter.allow == Allow.Never) {
					Element mutedEl = enable.getFirstChild("muted");
					if (mutedEl == null) {
						mutedEl = ElementFactory.create("muted", null, "tigase:push:filter:muted:0");
						enable.addChild(mutedEl);
					}
					Element item = ElementFactory.create("item", null, null);
					item.setAttribute("jid", filter.jid.toString());
					mutedEl.addChild(item);
				} else {
					Element item = ElementFactory.create("room", null, null);
					item.setAttribute("jid", filter.jid.toString());
					item.setAttribute("allow", filter.allow == Allow.Always ? "always" : "mentioned");
					if (filter.nickname != null) {
						item.setAttribute("nickname", filter.nickname);
					}
					enable.getFirstChild("groupchat").addChild(item);
				}
			}
		}
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
		user = this.createAccount().setLogPrefix("push-").build();
		userJaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new PushNotificationModule());
			return jaxmpp;
		}).setConnected(true).build();
		pushComponent = this.createAccount().setLogPrefix("push-component-").build();
		pushJaxmpp = pushComponent.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new DummyPushModule());
			return jaxmpp;
		}).setConnected(true).build();
		mucComponent = this.createAccount().setLogPrefix("muc-component-").build();
		mucJaxmpp = mucComponent.createJaxmpp().setConnected(true).build();

		enable(userJaxmpp, false, Collections.singletonList(Filter.mentioned(mucComponent.getJid(), "nick1")));
		
		userJaxmpp.disconnect(true);
		Thread.sleep(100);
	}

	@Test
	public void testPushDeliveryOffline() throws Exception {
		final Mutex mutex = new Mutex();

		ensureDisconnected(userJaxmpp);

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertFalse(mutex.isItemNotified("push:received"));
		assertFalse(mutex.isItemNotified("push:received:body:" + body));
		assertFalse(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
	}

	@Test
	public void testPushDeliveryOfflineMentioned() throws Exception {
		final Mutex mutex = new Mutex();

		ensureDisconnected(userJaxmpp);

		String body = "Some body - " + UUID.randomUUID().toString() + " - nick1";

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertTrue(mutex.isItemNotified("push:received"));
		assertTrue(mutex.isItemNotified("push:received:body:" + body));
		assertTrue(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
	}

	@Test
	public void testPushDeliveryOfflineAlways() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(userJaxmpp);
		enable(userJaxmpp, false, Collections.singletonList(Filter.always(mucComponent.getJid())));

		ensureDisconnected(userJaxmpp);

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertTrue(mutex.isItemNotified("push:received"));
		assertTrue(mutex.isItemNotified("push:received:body:" + body));
		assertTrue(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
	}

	@Test
	public void testPushDeliveryOfflineNever() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(userJaxmpp);
		enable(userJaxmpp, false, Collections.singletonList(Filter.never(mucComponent.getJid())));

		ensureDisconnected(userJaxmpp);
		Thread.sleep(100);

		String body = "Some body - " + UUID.randomUUID().toString() + " - nick1";

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertFalse(mutex.isItemNotified("push:received"));
		assertFalse(mutex.isItemNotified("push:received:body:" + body));
		assertFalse(mutex.isItemNotified("push:received:nickname:" +
												 ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														 .getResource()));
	}

	@Test
	public void testPushDeliveryOnlineNotJoined() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(userJaxmpp);

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertFalse(mutex.isItemNotified("push:received"));
		assertFalse(mutex.isItemNotified("push:received:body:" + body));
		assertFalse(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
	}

	@Test
	public void testPushDeliveryOnlineNotJoinedMentioned() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(userJaxmpp);

		String body = "Some body - " + UUID.randomUUID().toString() + " - nick1";

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertTrue(mutex.isItemNotified("push:received"));
		assertTrue(mutex.isItemNotified("push:received:body:" + body));
		assertTrue(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
	}

	@Test
	public void testPushDeliveryOnlineNotJoinedAlways() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(userJaxmpp);
		enable(userJaxmpp, false, Collections.singletonList(Filter.always(mucComponent.getJid())));

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertTrue(mutex.isItemNotified("push:received"));
		assertTrue(mutex.isItemNotified("push:received:body:" + body));
		assertTrue(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
	}

	@Test
	public void testPushDeliveryOnlineNotJoinedNever() throws Exception {
		final Mutex mutex = new Mutex();

		ensureConnected(userJaxmpp);
		enable(userJaxmpp,false, Collections.singletonList(Filter.never(mucComponent.getJid())));

		String body = "Some body - " + UUID.randomUUID().toString() + " - nick1";

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
		msg.setTo(JID.jidInstance(user.getJid()));
		msg.setType(StanzaType.groupchat);
		msg.addChild(ElementFactory.create("body", body, null));
		mucJaxmpp.getContext().getWriter().write(msg);

		mutex.waitFor(10 * 1000, "push:received");
		assertFalse(mutex.isItemNotified("push:received"));
		assertFalse(mutex.isItemNotified("push:received:body:" + body));
		assertFalse(mutex.isItemNotified("push:received:nickname:" +
												ResourceBinderModule.getBindedJID(mucJaxmpp.getSessionObject())
														.getResource()));
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
					mutex.isItemNotified("discovery:feature:tigase:push:filter:groupchat:0") &&
					mutex.isItemNotified("discovery:feature:tigase:push:filter:muted:0");
		} catch (Exception ex) {
			isComponentAvailable = false;
		}
		return isComponentAvailable;
	}

	private static class DummyPushModule
			extends AbstractIQModule {

		private static final Criteria CRITERIA = ElementCriteria.name("iq")
				.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"));

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
					.fire(new PushReceivedHandler.PushReceivedEvent(context.getSessionObject(), pushElement, body,
																	nickname));
		}

		private interface PushReceivedHandler
				extends EventHandler {

			void receivedPush(SessionObject sessionObject, Element pushElement, String body, String nickname);

			class PushReceivedEvent
					extends JaxmppEvent<TestPushGroupchatFiltered.DummyPushModule.PushReceivedHandler> {

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
				public void dispatch(TestPushGroupchatFiltered.DummyPushModule.PushReceivedHandler handler) throws Exception {
					handler.receivedPush(sessionObject, pushElement, body, nickname);
				}
			}
		}
	}

	private enum Allow {
		Never,
		Metioned,
		Always
	}

	private static class Filter {

		static Filter never(BareJID jid) {
			return new Filter(jid, Allow.Never, null);
		}

		static Filter always(BareJID jid) {
			return new Filter(jid, Allow.Always, null);
		}
		
		static Filter mentioned(BareJID jid, String nickname) {
			return new Filter(jid, Allow.Metioned, nickname);
		}

		protected final Allow allow;
		protected final BareJID jid;
		protected final String nickname;

		Filter(BareJID jid, Allow allow, String nickname) {
			this.jid = jid;
			this.allow = allow;
			this.nickname = nickname;
		}

	}
}
