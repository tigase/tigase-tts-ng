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
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.XmppModule;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.BookmarksModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.testng.Assert.*;

public class TestPepBookmarksConversion extends AbstractJaxmppTest {

	private Account user1;
	private Jaxmpp jaxmpp1;

	@BeforeMethod
	private void prepareTest() throws JaxmppException, InterruptedException {
		user1 = createAccount().setLogPrefix("pep-bookmarks_").build();
		jaxmpp1 = user1.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new tigase.jaxmpp.core.client.xmpp.modules.BookmarksModule());
			jaxmpp.getModulesManager().register(new PEPBookmarksModule());
			return jaxmpp;
		}).setConnected(true).build();
	}

	@Test
	private void test() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();
		jaxmpp1.getModule(PubSubModule.class).addNotificationReceivedHandler(
				(sessionObject, message, pubSubJID, nodeName, itemId, payload, delayTime, itemType) -> {
					try {
						List<Element> children = payload.getChildren("conference");
						if (children != null) {
							for (Element child : children) {
								mutex.notify("pep:" + sessionObject.getUserBareJid() + ":event:" + nodeName + ":" +
													 payload.getName() + ":" + child.getAttribute("name") + ":" +
													 child.getAttribute("jid"));
							}
						}
					} catch (XMLException e) {
						e.printStackTrace();
					}
				});


		jaxmpp1.getModule(BookmarksModule.class).retrieveBookmarks(new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("private:1:retrieve:error:" + error, "private:1:retrieve");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				Element elem = responseStanza.getFirstChild("query");
				Element storage = elem == null ? null : elem.getFirstChild("storage");
				if (storage != null) {
					List<Element> children = storage.getChildren("conference");
					if (children != null) {
						for (Element child : children) {
							mutex.notify("private:1:retrieve:success:" + child.getAttribute("name") + ":" +
												 child.getAttribute("jid"));
						}
					}
				}
				mutex.notify("private:1:retrieve:success", "private:1:retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("private:1:retrieve:timeout", "private:1:retrieve");
			}
		});
		mutex.waitFor(10 * 1000, "private:1:retrieve");
		assertTrue(mutex.isItemNotified("private:1:retrieve:success"));
		assertFalse(mutex.isItemNotified("private:1:retrieve:success:Test 1:room-1@muc.example.com"));
		
		jaxmpp1.getModule(PubSubModule.class).retrieveItem(user1.getJid(), "storage:bookmarks", new PubSubModule.RetrieveItemsAsyncCallback() {
			@Override
			protected void onRetrieve(IQ responseStanza, String nodeName, Collection<Item> items) {
				mutex.notify("pep:2:retrieve:success:" + items.size(), "pep:2:retrieve");
			}

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify("pep:2:retrieve:error:" + errorCondition, "pep:2:retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("pep:2:retrieve:timeout", "pep:2:retrieve");
			}
		});
		mutex.waitFor(10 * 1000, "pep:2:retrieve");
		assertTrue(mutex.isItemNotified("pep:2:retrieve:error:item_not_found"));

		Element bookmarkEl = ElementFactory.create("conference");
		bookmarkEl.setAttribute("name", "Test 1");
		bookmarkEl.setAttribute("jid", "room-1@muc.example.com");
		jaxmpp1.getModule(BookmarksModule.class).publishBookmarks(Arrays.asList(bookmarkEl), new AsyncCallback() {
			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("private:3:publish:error:" + error, "private:3:publish");
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				mutex.notify("private:3:publish:success", "private:3:publish");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("private:3:publish:timeout", "private:3:publish");
			}
		});
		mutex.waitFor(10 * 1000, "private:3:publish");
		assertTrue(mutex.isItemNotified("private:3:publish:success"));

		String tag = "pep:" + user1.getJid() + ":event:storage:bookmarks:storage:Test 1:room-1@muc.example.com";
		if (!mutex.isItemNotified(tag)) {
			Thread.sleep(1000);
		}
		assertTrue(mutex.isItemNotified(tag));

		jaxmpp1.getModule(BookmarksModule.class).retrieveBookmarks(new BookmarksModule.BookmarksAsyncCallback() {
			@Override
			public void onBookmarksReceived(List<Element> bookmarks) {
				if (bookmarks != null) {
					for (Element child : bookmarks) {
						try {
							mutex.notify("private:4:retrieve:success:" + child.getAttribute("name") + ":" + child.getAttribute("jid"));
						} catch (JaxmppException ex) {
							assertNull(ex);
						}
					}
				}
				mutex.notify("private:4:retrieve:success", "private:4:retrieve");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("private:4:retrieve:error:" + error, "private:4:retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("private:4:retrieve:timeout", "private:4:retrieve");
			}
		});
		mutex.waitFor(10 * 1000, "private:4:retrieve");
		assertTrue(mutex.isItemNotified("private:4:retrieve:success"));
		assertTrue(mutex.isItemNotified("private:4:retrieve:success:Test 1:room-1@muc.example.com"));

		Element storageEl = ElementFactory.create("storage", null, "storage:bookmarks");
		bookmarkEl = ElementFactory.create("conference");
		bookmarkEl.setAttribute("name", "Test 2");
		bookmarkEl.setAttribute("jid", "room-2@muc.example.com");
		storageEl.addChild(bookmarkEl);

		jaxmpp1.getModule(PubSubModule.class).publishItem(user1.getJid(), "storage:bookmarks", "current", storageEl, new PubSubModule.PublishAsyncCallback() {
			@Override
			public void onPublish(String itemId) {
				mutex.notify("pep:5:publish:success", "pep:5:publish");
			}

			@Override
			protected void onEror(IQ response, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify("pep:5:publish:error:" + errorCondition, "pep:5:publish");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("pep:5:publish:timeout", "pep:5:publish");
			}
		});
		mutex.waitFor(10 * 1000, "pep:5:publish");
		assertTrue(mutex.isItemNotified("pep:5:publish:success"));

		tag = "pep:" + user1.getJid() + ":event:storage:bookmarks:storage:Test 2:room-2@muc.example.com";
		if (!mutex.isItemNotified(tag)) {
			Thread.sleep(1000);
		}
		assertTrue(mutex.isItemNotified(tag));

		jaxmpp1.getModule(BookmarksModule.class).retrieveBookmarks(new BookmarksModule.BookmarksAsyncCallback() {
			@Override
			public void onBookmarksReceived(List<Element> bookmarks) {
				if (bookmarks != null) {
					for (Element child : bookmarks) {
						try {
							mutex.notify("private:6:retrieve:success:" + child.getAttribute("name") + ":" + child.getAttribute("jid"));
						} catch (JaxmppException ex) {
							assertNull(ex);
						}
					}
				}
				mutex.notify("private:6:retrieve:success", "private:4:retrieve");
			}

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify("private:6:retrieve:error:" + error, "private:4:retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("private:6:retrieve:timeout", "private:4:retrieve");
			}
		});
		mutex.waitFor(10 * 1000, "private:6:retrieve");
		assertTrue(mutex.isItemNotified("private:6:retrieve:success"));
		assertTrue(mutex.isItemNotified("private:6:retrieve:success:Test 2:room-2@muc.example.com"));
	}

	private class PEPBookmarksModule implements XmppModule {

		@Override
		public Criteria getCriteria() {
			return ElementCriteria.empty();
		}

		@Override
		public String[] getFeatures() {
			return new String[] { "storage:bookmarks+notify" };
		}

		@Override
		public void process(Element element) throws XMPPException, XMLException, JaxmppException {
			// nothing to do...
		}
	}

}
