/*
 * TestMessageArchiveManagement.java
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
package tigase.tests.archive;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.AbstractField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.core.client.xmpp.utils.DateTimeFormat;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule.mapChildrenToListOfJids;

/**
 * Created by andrzej on 24.07.2016.
 */
public class TestMessageArchiveManagement2
		extends AbstractTest {

	private static final String MAM2_XMLNS = "urn:xmpp:mam:2";
	private static final String USER_PREFIX = "mam-";
	private List<Date> expDates = new ArrayList<>();
	private List<String> expStableIds = new ArrayList<>();
	private List<String> expTags = new ArrayList<>();
	private Timer timer;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeClass
	public void setUp() throws Exception {
		timer = new Timer();
		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			return jaxmpp;
		}).setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConfigurator(jaxmpp -> {
			return jaxmpp;
		}).setConnected(true).build();
	}

	@AfterClass
	public void cleanUp() throws Exception {
		timer.cancel();
	}

	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModulesManager().getModule(DiscoveryModule.class).getInfo(
				JID.jidInstance(user1Jaxmpp.getSessionObject().getUserBareJid()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
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
		assertTrue(mutex.isItemNotified("discovery:feature:" + MAM2_XMLNS));
	}

	@Test
	public void testRetrievalOfForm() throws Exception {
		final Mutex mutex = new Mutex();
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.get);

		Element query = ElementFactory.create("query", null, MAM2_XMLNS);
		iq.addChild(query);

		user1Jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("form:fields");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				Element queryEl = responseStanza.getChildrenNS("query", MAM2_XMLNS);
				if (queryEl != null) {
					Element x = queryEl.getChildrenNS("x", "jabber:x:data");
					if (x != null) {
						JabberDataElement form = new JabberDataElement(x);
						for (AbstractField f : form.getFields()) {
							Element val = f.getFirstChild("value");
							mutex.notify("form:field:" + f.getVar() + ":" + (val == null ? null : val.getValue()));
						}
					}
				}
				mutex.notify("form:fields");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("form:fields");
			}
		});

		mutex.waitFor(10 * 1000, "form:fields");

		assertTrue(mutex.isItemNotified("form:field:FORM_TYPE:" + MAM2_XMLNS));
		assertTrue(mutex.isItemNotified("form:field:with:null"));
		assertTrue(mutex.isItemNotified("form:field:start:null"));
		assertTrue(mutex.isItemNotified("form:field:end:null"));
	}

	@Test
	public void testSettingsChange() throws Exception {
		final Mutex mutex = new Mutex();

		retrieveSettings(user1Jaxmpp, new SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
								  List<JID> never) throws JaxmppException {
				mutex.notify("settings:1:default:" + defValue.name());
				mutex.notify("settings:1");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("settings:1");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("settings:1");
			}
		});
		mutex.waitFor(10 * 1000, "settings:1");

		assertTrue(mutex.isItemNotified("settings:1:default:never"));

		changeSettings(mutex, user1Jaxmpp, MessageArchiveManagementModule.DefaultValue.roster);
		changeSettings(mutex, user1Jaxmpp, MessageArchiveManagementModule.DefaultValue.always);
	}

	@Test(dependsOnMethods = {"testSettingsChange"})
	public void testMessageRetrievalFromEmpty() throws Exception {
		final Mutex mutex = new Mutex();
		testMessageRerieval(mutex, user1Jaxmpp, expTags, expStableIds, true);
	}

	@Test(dependsOnMethods = {"testMessageRetrievalFromEmpty"})
	public void testMessageArchival() throws Exception {
		final Mutex mutex = new Mutex();
		for (int i = 0; i < 20; i++) {
			Jaxmpp sender = (i % 2 == 0) ? user2Jaxmpp : user1Jaxmpp;
			Jaxmpp recipient = (i % 2 == 1) ? user2Jaxmpp : user1Jaxmpp;
			expDates.add(new Date());
			sendTestMessage(mutex, sender, recipient, i == 19, id -> {
				if (user1Jaxmpp.equals(recipient)) {
					expStableIds.add(id);
				}
			});
			Thread.sleep(2000);
		}
	}

	@Test(dependsOnMethods = {"testMessageArchival"})
	public void testMessageRetrievalWithNonEmptyResults() throws Exception {
		final Mutex mutex = new Mutex();
		testMessageRerieval(mutex, user1Jaxmpp, null, null, expTags, expStableIds, true, true);
	}

	@Test(dependsOnMethods = {"testMessageArchival"})
	public void testMessageRetrievalWithNonEmptyResultsWithQuery() throws Exception {
		final Mutex mutex = new Mutex();

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		query.setWith(JID.jidInstance(user2Jaxmpp.getSessionObject().getUserBareJid()));
		query.setStart(expDates.get(2));
		query.setEnd(new Date(expDates.get(18).getTime() - 100));

		List<String> expTags = new ArrayList<>();
		List<String> expStableIds = new ArrayList<>();
		for (int i = 0; i < this.expTags.size(); i++) {
			if (i < 2 || i >= 18) {
				continue;
			}
			String tag = this.expTags.get(i);
			expTags.add(tag);
			if (i % 2 == 0) {
				expStableIds.add(this.expStableIds.get(i/2));
			}
		}

		testMessageRerieval(mutex, user1Jaxmpp, query, expTags, expStableIds, true);
	}

	@Test(dependsOnMethods = {"testMessageArchival"})
	public void testMessageRetrievalWithNonEmptyResultsWithQueryAndRsm() throws Exception {
		final Mutex mutex = new Mutex();

		MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
		query.setWith(JID.jidInstance(user2Jaxmpp.getSessionObject().getUserBareJid()));
		query.setStart(expDates.get(2));
		query.setEnd(new Date(expDates.get(18).getTime() - 100));

		List<String> expTags = new ArrayList<>();
		List<String> expStableIds = new ArrayList<>();
		for (int i = 0; i < this.expTags.size(); i++) {
			if (i < 2 || i >= 7) {
				continue;
			}
			String tag = this.expTags.get(i);
			expTags.add(tag);
			if (i % 2 == 0) {
				expStableIds.add(this.expStableIds.get(i/2));
			}
		}

		RSM rsm = new RSM();
		rsm.setMax(5);

		testMessageRerieval(mutex, user1Jaxmpp, query, rsm, expTags, expStableIds, false, false);
	}

	public void changeSettings(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.DefaultValue value)
			throws Exception {
		String id = UUID.randomUUID().toString();
		updateSetttings(jaxmpp, value, null, null, new SettingsCallback() {
			@Override
			public void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always,
								  List<JID> never) throws JaxmppException {
				mutex.notify("settings:" + id + ":default:" + defValue.name());
				mutex.notify("settings:" + id);
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("settings:" + id);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("settings:" + id);
			}
		});
		mutex.waitFor(10 * 1000, "settings:" + id);

		assertTrue(mutex.isItemNotified("settings:" + id + ":default:" + value.name()));
	}

	protected void retrieveSettings(Jaxmpp jaxmpp, SettingsCallback callback) throws JaxmppException {
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.get);
		iq.addChild(ElementFactory.create("prefs", null, MAM2_XMLNS));
		jaxmpp.send(iq, callback);
	}

	protected void updateSetttings(Jaxmpp jaxmpp, MessageArchiveManagementModule.DefaultValue defValue, List<JID> always, List<JID> never, SettingsCallback callback)
			throws JaxmppException {
		if (defValue == null) {
			throw new JaxmppException("Default value may not be NULL!");
		}
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);
		Element prefs = ElementFactory.create("prefs", null, MAM2_XMLNS);
		prefs.setAttribute("default", defValue.name());
		iq.addChild(prefs);

		if (always != null) {
			Element alwaysEl = ElementFactory.create("always");
			for (JID jid : always) {
				alwaysEl.addChild(ElementFactory.create("jid", jid.toString(), null));
			}
			prefs.addChild(alwaysEl);
		}
		if (never != null) {
			Element neverEl = ElementFactory.create("never");
			for (JID jid : never) {
				neverEl.addChild(ElementFactory.create("jid", jid.toString(), null));
			}
			prefs.addChild(neverEl);
		}

		jaxmpp.send(iq, callback);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, List<String> expectedMessageTags, List<String> expStableIds,
									boolean complete) throws Exception {
		testMessageRerieval(mutex, jaxmpp, null, expectedMessageTags, expStableIds, complete);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query,
									List<String> expectedMessageTags, List<String> expStableIds, boolean complete) throws Exception {
		testMessageRerieval(mutex, jaxmpp, query, null, expectedMessageTags, expStableIds, complete, false);
	}

	public void testMessageRerieval(final Mutex mutex, Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query,
									RSM rsm, List<String> expectedMessageTags, List<String> expStableIds, boolean complete, boolean updateExpDates) throws Exception {
		String queryid = UUID.randomUUID().toString();

		final AtomicInteger count = new AtomicInteger(0);

		if (updateExpDates) {
			expDates.clear();
		}

		List<String> stableIds = new ArrayList<>();

		MessageArchiveItemReceivedEventHandler handler = new MessageArchiveItemReceivedEventHandler() {
			@Override
			public void onArchiveItemReceived(SessionObject sessionObject, String queryid, String messageId,
											  Date timestamp, Message message) throws JaxmppException {
				if (message.getTo().getBareJid().equals(jaxmpp.getSessionObject().getUserBareJid())) {
					stableIds.add(messageId);
				}
				mutex.notify("item:" + message.getFrom() + ":" + message.getTo() + ":" + message.getBody());
				if (updateExpDates) {
					expDates.add(timestamp);
				}
				count.incrementAndGet();
			}
		};
		jaxmpp.getContext()
				.getEventBus()
				.addHandler(
						MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
						handler);
		
		queryItems(jaxmpp, query, queryid, rsm,
							  new ResultCallback() {
								  @Override
								  public void onSuccess(String queryid, boolean complete, RSM rsm)
										  throws JaxmppException {
									  mutex.notify("items:received:" + queryid + ":" + complete);
									  timer.schedule(new TimerTask() {
										  @Override
										  public void run() {
											  mutex.notify("items:received");
										  }
									  }, 1000);

								  }

								  @Override
								  public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										  throws JaxmppException {
									  mutex.notify("items:received");
								  }

								  @Override
								  public void onTimeout() throws JaxmppException {
									  mutex.notify("items:received");
								  }
							  });

		mutex.waitFor(10 * 1000, "items:received");

		assertTrue(mutex.isItemNotified("items:received:" + queryid + ":" + complete));

		jaxmpp.getContext()
				.getEventBus()
				.remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
						handler);

		for (String tag : expectedMessageTags) {
			assertTrue("Not returned message: " + tag, mutex.isItemNotified("item:" + tag));
		}

		assertEquals(expectedMessageTags.size(), count.get());
		assertEquals(expStableIds, stableIds);
	}

	protected void queryItems(Jaxmpp jaxmpp, MessageArchiveManagementModule.Query query, String queryid, RSM rsm, ResultCallback callback) throws JaxmppException {
		if (query != null) {
			this.queryItems(jaxmpp, query.toJabberDataElement(format), queryid, rsm, callback);
		} else {
			this.queryItems(jaxmpp, (JabberDataElement) null, queryid, rsm, callback);
		}
	}
	
	protected void queryItems(Jaxmpp jaxmpp, JabberDataElement form, String queryid, RSM rsm, ResultCallback callback)
			throws JaxmppException {
		IQ iq = IQ.createIQ();
		iq.setType(StanzaType.set);

		callback.setQueryid(queryid);
		Element query = ElementFactory.create("query", null, MAM2_XMLNS);
		iq.addChild(query);

		if (queryid != null) {
			query.setAttribute("queryid", queryid);
		}

		if (form != null) {
			query.addChild(form);
		}

		if (rsm != null) {
			query.addChild(rsm.toElement());
		}

		jaxmpp.send(iq, callback);
	}

	public void sendTestMessage(final Mutex mutex, Jaxmpp sender, Jaxmpp recipient, boolean noBodyWithStore, Consumer<String> stableIdConsumer) throws Exception {
		JID to = JID.jidInstance(recipient.getSessionObject().getUserBareJid());
		String msg = noBodyWithStore ?  null : "Message-" + UUID.randomUUID().toString();
		String tag = "" + ResourceBinderModule.getBindedJID(sender.getSessionObject()) + ":" + to + ":" + msg;

		MessageModule.MessageReceivedHandler handler = new MessageModule.MessageReceivedHandler() {
			@Override
			public void onMessageReceived(SessionObject sessionObject, Chat chat, Message stanza) {
				try {
					Element stanzaId = stanza.getChildrenNS("stanza-id", "urn:xmpp:sid:0");
					if (stanzaId != null) {
						stableIdConsumer.accept(stanzaId.getAttribute("id"));
					}
					mutex.notify("" + stanza.getFrom() + ":" + stanza.getTo() + ":" + stanza.getBody());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}
		};

		recipient.getContext()
				.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);

		if (noBodyWithStore) {
			Message m = Message.create();
			m.setType(StanzaType.chat);
			m.setTo(to);
			m.setId(UIDGenerator.next());

			m.addChild(ElementFactory.create("store", null, "urn:xmpp:hints"));

			sender.getContext().getWriter().write(m);
		} else {
			sender.getModule(MessageModule.class).sendMessage(to, null, msg);
		}

		mutex.waitFor(10 * 1000, tag);

		expTags.add(tag);

		recipient.getContext()
				.getEventBus()
				.remove(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, handler);
	}

	private static final DateTimeFormat format = new DateTimeFormat();
	protected abstract class MessageArchiveItemReceivedEventHandler
			implements MessageModule.MessageReceivedHandler {

		public void onMessageReceived(SessionObject sessionObject, Chat chat, Message received) {
			try {
				Element resultEl = received.getChildrenNS("result", MAM2_XMLNS);
				if (resultEl == null) {
					return;
				}

				Element forwarded = resultEl.getChildrenNS("forwarded", "urn:xmpp:forward:0");
				if (forwarded == null) {
					return;
				}

				Element timestampEl = forwarded.getChildrenNS("delay", "urn:xmpp:delay");
				Element forwardedMessageEl = forwarded.getFirstChild("message");

				String queryid = resultEl.getAttribute("queryid");
				String messageId = resultEl.getAttribute("id");

				Date timestamp = format.parse(timestampEl.getAttribute("stamp"));
				Message forwarededMessage = (Message) Stanza.create(forwardedMessageEl);


				onArchiveItemReceived(sessionObject, queryid, messageId, timestamp, forwarededMessage);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}

		abstract void onArchiveItemReceived(SessionObject sessionObject, String queryid, String messageId, Date timestamp,
								   Message message) throws JaxmppException;

		class MessageArchiveItemReceivedEvent
				extends JaxmppEvent<MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler> {

			private final Message message;
			private final String messageId;
			private final String queryid;
			private final Date timestamp;

			MessageArchiveItemReceivedEvent(SessionObject sessionObject, String queryid, String messageId,
											Date timestamp, Message message) {
				super(sessionObject);
				this.queryid = queryid;
				this.messageId = messageId;
				this.timestamp = timestamp;
				this.message = message;
			}

			@Override
			public void dispatch(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler handler) throws Exception {
				handler.onArchiveItemReceived(sessionObject, queryid, messageId, timestamp, message);
			}
		}

	}

	protected abstract class SettingsCallback
			implements AsyncCallback {
		@Override
		public void onSuccess(Stanza responseStanza) throws JaxmppException {
			Element prefs = responseStanza.getWrappedElement().getChildrenNS("prefs", MAM2_XMLNS);
			MessageArchiveManagementModule.DefaultValue defValue = MessageArchiveManagementModule.DefaultValue.valueOf(prefs.getAttribute("default"));
			List<JID> always = mapChildrenToListOfJids(prefs.getFirstChild("always"));
			List<JID> never = mapChildrenToListOfJids(prefs.getFirstChild("never"));
			onSuccess(defValue, always, never);
		}

		public abstract void onSuccess(MessageArchiveManagementModule.DefaultValue defValue, List<JID> always, List<JID> never) throws JaxmppException;

	}

	public static abstract class ResultCallback
			implements AsyncCallback {

		private String queryid;

		@Override
		public void onSuccess(Stanza responseStanza) throws JaxmppException {
			Element fin = responseStanza.getChildrenNS("fin", MAM2_XMLNS);
			RSM rsm = new RSM();
			rsm.fromElement(fin);
			boolean complete = "true".equals(fin.getAttribute("complete"));

			onSuccess(queryid, complete, rsm);
		}

		public abstract void onSuccess(String queryid, boolean complete, RSM rsm) throws JaxmppException;

		protected void setQueryid(String queryid) {
			this.queryid = queryid;
		}
	}
}