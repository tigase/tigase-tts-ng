/*
 * TestVCard.java
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

package tigase.tests.muc;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.BooleanField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCard;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

public class TestVCard extends AbstractTest {

	private static final String BINVAL = "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";

	private MucModule muc1Module;
	private BareJID roomJID;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private VCardModule vcardModule;
	private String avatarHash = null;

	@BeforeClass
	protected void setUp() throws Exception {
		byte[] binval = Base64.decode(BINVAL);
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		
		avatarHash = bytesToHex(md.digest(binval));

		user1 = createAccount().setLogPrefix("user1").build();
		user1Jaxmpp = user1.createJaxmpp().setConfigurator(this::configureJaxmpp).setConnected(true).build();

		muc1Module = user1Jaxmpp.getModule(MucModule.class);
		vcardModule = user1Jaxmpp.getModule(VCardModule.class);

		roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1.getJid().getDomain());

		final Mutex mutex = new Mutex();
		MucModule.YouJoinedHandler youJoinedHandler = new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, final Room room, final String asXNickname) {
				mutex.notify("joinAs:user1");
			}
		};
		user1Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, youJoinedHandler);
		muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user1.getJid().getLocalpart());
		mutex.waitFor(1000 * 20, "joinAs:user1");
		assertTrue(mutex.isItemNotified("joinAs:user1"));

		user1Jaxmpp.getEventBus().remove(youJoinedHandler);

		final MutableObject<JabberDataElement> roomConfig = new MutableObject<JabberDataElement>();
		muc1Module.getRoomConfiguration(muc1Module.getRoom(roomJID), new MucModule.RoomConfgurationAsyncCallback() {
			@Override
			public void onConfigurationReceived(JabberDataElement jabberDataElement) throws XMLException {
				roomConfig.setValue(jabberDataElement);
				mutex.notify("getConfig:success", "getConfig");
			}

			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("getConfig:error", "getConfig");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("getConfig:timeout", "getConfig");
			}
		});

		mutex.waitFor(1000 * 20, "getConfig");
		assertTrue(mutex.isItemNotified("getConfig:success"));

		BooleanField persistent = roomConfig.getValue().getField("muc#roomconfig_persistentroom");
		persistent.setFieldValue(true);

		muc1Module.setRoomConfiguration(muc1Module.getRoom(roomJID), roomConfig.getValue(), new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("setConfig:error", "setConfig");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("setConfig:success", "setConfig");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("setConfig:timeout", "setConfig");
			}
		});

		mutex.waitFor(1000 * 20, "setConfig");
		assertTrue(mutex.isItemNotified("setConfig:success"));
	}

	@AfterClass
	public void destroyMucRoom() throws JaxmppException, InterruptedException {
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setTo(JID.jidInstance(roomJID));

		final Mutex mutex = new Mutex();
		Element query = ElementFactory.create("query", null, "http://jabber.org/protocol/muc#owner");
		query.addChild(ElementFactory.create("destroy"));
		iq.addChild(query);
		user1Jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("room:destroyed:error:" + error,"room:destroyed");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("room:destroyed:success", "room:destroyed");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("room:destroyed:timeout", "room:destroyed");
			}
		});
		mutex.waitFor(10 * 1000, "room:destroyed");
		assertTrue(mutex.isItemNotified("room:destroyed:success"));
	}

	@Test
	public void testSettingAvatar() throws JaxmppException, InterruptedException {
		IQ iq = IQ.createIQ();
		iq.setTo(JID.jidInstance(roomJID));
		iq.setType(StanzaType.set);

		Element vcard = ElementFactory.create("vCard", null, "vcard-temp");
		iq.addChild(vcard);

		Element photo = ElementFactory.create("PHOTO");
		vcard.addChild(photo);

		Element type = ElementFactory.create("TYPE", "image/png", null);
		photo.addChild(type);

		Element binval = ElementFactory.create("BINVAL", BINVAL, null);
		photo.addChild(binval);

		final Mutex mutex = new Mutex();
		MucModule.MucMessageReceivedHandler mucMessageReceivedHandler = new MucModule.MucMessageReceivedHandler() {
			@Override
			public void onMucMessageReceived(SessionObject sessionObject, Message message, Room room, String nickname,
											 Date timestamp) {
				try {
					Element x = message.getChildrenNS("x", "http://jabber.org/protocol/muc#user");
					if (x != null) {
						List<Element> children = x.getChildren("status");
						if (children != null) {
							for (Element child : children) {
								mutex.notify("message:" + message.getFrom() + ":status:" + child.getAttribute("code"));
							}
						}
					}
				} catch (JaxmppException ex) {
					ex.printStackTrace();
				}
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, mucMessageReceivedHandler);

		user1Jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("setVCard:error", "setVCard");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("setVCard:success", "setVCard");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("setVCard:timeout", "setVCard");
			}
		});
		mutex.waitFor(1000 * 20, "setVCard");
		assertTrue(mutex.isItemNotified("setVCard:success"));

		mutex.waitFor(1000 * 20, "message:" + roomJID + ":status:104");
		assertTrue(mutex.isItemNotified("message:" + roomJID + ":status:104"));

		user1Jaxmpp.getEventBus().remove(mucMessageReceivedHandler);
	}

	@Test(dependsOnMethods = {"testSettingAvatar"})
	public void testMUCRoomVCardRetrieval() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();
		vcardModule.retrieveVCard(JID.jidInstance(roomJID), new VCardModule.VCardAsyncCallback() {
			@Override
			protected void onVCardReceived(VCard vcard) throws XMLException {
				mutex.notify("getVCard:photo:type:" + vcard.getPhotoType());
				mutex.notify("getVCard:photo:binval:" + vcard.getPhotoVal());

				String photo = vcard.getPhotoVal();
				try {
					MessageDigest md = MessageDigest.getInstance("SHA-1");
					String hash = bytesToHex(md.digest(Base64.decode(photo)));
					mutex.notify("getVCard:photo:hash:" + hash);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}

				mutex.notify("getVCard:success", "getVCard");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("getVCard:error", "getVCard");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("getVCard:timeout", "getVCard");
			}
		});
		mutex.waitFor(1000 * 20, "getVCard");
		assertTrue(mutex.isItemNotified("getVCard:success"));
		assertTrue(mutex.isItemNotified("getVCard:photo:type:image/png"));
		assertTrue(mutex.isItemNotified("getVCard:photo:binval:" + BINVAL));
		assertTrue(mutex.isItemNotified("getVCard:photo:hash:" + avatarHash));
	}

	@Test(dependsOnMethods = {"testMUCRoomVCardRetrieval"})
	public void testMUCOccupantAvatarRetrieval() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();
		vcardModule.retrieveVCard(JID.jidInstance(roomJID, user1.getJid().getLocalpart()), new VCardModule.VCardAsyncCallback() {
			@Override
			protected void onVCardReceived(VCard vcard) throws XMLException {
				mutex.notify("getVCard:photo:binval:" + vcard.getPhotoVal());
				mutex.notify("getVCard:success", "getVCard");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("getVCard:error", "getVCard");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("getVCard:timeout", "getVCard");
			}
		});
		mutex.waitFor(1000 * 20, "getVCard");
		assertTrue(mutex.isItemNotified("getVCard:success"));
		assertFalse(mutex.isItemNotified("getVCard:photo:binval:" + BINVAL));
	}

	@Test(dependsOnMethods = {"testMUCOccupantAvatarRetrieval"})
	public void testPresenceOnRejoin() throws JaxmppException, InterruptedException {
		muc1Module.leave(muc1Module.getRoom(roomJID));

		Thread.sleep(1000);

		final Mutex mutex = new Mutex();
		MucModule.YouJoinedHandler youJoinedHandler = new MucModule.YouJoinedHandler() {
			@Override
			public void onYouJoined(SessionObject sessionObject, final Room room, final String asXNickname) {
				mutex.notify("joinAs:user1");
			}
		};
		PresenceModule.ContactChangedPresenceHandler presenceChangedHandler = new PresenceModule.ContactChangedPresenceHandler() {
			@Override
			public void onContactChangedPresence(SessionObject sessionObject, Presence stanza,
												 tigase.jaxmpp.core.client.JID jid, Presence.Show show, String status,
												 Integer priority) throws JaxmppException {
				Element x = stanza.getWrappedElement().getChildrenNS("x", "vcard-temp:x:update");
				Element photo = x == null ? null : x.getFirstChild("photo");
				String hash = photo == null ? null : photo.getValue();
				mutex.notify("presence:" + stanza.getFrom() + ":" + show + ":" + hash,
							 "presence:" + stanza.getFrom() + ":" + show);
			}
		};
		user1Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, youJoinedHandler);
		user1Jaxmpp.getEventBus()
				.addHandler(PresenceModule.ContactChangedPresenceHandler.ContactChangedPresenceEvent.class,
							presenceChangedHandler);
		muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user1.getJid().getLocalpart());
		mutex.waitFor(1000 * 20, "joinAs:user1");
		assertTrue(mutex.isItemNotified("joinAs:user1"));

		mutex.waitFor(1000 * 20, "presence:" + roomJID + ":" + Presence.Show.online);
		assertTrue(mutex.isItemNotified("presence:" + roomJID + ":" + Presence.Show.online + ":" + avatarHash));

		user1Jaxmpp.getEventBus().remove(youJoinedHandler);
		user1Jaxmpp.getEventBus().remove(presenceChangedHandler);
	}

	protected Jaxmpp configureJaxmpp(Jaxmpp jaxmpp) {
		jaxmpp.getModulesManager().register(new VCardModule());
		return jaxmpp;
	}

	private static String bytesToHex(byte[] hashInBytes) {

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < hashInBytes.length; i++) {
			sb.append(Integer.toString((hashInBytes[i] & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();

	}
}
