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
package tigase.tests.server.offlinemsg;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.Affiliation;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule.NotificationReceivedHandler;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;
import tigase.tests.utils.PubSubNode;

import java.util.Arrays;
import java.util.Date;

public class TestOfflineMessageSinkProvider
		extends AbstractTest {

	private PubSubNode testNode;
	private Account userA;
	private Account userB;

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		userA = createAccount().setLogPrefix("userA").build();
		userB = createAccount().setLogPrefix("userB").build();

		Jaxmpp adminJaxmpp = getJaxmppAdmin();
		preparePubSubNode(adminJaxmpp);
	}

	@Test(groups = {"Offline Message"}, description = "Offline Message Sink Provider")
	public void testOfflineMessageSinkProvider() throws Exception {
		final Mutex mutex = new Mutex();

		Jaxmpp ownerJaxmpp = getAdminAccount().createJaxmpp().setConnected(true).build();
		PubSubModule pubSub = ownerJaxmpp.getModule(PubSubModule.class);
		pubSub.addNotificationReceivedHandler(new NotificationReceivedHandler() {

			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID pubSubJID,
											   String nodeName, String itemId, Element payload, Date delayTime,
											   String itemType) {
				try {
					Message msg = (Message) Message.create(payload);
					mutex.notify("received:" + msg.getBody());
				} catch (JaxmppException e) {
					e.printStackTrace();
					Assert.fail(e.getMessage());
				}
			}
		});

		Jaxmpp userAJaxmpp = userA.createJaxmpp().setConnected(true).build();

		final String body = "body-" + nextRnd();
		final Message msg1 = Message.create();
		msg1.setTo(JID.jidInstance(userB.getJid()));
		msg1.setBody(body);
		msg1.setType(StanzaType.chat);
		msg1.setId(nextRnd());
		userAJaxmpp.send(msg1);

		mutex.waitFor(1000 * 30, "received:" + body);

		Assert.assertTrue(mutex.isItemNotified("received:" + body), "Notification from PubSub not received! body: " + body);
	}

	private void preparePubSubNode(Jaxmpp ownerJaxmpp) throws JaxmppException, InterruptedException {
		testNode = pubSubManager.createNode("test")
				.setNodeType(PubSubNode.Type.leaf)
				.setJaxmpp(ownerJaxmpp)
				.setReplaceIfExists(true)
				.build();

		PubSubModule pubSub = ownerJaxmpp.getModule(PubSubModule.class);
		String[] domains = getDomains();
		if (domains != null) {
			final Mutex mutex = new Mutex();
			Arrays.stream(domains).forEach(domain -> {
				try {
					pubSub.setAffiliation(testNode.getPubsubJid(), testNode.getName(),
										  JID.jidInstance("sess-man", domain), Affiliation.publisher,
										  new PubSubAsyncCallback() {
											  @Override
											  public void onSuccess(Stanza responseStanza) throws JaxmppException {
												  mutex.notify(domain + ":affiliation:success");
												  mutex.notify(domain + ":affiliation");
											  }

											  @Override
											  public void onTimeout() throws JaxmppException {
												  mutex.notify(domain + ":affiliation:timeout");
												  mutex.notify(domain + ":affiliation");
											  }

											  @Override
											  protected void onEror(IQ response,
																	XMPPException.ErrorCondition errorCondition,
																	PubSubErrorCondition pubSubErrorCondition)
													  throws JaxmppException {
												  mutex.notify(domain + ":affiliation:error");
												  mutex.notify(domain + ":affiliation");
											  }
										  });

					mutex.waitFor(30 * 1000, domain + ":affiliation");
					Assert.assertTrue(mutex.isItemNotified(domain + ":affiliation:success"));
				} catch (JaxmppException | InterruptedException ex) {
					ex.printStackTrace();
				}
			});
		}

		final Mutex mutex = new Mutex();
		pubSub.subscribe(testNode.getPubsubJid(), testNode.getName(), JID.jidInstance(ownerJaxmpp.getSessionObject().getUserBareJid()), new PubSubModule.SubscriptionAsyncCallback() {

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("subscribe", "subscribe:timeout");
			}

			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify("subscribe", "subscribe:error");
			}

			@Override
			protected void onSubscribe(IQ iq, PubSubModule.SubscriptionElement subscriptionElement) {
				mutex.notify("subscribe", "subscribe:true");
			}
		});

		mutex.waitFor(1000 * 30, "subscribe");

		Assert.assertTrue(mutex.isItemNotified("subscribe:true"));

		pubSubManager.remove(testNode);
	}
}
