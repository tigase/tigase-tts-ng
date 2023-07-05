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
package tigase.tests.muc;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubAsyncCallback;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;
import tigase.util.Algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestRTBL extends AbstractTest {

	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;
	private Account user3;
	private Jaxmpp user3Jaxmpp;
	private MucModule muc1Module;
	private MucModule muc2Module;
	private MucModule muc3Module;
	private BareJID roomJID;

	final MutableObject<JabberDataElement> roomConfig = new MutableObject<JabberDataElement>();
	@BeforeTest
	public void prepareMucRoom() throws Exception {
		Mutex mutex = new Mutex();
		this.user1 = createAccount().setLogPrefix("user1").build();
		this.user2 = createAccount().setLogPrefix("user2").build();
		this.user3 = createAccount().setLogPrefix("user3").build();
		
		this.user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();
		this.user2Jaxmpp = user2.createJaxmpp().setConnected(true).build();
		this.user3Jaxmpp = user3.createJaxmpp().setConnected(true).build();

		this.roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1.getJid().getDomain());

		this.muc1Module = user1Jaxmpp.getModule(MucModule.class);
		this.muc2Module = user2Jaxmpp.getModule(MucModule.class);
		this.muc3Module = user3Jaxmpp.getModule(MucModule.class);

		muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user1");
		user1Jaxmpp.getEventBus()
				.addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class,
							(sessionObject, room, asNickname) -> mutex.notify("1:joinAs:" + asNickname));
		mutex.waitFor(1000 * 20, "1:joinAs:user1");
		assertTrue(mutex.isItemNotified("1:joinAs:user1"));

		muc1Module.getRoomConfiguration(muc1Module.getRoom(roomJID), new MucModule.RoomConfgurationAsyncCallback() {
			@Override
			public void onConfigurationReceived(JabberDataElement jabberDataElement) throws XMLException {
				roomConfig.setValue(jabberDataElement);
				try {
					ElementBuilder b = ElementBuilder.create("iq");
					b.setAttribute("id", nextRnd())
							.setAttribute("to", roomJID.toString())
							.setAttribute("type", "set")
							.child("query")
							.setXMLNS("http://jabber.org/protocol/muc#owner")
							.child("x")
							.setXMLNS("jabber:x:data")
							.setAttribute("type", "submit");

					user1Jaxmpp.send(Stanza.create(b.getElement()));
				} catch (JaxmppException e) {
					fail(e);
				}
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

		Thread.sleep(1000);

		muc1Module.setRoomConfiguration(muc1Module.getRoom(roomJID), roomConfig.getValue(), new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				TestLogger.log("Error on set config: " + errorCondition);
				mutex.notify("setConfig", "setConfig:error");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("setConfig", "setConfig:success");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("setConfig", "setConfig:timeout");
			}
		});
		mutex.waitFor(1000 * 20, "setConfig");
		assertTrue(mutex.isItemNotified("setConfig:success"));

		//disableAllRTBLs();
		Thread.sleep(1000);
	}

	@AfterTest
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

		disableAllRTBLs();
	}
	
	@Test()
	public void createRTBLNode() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addFixedField("pubsub#access_model", "open");
		form.addFixedField("pubsub#deliver_notifications", "true");
		form.addFixedField("pubsub#presence_based_delivery", "false");
		form.addFixedField("pubsub#notify_retract", "true");
		form.addFixedField("pubsub#max_items", "10");
		form.addFixedField("pubsub#persist_items", "true");
		form.addFixedField("pubsub#send_last_published_item", "never");
		user1Jaxmpp.getModule(PubSubModule.class).createNode(user1.getJid(), "rtbl", form, new PubSubAsyncCallback() {
			@Override
			protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
								  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
				mutex.notify("node:create:error:" + errorCondition.name(), "node:create:completed");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				mutex.notify("node:create:success", "node:create:completed");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("node:create:error:timeout", "node:create:completed");
			}
		});

		mutex.waitFor(10 * 1000, "node:create:completed");
		assertTrue(mutex.isItemNotified("node:create:success"));
	}

	@Test(dependsOnMethods = {"createRTBLNode"})
	public void enableEmptyRTBL() throws JaxmppException, InterruptedException {
		enableRTBL();
	}

	@Test(dependsOnMethods = {"enableEmptyRTBL"})
	public void testJoinEmptyRTBL() throws JaxmppException, InterruptedException {
		assertTrue(testJoin(user2Jaxmpp));
		assertTrue(testJoin(user3Jaxmpp));
	}

	@Test(dependsOnMethods = {"testJoinEmptyRTBL"})
	public void blockUser2() throws JaxmppException, InterruptedException {
		blockUser(user2.getJid());
	}

	@Test(dependsOnMethods = {"blockUser2"})
	public void testJoinBlockedUser2_1() throws JaxmppException, InterruptedException {
		assertFalse(testJoin(user2Jaxmpp));
		assertTrue(testJoin(user3Jaxmpp));
	}

	@Test(dependsOnMethods = {"testJoinBlockedUser2_1"})
	public void disableRTBL_1() throws JaxmppException, InterruptedException {
		disableRTBL();
	}

	@Test(dependsOnMethods = {"disableRTBL_1"})
	public void testJoinNoRTBL() throws JaxmppException, InterruptedException {
		assertTrue(testJoin(user2Jaxmpp));
		assertTrue(testJoin(user3Jaxmpp));
	}

	@Test(dependsOnMethods = {"testJoinNoRTBL"})
	public void enableRTBL_NonEmpty() throws JaxmppException, InterruptedException {
		enableRTBL();
	}

	@Test(dependsOnMethods = {"enableRTBL_NonEmpty"})
	public void testJoinBlockedUser2_2() throws JaxmppException, InterruptedException {
		assertFalse(testJoin(user2Jaxmpp));
		assertTrue(testJoin(user3Jaxmpp));
	}

	@Test(dependsOnMethods = {"testJoinBlockedUser2_2"})
	public void testBlockingJoinedUser3() throws JaxmppException, InterruptedException {
		String user = user3.getJid().getLocalpart();
		Mutex mutex = new Mutex();
		MucModule.PresenceErrorHandler errorHandler = (sessionObject, room, presence, string) -> mutex.notify(user + ":room:completed");
		MucModule.YouJoinedHandler handler = (sessionObject, room, asNickname) -> mutex.notify(user + ":room:joined", user + ":room:completed");
		user3Jaxmpp.getEventBus().addHandler(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, errorHandler);
		user3Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, handler);
		Room room = muc3Module.join(roomJID.getLocalpart(), roomJID.getDomain(), user);
		mutex.waitFor(10 * 1000, user + ":room:joined");
		assertTrue(mutex.isItemNotified(user + ":room:joined"));
		user3Jaxmpp.getEventBus().remove(handler);
		user3Jaxmpp.getEventBus().remove(errorHandler);

		blockUser(user3.getJid());

		String body = "Test " + UUID.randomUUID().toString();
		user3Jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class,
											 (sessionObject, streamPacket) -> {
												 try {
													 if ("message".equals(streamPacket.getName()) && room.getRoomJid().toString().equals(streamPacket.getWrappedElement().getAttribute("from"))) {
														 if ("error".equals(streamPacket.getWrappedElement().getAttribute("type")) && streamPacket.getWrappedElement().getAsString().contains(body) && streamPacket.getWrappedElement().getAsString().contains("<forbidden ")) {
															 mutex.notify("message:received:error:" + body);
														 }
													 }
												 } catch (XMLException e) {
													 throw new RuntimeException(e);
												 }
												 mutex.notify("message:received");
											 });
		user3Jaxmpp.getEventBus()
				.addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class,
							(sessionObject, chat, message) -> {
								try {
									mutex.notify("message:received:" + message.getType() + ":" + message.getBody(),
												 "message:received");
								} catch (XMLException e) {
									mutex.notify("message:received:" + e.getMessage(), "message:received");
								}
							});
		room.sendMessage(body);

		mutex.waitFor(10 * 1000, "message:received");
		assertTrue(mutex.isItemNotified("message:received:error:" + body));

		muc3Module.leave(room);
	}
	
	private void blockUser(BareJID userJid) throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		String id = Algorithms.sha256(userJid.toString());
		user1Jaxmpp.getModule(PubSubModule.class).publishItem(user1.getJid(), "rtbl", id, ElementBuilder.create("dummy").getElement(),
															  new PubSubModule.PublishAsyncCallback() {
																  @Override
																  public void onPublish(String id) {
																	  mutex.notify("block:publish:success", "block:publish:completed");
																  }

																  @Override
																  protected void onEror(IQ iq,
																						XMPPException.ErrorCondition errorCondition,
																						PubSubErrorCondition pubSubErrorCondition)
																		  throws JaxmppException {
																	  mutex.notify("block:publish:error:" + errorCondition.name(), "block:publish:completed");
																  }

																  @Override
																  public void onTimeout() throws JaxmppException {
																	  mutex.notify("block:publish:error:timeout", "block:publish:completed");
																  }
															  });

		mutex.waitFor(10 * 1000, "block:publish:completed");
		assertTrue(mutex.isItemNotified("block:publish:success"));
		Thread.sleep(1000);
	}

	private boolean testJoin(Jaxmpp jaxmpp) throws InterruptedException, JaxmppException {
		String user = jaxmpp.getSessionObject().getUserBareJid().getLocalpart();
		Mutex mutex = new Mutex();
		MucModule.PresenceErrorHandler errorHandler = (sessionObject, room, presence, string) -> mutex.notify(user + ":room:completed");
		MucModule.YouJoinedHandler handler = (sessionObject, room, asNickname) -> mutex.notify(user + ":room:joined", user + ":room:completed");
		jaxmpp.getEventBus().addHandler(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, errorHandler);
		jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, handler);
		Room room = jaxmpp.getModule(MucModule.class).join(roomJID.getLocalpart(), roomJID.getDomain(), user);
		mutex.waitFor(10 * 1000, user + ":room:completed");
		boolean result = mutex.isItemNotified(user + ":room:joined");
		jaxmpp.getEventBus().remove(handler);
		jaxmpp.getEventBus().remove(errorHandler);
		Thread.sleep(100);
		jaxmpp.getModule(MucModule.class).leave(room);
		Thread.sleep(100);
		return result;
	}


	private void enableRTBL() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addFixedField("pubsubJid", user1.getJid().toString());
		form.addFixedField("node", "rtbl");
		form.addFixedField("hash", "SHA-256");
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(JID.jidInstance("rtbl-component", getAdminAccount().getJid().getDomain()), "rtbl-add",
																	 Action.execute, form, new AsyncCallback() {
					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("rtbl:add:error:" + errorCondition.name(), "rtbl:add:completed");
					}

					@Override
					public void onSuccess(Stanza stanza) throws JaxmppException {
						mutex.notify("rtbl:add:success", "rtbl:add:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("rtbl:add:error:timeout", "rtbl:add:completed");
					}
				});
		mutex.waitFor(10 * 1000, "rtbl:add:completed");
		assertTrue(mutex.isItemNotified("rtbl:add:success"));

		// wait for list data to be fetched
		Thread.sleep(1000);
	}

	private void disableRTBL() throws JaxmppException, InterruptedException {
		disableRTBL(user1.getJid(), "rtbl");
	}
	private void disableRTBL(BareJID jid, String node) throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addFixedField("blocklist", jid + "/" + node);
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(JID.jidInstance("rtbl-component", getAdminAccount().getJid().getDomain()), "rtbl-delete",
																	 Action.execute, form, new AsyncCallback() {
					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("rtbl:add:error:" + errorCondition.name(), "rtbl:add:completed");
					}

					@Override
					public void onSuccess(Stanza stanza) throws JaxmppException {
						mutex.notify("rtbl:add:success", "rtbl:add:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("rtbl:add:error:timeout", "rtbl:add:completed");
					}
				});
		mutex.waitFor(10 * 1000, "rtbl:add:completed");
		assertTrue(mutex.isItemNotified("rtbl:add:success"));

		// wait for list data to be removed
		Thread.sleep(1000);
	}

	private void disableAllRTBLs() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		List<Item> items = new ArrayList<>();

		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(JID.jidInstance("rtbl-component", getAdminAccount().getJid().getDomain()), "rtbl-delete", Action.execute, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
					throws JaxmppException {
				mutex.notify("rtbl:fetch:completed");
			}

			@Override
			protected void onResponseReceived(String s, String s1, State state, JabberDataElement form)
					throws JaxmppException {
				List<Element> children = form.getField("blocklist").getChildren("option");
				if (children != null) {
					for (Element option : children) {
						Element value = option.getFirstChild("value");
						if (value != null) {
							JID jid = JID.jidInstance(value.getValue());
							items.add(new Item(jid.getBareJid(), jid.getResource()));
						}
					}
				}
				mutex.notify("rtbl:fetch:completed");
			}
			
			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("rtbl:fetch:completed");
			}
		});
		mutex.waitFor(10 * 1000, "rtbl:fetch:completed");
		assertTrue(mutex.isItemNotified("rtbl:fetch:completed"));

		for (Item item : items) {
			disableRTBL(item.jid, item.node);
		}
	}

	private class Item {
		public final BareJID jid;
		public final String node;

		public Item(BareJID jid, String node) {
			this.jid = jid;
			this.node = node;
		}
	}
}
