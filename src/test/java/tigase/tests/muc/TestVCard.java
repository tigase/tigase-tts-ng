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
import tigase.TestLogger;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

public class TestVCard extends AbstractTest {

	private static String BINVAL;

	private static String generateRandomImage(int width, int height) throws Exception {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int r = (int) (Math.random() * 256);
				int g = (int) (Math.random() * 256);
				int b = (int) (Math.random() * 256);

				int p = (r << 16) | (g << 8) | b;

				img.setRGB(x, y, p);
			}
		}
		ImageIO.write(img, "png", java.util.Base64.getEncoder().wrap(os));

		TestLogger.log("Generated test image, size: " + (os.size() / 1024) + "KB");
		return os.toString(StandardCharsets.ISO_8859_1.name());
	}

	private MucModule muc1Module;
	private BareJID roomJID;
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private VCardModule vcardModule;
	private String avatarHash = null;

	@BeforeClass
	protected void setUp() throws Exception {
		BINVAL = generateRandomImage(2 * 1024, 1024);
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
