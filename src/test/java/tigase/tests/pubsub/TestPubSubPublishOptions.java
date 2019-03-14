/*
 * TestPubSubPublishOptions.java
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

package tigase.tests.pubsub;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.UUID;

import static org.testng.AssertJUnit.assertTrue;

public class TestPubSubPublishOptions extends AbstractJaxmppTest {

	private Account user;
	private Jaxmpp jaxmpp;

	private BareJID pubsubJid;
	private String node;

	@BeforeTest
	public void setupBeforeTest() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("publish-options_").setRegister(true).build();
		jaxmpp = user.createJaxmpp().setConnected(true).build();
	}

	@AfterTest
	public void tearDownAfterTest() throws InterruptedException, JaxmppException {
		if (pubsubJid != null && node != null) {
			final Mutex mutext = new Mutex();
			jaxmpp.getModule(PubSubModule.class).deleteNode(pubsubJid, node, (AsyncCallback) new AsyncCallback() {
				@Override
				public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
					mutext.notify("node:deleted");
				}

				@Override
				public void onSuccess(Stanza responseStanza) throws JaxmppException {
					mutext.notify("node:deleted");
				}

				@Override
				public void onTimeout() throws JaxmppException {
					mutext.notify("node:deleted");
				}
			});
			mutext.waitFor(10 * 1000, "node:deleted");
		}
	}

	@Test
	public void testPublishOptions_creation_PEP() throws InterruptedException, JaxmppException {
		final Mutex mutex = new Mutex();

		pubsubJid = user.getJid();//BareJID.bareJIDInstance("pubsub." + user.getJid().getDomain());
		JabberDataElement publishOptions = new JabberDataElement(XDataType.submit);
		publishOptions.addFORM_TYPE("http://jabber.org/protocol/pubsub#publish-options");
		publishOptions.addFixedField("pubsub#access_model", "whitelist");

		String node = UUID.randomUUID().toString();
		jaxmpp.getModule(PubSubModule.class).publishItem(pubsubJid, node, null,
														 ElementFactory.create("payload", null, "Some content " +
																 UUID.randomUUID().toString()), publishOptions, new PubSubModule.PublishAsyncCallback() {
					@Override
					public void onPublish(String itemId) {
						TestPubSubPublishOptions.this.node = node;
						mutex.notify("item:publish:success", "item:publish");
					}

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("item:publish:error:" + errorCondition, "item:publish");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("item:publish:timeout", "item:publish");
					}
				});

		mutex.waitFor(10 * 1000, "item:publish");
		assertTrue(mutex.isItemNotified("item:publish:success"));

		jaxmpp.getModule(PubSubModule.class).getNodeConfiguration(pubsubJid, node, new PubSubModule.NodeConfigurationAsyncCallback() {
			@Override
			protected void onReceiveConfiguration(IQ responseStanza, String node, JabberDataElement form) {
				try {
					mutex.notify("configuration:received:access_model:" + form.getField("pubsub#access_model").getFieldValue(), "configuration:received");
				} catch (XMLException e) {
					//
				}
			}

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify("configuration:received:error:" + errorCondition, "configuration:received");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("configuration:received:timeout", "configuration:received");
			}
		});

		mutex.waitFor(10 * 1000, "configuration:received");
		assertTrue(mutex.isItemNotified("configuration:received:access_model:whitelist"));
	}

	@Test
	public void testPublishOptions_publication_PUBSUB() throws InterruptedException, JaxmppException {
		testPublishOptions_publication(BareJID.bareJIDInstance("pubsub." + user.getJid().getDomain()));
	}

	@Test
	public void testPublishOptions_publication_PEP() throws InterruptedException, JaxmppException {
		testPublishOptions_publication(user.getJid());
	}

	protected void testPublishOptions_publication(BareJID pubsubJid) throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		this.pubsubJid = pubsubJid;
		JabberDataElement config = new JabberDataElement(XDataType.submit);
		config.addFORM_TYPE("http://jabber.org/protocol/pubsub#node_config");
		config.addFixedField("pubsub#access_model", "whitelist");

		String node = UUID.randomUUID().toString();
		jaxmpp.getModule(PubSubModule.class).createNode(pubsubJid, node, config, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify("node:create:error:" + errorCondition, "node:create");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				TestPubSubPublishOptions.this.node = node;
				mutex.notify("node:create:success", "node:create");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("node:create:timeout", "node:create");
			}
		});

		mutex.waitFor(10 * 1000, "node:create");
		assertTrue(mutex.isItemNotified("node:create:success"));

		JabberDataElement publishOptions = new JabberDataElement(XDataType.submit);
		publishOptions.addFORM_TYPE("http://jabber.org/protocol/pubsub#publish-options");
		publishOptions.addFixedField("pubsub#access_model", "whitelist");

		jaxmpp.getModule(PubSubModule.class).publishItem(pubsubJid, node, null,
														 ElementFactory.create("payload", null, "Some content " +
																 UUID.randomUUID().toString()), publishOptions, new PubSubModule.PublishAsyncCallback() {
					@Override
					public void onPublish(String itemId) {
						TestPubSubPublishOptions.this.node = node;
						mutex.notify("item:1:publish:success", "item:1:publish");
					}

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("item:1:publish:error:" + errorCondition, "item:1:publish");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("item:1:publish:timeout", "item:1:publish");
					}
				});

		mutex.waitFor(10 * 1000, "item:1:publish");
		assertTrue(mutex.isItemNotified("item:1:publish:success"));
		
		publishOptions = new JabberDataElement(XDataType.submit);
		publishOptions.addFORM_TYPE("http://jabber.org/protocol/pubsub#publish-options");
		publishOptions.addFixedField("pubsub#access_model", "presence");

		jaxmpp.getModule(PubSubModule.class).publishItem(pubsubJid, node, null,
														 ElementFactory.create("payload", null, "Some content " +
																 UUID.randomUUID().toString()), publishOptions, new PubSubModule.PublishAsyncCallback() {
					@Override
					public void onPublish(String itemId) {
						TestPubSubPublishOptions.this.node = node;
						mutex.notify("item:2:publish:success", "item:2:publish");
					}

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("item:2:publish:error:" + errorCondition, "item:2:publish");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("item:2:publish:timeout", "item:2:publish");
					}
				});

		mutex.waitFor(10 * 1000, "item:2:publish");
		assertTrue(mutex.isItemNotified("item:2:publish:error:conflict"));
	}

}
