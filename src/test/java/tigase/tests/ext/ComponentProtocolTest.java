/*
 * ComponentProtocolTest.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2018 "Tigase, Inc." <office@tigase.com>
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

package tigase.tests.ext;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.ListSingleField;
import tigase.jaxmpp.core.client.xmpp.forms.TextSingleField;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ComponentProtocolTest
		extends AbstractTest {

	@BeforeClass
	public void prepareForTest() throws JaxmppException, InterruptedException {
		removeAllSettings();
	}

	@AfterClass
	public void cleanUpAfterTest() throws JaxmppException, InterruptedException {
		removeAllSettings();
	}

	@Test
	public void testConnection1() throws IOException, InterruptedException {
		testConnection(5270, false);
	}

	@Test(dependsOnMethods = {"testConnection1"})
	public void addConnectionAccept() throws JaxmppException, InterruptedException, IOException {
		Mutex mutex = new Mutex();
		JID extJID = JID.jidInstance("ext", this.getDomain());
		getJaxmppAdmin().getModule(AdHocCommansModule.class)
				.execute(extJID, "comp-repo-item-add", Action.execute, null,
						 new AdHocCommansModule.AdHocCommansAsyncCallback() {
							 @Override
							 protected void onResponseReceived(String sessionid, String node, State status,
															   JabberDataElement _data) throws JaxmppException {

							 	 JabberDataElement data = new JabberDataElement(ElementFactory.create(_data));
								 ((TextSingleField) data.getField("Domain name")).setFieldValue("muc-ext." + getDomain());
								 ((TextSingleField) data.getField("Domain password")).setFieldValue("muc-pass");
								 ((ListSingleField) data.getField("Connection type")).setFieldValue("accept");
								 ((TextSingleField) data.getField("Port number")).setFieldValue("5270");
							 	 
								 getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(extJID, "comp-repo-item-add", Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

									 @Override
									 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											 throws JaxmppException {
										 mutex.notify("item:add:" + error, "item:add");
									 }

									 @Override
									 public void onTimeout() throws JaxmppException {
										 mutex.notify("item:add:timeout", "item:add");
									 }

									 @Override
									 protected void onResponseReceived(String sessionid, String node, State status,
																	   JabberDataElement data) throws JaxmppException {
										 mutex.notify("item:add:success", "item:add");
									 }
								 });
							 }

							 @Override
							 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									 throws JaxmppException {
								 mutex.notify("item:add:" + error, "item:add");
							 }

							 @Override
							 public void onTimeout() throws JaxmppException {
								 mutex.notify("item:add:timeout", "item:add");
							 }
						 });

		mutex.waitFor(20 * 1000, "item:add");
		assertTrue(mutex.isItemNotified("item:add:success"));

		testConnection(5270, true);
	}

	@Test(dependsOnMethods = {"addConnectionAccept"})
	public void updateConnectionAccept() throws JaxmppException, InterruptedException, IOException {
		Mutex mutex = new Mutex();
		JID extJID = JID.jidInstance("ext", this.getDomain());
		JabberDataElement initialForm = new JabberDataElement(XDataType.submit);
		initialForm.addListSingleField("item-list", "muc-ext." + getDomain());
		getJaxmppAdmin().getModule(AdHocCommansModule.class)
				.execute(extJID, "comp-repo-item-update", Action.execute, initialForm,
						 new AdHocCommansModule.AdHocCommansAsyncCallback() {
							 @Override
							 protected void onResponseReceived(String sessionid, String node, State status,
															   JabberDataElement _data) throws JaxmppException {
								 JabberDataElement data = new JabberDataElement(ElementFactory.create(_data));
								 ((TextSingleField) data.getField("Port number")).setFieldValue("5271");

								 getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(extJID, "comp-repo-item-update", Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

									 @Override
									 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											 throws JaxmppException {
										 mutex.notify("item:update:" + error, "item:update");
									 }

									 @Override
									 public void onTimeout() throws JaxmppException {
										 mutex.notify("item:update:timeout", "item:add");
									 }

									 @Override
									 protected void onResponseReceived(String sessionid, String node, State status,
																	   JabberDataElement data) throws JaxmppException {
										 mutex.notify("item:update:success", "item:update");
									 }
								 });
							 }

							 @Override
							 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									 throws JaxmppException {
								 mutex.notify("item:update:" + error, "item:update");
							 }

							 @Override
							 public void onTimeout() throws JaxmppException {
								 mutex.notify("item:update:timeout", "item:update");
							 }
						 });

		mutex.waitFor(20 * 1000, "item:update");
		assertTrue(mutex.isItemNotified("item:update:success"));

		testConnection(5271,true);
	}


	@Test(dependsOnMethods = {"updateConnectionAccept"})
	public void testRemoval1() throws JaxmppException, InterruptedException, IOException {
		removeAllSettings();

		testConnection(5271, false);
	}

	@Test(dependsOnMethods = {"testRemoval1"})
	public void addConnectionConnect() throws JaxmppException, InterruptedException, IOException {
		Mutex mutex = new Mutex();

		ServerSocket socket = new ServerSocket();
		socket.bind(new InetSocketAddress(5270));

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Socket s = socket.accept();
					mutex.notify("connection:established:" + (s != null));
					if (s != null) {
						s.close();
					}
				} catch (IOException ex) {}
			}
		}).start();

		JID extJID = JID.jidInstance("ext", this.getDomain());
		getJaxmppAdmin().getModule(AdHocCommansModule.class)
				.execute(extJID, "comp-repo-item-add", Action.execute, null,
						 new AdHocCommansModule.AdHocCommansAsyncCallback() {
							 @Override
							 protected void onResponseReceived(String sessionid, String node, State status,
															   JabberDataElement _data) throws JaxmppException {
								 JabberDataElement data = new JabberDataElement(ElementFactory.create(_data));
								 ((TextSingleField) data.getField("Domain name")).setFieldValue("muc-ext." + getDomain());
								 ((TextSingleField) data.getField("Domain password")).setFieldValue("muc-pass");
								 ((ListSingleField) data.getField("Connection type")).setFieldValue("connect");
								 ((TextSingleField) data.getField("Port number")).setFieldValue("5270");
								 ((TextSingleField) data.getField("Remote host")).setFieldValue(getInstanceHostname());
								 ((ListSingleField) data.getField("Protocol")).setFieldValue("accept");

								 getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(extJID, "comp-repo-item-add", Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

									 @Override
									 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											 throws JaxmppException {
										 mutex.notify("item:add:" + error, "item:add");
									 }

									 @Override
									 public void onTimeout() throws JaxmppException {
										 mutex.notify("item:add:timeout", "item:add");
									 }

									 @Override
									 protected void onResponseReceived(String sessionid, String node, State status,
																	   JabberDataElement data) throws JaxmppException {
										 mutex.notify("item:add:success", "item:add");
									 }
								 });
							 }

							 @Override
							 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									 throws JaxmppException {
								 mutex.notify("item:add:" + error, "item:add");
							 }

							 @Override
							 public void onTimeout() throws JaxmppException {
								 mutex.notify("item:add:timeout", "item:add");
							 }
						 });

		mutex.waitFor(20 * 1000, "item:add");
		assertTrue(mutex.isItemNotified("item:add:success"));

		mutex.waitFor(10 * 1000,"connection:established:true");
		assertTrue(mutex.isItemNotified("connection:established:true"));

		removeAllSettings();

		socket.close();
	}

	@Test(dependsOnMethods = {"addConnectionConnect"})
	public void testRemoval2() throws IOException, InterruptedException, JaxmppException {
		removeAllSettings();

		testConnection(5270, false);
	}


	private void testConnection(int port, boolean expectedConnected) throws InterruptedException, IOException {
		Socket socket = new Socket();
		int i = 0;
		InetSocketAddress addr = new InetSocketAddress(Inet4Address.getByName(this.getInstanceHostname()),  port);
		while (!socket.isConnected() && i<10) {
			try {
				socket.connect(addr);
			} catch (IOException ex) {
				// ignoring...
			}
			i++;
			Thread.sleep(200);
		}
		assertEquals(socket.isConnected(), expectedConnected, "Could not connect, port is not opened?");
		socket.close();
	}

	private void removeAllSettings() throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		JID extJID = JID.jidInstance("ext", this.getDomain());
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(extJID, "comp-repo-item-remove", Action.execute,
																	 null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
					@Override
					protected void onResponseReceived(String sessionid, String node, State status,
													  JabberDataElement _data) throws JaxmppException {
						JabberDataElement data = new JabberDataElement(ElementFactory.create(_data));
						ListSingleField f = data.getField("item-list");
						if (f != null) {
							Queue<String> items = new ArrayDeque<>();
							for (Element el : f.getChildren("option")) {
								String value = el.getFirstChild("value").getValue();
								if (value != null && !value.isEmpty()) {
									items.add(value);
								}
							}

							if (items.isEmpty()) {
								mutex.notify("items:cleared:success", "items:cleared");
							} else {
								new Thread(() -> { try {
									Mutex mutex1 = new Mutex();
									for (String item : items) {
										JabberDataElement form = new JabberDataElement(data);
										((ListSingleField) form.getField("item-list")).setFieldValue(item);
										String waitFor = "item:removed:" + item;
										getJaxmppAdmin().getModule(AdHocCommansModule.class)
												.execute(extJID, "comp-repo-item-remove", Action.execute, form, new AdHocCommansModule.AdHocCommansAsyncCallback() {

													@Override
													public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
															throws JaxmppException {
														mutex1.notify("item:removed:" + item + ":" + error,
																	 waitFor);
													}

													@Override
													public void onTimeout() throws JaxmppException {
														mutex1.notify("item:removed:" + item + ":timeout",
																	 waitFor);

													}

													@Override
													protected void onResponseReceived(String sessionid, String node,
																					  State status, JabberDataElement data)
															throws JaxmppException {
														mutex1.notify("item:removed:" + item + ":success",
																	 waitFor);
													}
												});
										mutex1.waitFor(20 * 1000, waitFor);
										if (!mutex1.isItemNotified("item:removed:" + item + ":success")) {
											mutex.notify("items:cleared:error", "items:cleared");
											return;
										}
									}
									mutex.notify("items:cleared:success", "items:cleared");
								} catch (InterruptedException|JaxmppException ex) {
								}
								}).start();
							}
						} else {
							mutex.notify("items:cleared:success", "items:cleared");
						}
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("items:cleared:" + error, "items:cleared");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("items:cleared:timeout", "items:cleared");
					}
				});

		mutex.waitFor(30 * 1000, "items:cleared");
		assertTrue(mutex.isItemNotified("items:cleared:success"));
	}

}
