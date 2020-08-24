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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Base64;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCard;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;
import tigase.util.Algorithms;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import static org.testng.Assert.*;

public class TestPepUserAvatarToVCardConversion extends AbstractJaxmppTest {

	private static final String XMLNS = "urn:xmpp:pep-vcard-conversion:0";

	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;

	@BeforeMethod
	public void prepareMethod() throws Exception {
		user1 = createAccount().setLogPrefix("pep-vcard_").build();
		user2 = createAccount().setLogPrefix("pep-vcard_").build();
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();

		testSubscribeAndWait(user1Jaxmpp, user2Jaxmpp);
		testSubscribeAndWait(user2Jaxmpp, user1Jaxmpp);
	}

	@Test
	public void test() throws JaxmppException, InterruptedException, NoSuchAlgorithmException {
		assertTrue(((Set<String>) user1Jaxmpp.getSessionObject().getProperty(DiscoveryModule.SERVER_FEATURES_KEY)).contains(XMLNS));
		user1Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.away, null, null);
		Thread.sleep(500);

		Presence presence = PresenceModule.getPresenceStore(user2Jaxmpp.getSessionObject()).getPresence(ResourceBinderModule.getBindedJID(user1Jaxmpp.getSessionObject()));
		assertNotNull(presence);
		Element x = presence.getFirstChild("x");
		assertNull(x);

		checkVCarPhoto(user1Jaxmpp, user1.getJid(), null);
		checkVCarPhoto(user2Jaxmpp, user2.getJid(), null);
		checkVCarPhoto(user2Jaxmpp, user1.getJid(), null);

		byte[] photo = new byte[1024];
		String photoStr = Base64.encode(photo);
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		String photoHash = Algorithms.bytesToHex(md.digest(photo));

		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModule(PubSubModule.class).publishItem(user1.getJid(), "urn:xmpp:avatar:data", photoHash,
															  ElementFactory.create("data", photoStr,
																					"urn:xmpp:avatar:data"), new PubSubModule.PublishAsyncCallback() {
					@Override
					public void onPublish(String itemId) {
						mutex.notify("pep:data:publish:" + itemId + ":success", "pep:data:publish");
					}

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("pep:data:publish:error:" + errorCondition, "pep:data:publish");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("pep:data:publish:timeout", "pep:data:publish");
					}
				});
		mutex.waitFor(10 * 1000, "pep:data:publish");
		assertTrue(mutex.isItemNotified("pep:data:publish:" + photoHash + ":success"));

		Thread.sleep(1000);

		Element metdata = ElementFactory.create("metadata", null, "urn:xmpp:avatar:metadata");
		Element info = ElementFactory.create("info" );
		metdata.addChild(info);
		info.setAttribute("id", photoHash);
		info.setAttribute("type", "image/png");
		
		user1Jaxmpp.getModule(PubSubModule.class).publishItem(user1.getJid(), "urn:xmpp:avatar:metadata", photoHash,
															  metdata, new PubSubModule.PublishAsyncCallback() {
					@Override
					public void onPublish(String itemId) {
						mutex.notify("pep:metadata:publish:" + itemId + ":success", "pep:metadata:publish");
					}

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("pep:metadata:publish:error:" + errorCondition, "pep:metadata:publish");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("pep:metadata:publish:timeout", "pep:metadata:publish");
					}
				});
		mutex.waitFor(10 * 1000, "pep:metadata:publish");
		assertTrue(mutex.isItemNotified("pep:metadata:publish:" + photoHash + ":success"));

		Thread.sleep(2000);

		PresenceModule.ContactChangedPresenceHandler handler = (sessionObject, presence1, jid, show, s, integer) -> {
			if (jid != null && show != null) {
				mutex.notify("pep:received:presence:" + jid.getBareJid() + ":" + show.name());
			}
		};

		user2Jaxmpp.getEventBus().addHandler(PresenceModule.ContactChangedPresenceHandler.ContactChangedPresenceEvent.class, handler);
		user1Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.away, null, null);

		mutex.waitFor(10 * 1000, "pep:received:presence:" + user1.getJid() + ":" + Presence.Show.away);
		assertTrue(mutex.isItemNotified("pep:received:presence:" + user1.getJid() + ":" + Presence.Show.away));

		user2Jaxmpp.getEventBus().remove(handler);

		Thread.sleep(100);

		presence = PresenceModule.getPresenceStore(user2Jaxmpp.getSessionObject()).getPresence(ResourceBinderModule.getBindedJID(user1Jaxmpp.getSessionObject()));
		assertNotNull(presence);
		x = presence.getFirstChild("x");
		assertNotNull(x);
		Element photoEl = x.getFirstChild("photo");
		assertNotNull(photoEl);
		assertEquals(photoEl.getValue(), photoHash);

		checkVCarPhoto(user1Jaxmpp, user1.getJid(), photoStr);
		checkVCarPhoto(user2Jaxmpp, user1.getJid(), photoStr);
		checkVCarPhoto(user2Jaxmpp, user2.getJid(), null);

		user2Jaxmpp.getModule(PresenceModule.class).setPresence(Presence.Show.away, null, null);
		Thread.sleep(500);
		presence = PresenceModule.getPresenceStore(user1Jaxmpp.getSessionObject()).getPresence(ResourceBinderModule.getBindedJID(user2Jaxmpp.getSessionObject()));
		assertNotNull(presence);
		x = presence.getFirstChild("x");
		assertNull(x);
	}

	public static void checkVCarPhoto(Jaxmpp user1Jaxmpp, BareJID bareJid, String photo) throws InterruptedException, JaxmppException {
		JID jid = JID.jidInstance(bareJid);

		final Mutex mutex = new Mutex();
		user1Jaxmpp.getModulesManager().getModule(VCardModule.class).retrieveVCard(jid, new VCardModule.VCardAsyncCallback() {
			@Override
			protected void onVCardReceived(VCard vcard) throws XMLException {
				mutex.notify("vcard:" + jid + ":photo:success:" + vcard.getPhotoVal(), "vcard:" + jid + ":photo");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("vcard:" + jid + ":photo:error:" + error, "vcard:" + jid + ":photo");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("vcard:" + jid + ":photo:timeout", "vcard:" + jid + ":photo");

			}
		});

		mutex.waitFor(10 * 100, "vcard:" + jid + ":photo");
		assertTrue(mutex.isItemNotified("vcard:" + jid + ":photo:success:" + photo));
	}
}
