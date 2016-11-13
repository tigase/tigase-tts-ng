/*
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
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
 *
 */
package tigase.tests.http;

import org.testng.Assert;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Base64;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule.RetrieveItemsAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static tigase.TestLogger.log;

/**
 *
 * @author andrzej
 */
public class TestRetrievalOfUserAvatarUsingREST extends AbstractTest {

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	private static final String USER_AVATAR_DATA_NODE = "urn:xmpp:avatar:data";

	private static final String USER_AVATAR_METADATA_NODE = "urn:xmpp:avatar:metadata";

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	protected void fail(String msg) {
		log(msg);
		Assert.fail(msg);
	}

	private byte[] generateRandomAvatarData() {
		byte[] data = new byte[4096];
		Random random = new Random();
		random.nextBytes(data);

		return data;
	}

	private String hash(byte[] data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] hash = md.digest(data);
		return bytesToHex(hash);
	}

	private void publishAvatar(final Mutex mutex, Jaxmpp jaxmpp1, byte[] data) throws XMLException, JaxmppException,
			NoSuchAlgorithmException, InterruptedException {
		PubSubModule pubsubModule = jaxmpp1.getModule(PubSubModule.class);

		final String id = hash(data);
		String b64Data = Base64.encode(data);
		Element dataPayload = ElementFactory.create("data", b64Data, USER_AVATAR_DATA_NODE);

		pubsubModule.publishItem(jaxmpp1.getSessionObject().getUserBareJid(), USER_AVATAR_DATA_NODE, id, dataPayload,
				new PubSubModule.PublishAsyncCallback() {

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
							PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						fail("Was not able to publish data: " + errorCondition + " - " + pubSubErrorCondition);
					}

					@Override
					public void onPublish(String itemId) {
						assertEquals("Received confirmation of publication of item with wrong id", id, itemId);
						mutex.notify("published:data:" + itemId + ":jaxmpp1");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						throw new UnsupportedOperationException("Not supported yet.");
					}
				});

		mutex.waitFor(1000 * 20, "published:data:" + id + ":jaxmpp1");
		Thread.sleep(1000 * 2);
		assertTrue("Data item not published properly", mutex.isItemNotified("published:data:" + id + ":jaxmpp1"));

		Element metaPayload = ElementFactory.create("metadata", null, USER_AVATAR_METADATA_NODE);
		Element metaInfo = metaPayload.addChild(ElementFactory.create("info"));
		metaInfo.setAttribute("bytes", String.valueOf(data.length));
		metaInfo.setAttribute("id", id);
		metaInfo.setAttribute("type", "image/png");

		pubsubModule.publishItem(jaxmpp1.getSessionObject().getUserBareJid(), USER_AVATAR_METADATA_NODE, id, metaPayload,
				new PubSubModule.PublishAsyncCallback() {

					@Override
					protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
							PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						fail("Was not able to publish metadata: " + errorCondition + " - " + pubSubErrorCondition);
					}

					@Override
					public void onPublish(String itemId) {
						assertEquals("Received confirmation of publication of item with wrong id", id, itemId);
						mutex.notify("published:meta:" + itemId + ":jaxmpp1");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						throw new UnsupportedOperationException("Not supported yet.");
					}
				});

		mutex.waitFor(1000 * 20, "published:meta:" + id + ":jaxmpp1");
		Thread.sleep(1000 * 2);
		assertTrue("Metadata item not published properly", mutex.isItemNotified("published:meta:" + id + ":jaxmpp1"));
	}

	private void retrieveAvatar(final Mutex mutex, final Jaxmpp jaxmpp, BareJID userJid, final byte[] data,
			final boolean shouldReceive, final String uuid) throws JaxmppException, NoSuchAlgorithmException,
			InterruptedException {
		PubSubModule pubsubModule = jaxmpp.getModule(PubSubModule.class);

		final String id = hash(data);
		pubsubModule.retrieveItems(userJid, USER_AVATAR_METADATA_NODE, 1, null, new PubSubModule.RetrieveItemsAsyncCallback() {

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
					PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				if (!shouldReceive) {
					mutex.notify("received:meta:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString() + ":"
							+ uuid);
				} else {
					fail("Should receive meta result but got error = " + errorCondition + ", " + pubSubErrorCondition);
				}
			}

			@Override
			protected void onRetrieve(IQ responseStanza, String nodeName, Collection<RetrieveItemsAsyncCallback.Item> items) {
				if (shouldReceive) {
					assertEquals("Could not retrieve metadata info", 1, items.size());
					for (Item item : items) {
						try {
							Element info = item.getPayload().getChildren("info").get(0);
							assertEquals("Received metadata info with wrong bytes value", String.valueOf(data.length),
									info.getAttribute("bytes"));
							assertEquals("Received metadata info with wrong id", id, info.getAttribute("id"));
							mutex.notify("received:meta:" + info.getAttribute("id") + ":"
									+ jaxmpp.getSessionObject().getUserBareJid().toString() + ":" + uuid);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				} else {
					assertEquals("Was able to retrieve metadata but it should not be allowed", 0, items.size());
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet.");
			}

		});

		mutex.waitFor(1000 * 20, "received:meta:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString() + ":"
				+ uuid);
		Thread.sleep(1000 * 2);
		assertTrue(
				"Error during retrieval of metadata item",
				mutex.isItemNotified("received:meta:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString() + ":"
						+ uuid));

		pubsubModule.retrieveItem(userJid, USER_AVATAR_DATA_NODE, id, new RetrieveItemsAsyncCallback() {

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
					PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				if (!shouldReceive) {
					mutex.notify("received:data:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString() + ":"
							+ uuid);
				} else {
					fail("Should receive data result but got error = " + errorCondition + ", " + pubSubErrorCondition);
				}
			}

			@Override
			protected void onRetrieve(IQ responseStanza, String nodeName, Collection<RetrieveItemsAsyncCallback.Item> items) {
				if (shouldReceive) {
					assertEquals("Could not retrieve data info", 1, items.size());
					for (Item item : items) {
						try {
							Element dataEl = item.getPayload();
							assertEquals("Received wrong data", data, Base64.decode(dataEl.getValue()));
							mutex.notify("received:data:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString()
									+ ":" + uuid);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				} else {
					assertEquals("Was able to retrieve data but it should not be allowed", 0, items.size());
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				throw new UnsupportedOperationException("Not supported yet.");
			}

		});

		mutex.waitFor(1000 * 20, "received:data:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString() + ":"
				+ uuid);
		Thread.sleep(1000 * 2);
		assertTrue(
				"Error during retrieval of data item",
				mutex.isItemNotified("received:data:" + id + ":" + jaxmpp.getSessionObject().getUserBareJid().toString() + ":"
						+ uuid));
	}

	@Test(groups = { "Phase 1" }, description = "Test User Avatar support - XEP-0084")
	public void testUserAvatarSupportXEP0084() throws JaxmppException, InterruptedException, XMLException,
			NoSuchAlgorithmException, MalformedURLException, IOException {
		byte[] avatarData = generateRandomAvatarData();

		final BareJID user1JID = createUserAccount("user1");
		final BareJID user2JID = createUserAccount("user2");
		final Jaxmpp jaxmpp1 = createJaxmpp("user1", user1JID);
		final Jaxmpp jaxmpp2 = createJaxmpp("user2", user2JID);

		jaxmpp1.getModulesManager().register(new PubSubModule());
		jaxmpp1.getModulesManager().register(new RosterModule());
		jaxmpp2.getModulesManager().register(new PubSubModule());
		jaxmpp2.getModulesManager().register(new RosterModule());

		jaxmpp1.login(true);
		jaxmpp2.login(true);

		final Mutex mutex = new Mutex();

		publishAvatar(mutex, jaxmpp1, avatarData);
		retrieveAvatar(mutex, jaxmpp1, user1JID, avatarData, true, nextRnd());
		retrieveAvatar(mutex, jaxmpp2, user1JID, avatarData, false, nextRnd());

		RosterItem ri = new RosterItem(user2JID, jaxmpp1.getSessionObject());
		ri.setSubscription(RosterItem.Subscription.from);
		RosterModule.getRosterStore(jaxmpp1.getSessionObject()).update(ri);

		Thread.sleep(1000 * 2);

		Element subscrEl = ElementFactory.create("presence");
		subscrEl.setAttribute("to", user1JID.toString());
		subscrEl.setAttribute("type", StanzaType.subscribe.toString());
		jaxmpp2.send(Stanza.create(subscrEl));

		Thread.sleep(1000 * 3);

		subscrEl = ElementFactory.create("presence");
		subscrEl.setAttribute("to", user2JID.toString());
		subscrEl.setAttribute("type", StanzaType.subscribed.toString());
		jaxmpp1.send(Stanza.create(subscrEl));

		Thread.sleep(1000 * 10);

		retrieveAvatar(mutex, jaxmpp2, user1JID, avatarData, true, nextRnd());

		String domain = getDomain(0);
		String[] instances = getInstanceHostnames();
		if (instances != null && instances.length > 0) {
			domain = instances[0];
		}

		URL url = new URL("http://" + domain + ":" + getHttpPort() + "/rest/avatar/" + user1JID.toString() + "/avatar?api-key=" + getApiKey());
		URLConnection con = url.openConnection();
		con.setDoInput(true);
		InputStream is = con.getInputStream();
		byte[] data = new byte[avatarData.length * 2];
		int read = 0;
		int r = 0;
		while ((r = is.read(data, read, data.length - read)) != -1) {
			read += r;
		}
		// we need to remove HTTP headers data from bytes read from stream
		data = Arrays.copyOfRange(data, read - 4096, read);
		assertEquals(avatarData, data);

		jaxmpp1.disconnect();
		jaxmpp2.disconnect();
	
	}
}
