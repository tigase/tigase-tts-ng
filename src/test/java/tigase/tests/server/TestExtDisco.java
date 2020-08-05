/*
 * TestServiceDiscoveryExtensions.java
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
package tigase.tests.server;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.testng.Assert.assertTrue;

public class TestExtDisco extends AbstractTest {

	protected Account account;
	protected Jaxmpp jaxmpp;

	protected String turnServiceKey;
	protected String turnServerHostname;
	protected String turnServerPort;
	protected String turnServerType = "turn";
	protected String turnServerTransport = "udp";
	protected String turnUsername;
	protected String turnPassword;

	@BeforeClass
	public void setUp() throws Exception {
		Mutex mutex = new Mutex();
		account = createAccount().setLogPrefix("ext-disco").setRegister(true).build();
		jaxmpp = account.createJaxmpp().setConfigurator(jaxmpp1 -> {
			jaxmpp1.getModulesManager().register(new DiscoveryModule());
			return jaxmpp1;
		}).setConnected(true).build();
		turnServiceKey = UUID.randomUUID().toString();
		turnServerPort = String.valueOf(ThreadLocalRandom.current().nextInt(1, 65535));
		turnServerHostname = "turn." + account.getJid().getDomain();
		turnUsername = UUID.randomUUID().toString();
		turnPassword = UUID.randomUUID().toString();
	}

	@Test
	public void testSupportAdvertisement() throws Exception {
		final Mutex mutex = new Mutex();
		jaxmpp.getModule(DiscoveryModule.class).discoverServerFeatures(new DiscoveryModule.DiscoInfoAsyncCallback(null) {

			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
					throws JaxmppException {
				mutex.notify("discovery:completed:error", "discovery:completed");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("discovery:completed:timeout", "discovery:completed");
			}

			@Override
			protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
										  Collection<String> features) throws XMLException {
				if (identities != null) {
					identities.forEach(identity -> mutex.notify("discovery:identity:" + identity.getCategory() + ":" + identity.getType()));
				}
				if (features != null) {
					features.forEach(feature -> mutex.notify("discovery:feature:" + feature));
				}
				mutex.notify("discovery:completed:success", "discovery:completed");
			}
		});
		mutex.waitFor(3 * 1000, "discovery:completed");
		assertTrue(mutex.isItemNotified("discovery:feature:urn:xmpp:extdisco:2"));
	}

	@Test(dependsOnMethods = {"testSupportAdvertisement"})
	public void addExtDiscoEntry() throws Exception {
		final Mutex mutex = new Mutex();
		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addHiddenField("command-marker", "command-marker");
		form.addTextSingleField("Service", turnServiceKey);
		form.addTextSingleField("Service name", "TURN server");
		form.addTextSingleField("Host", turnServerHostname);
		form.addTextSingleField("Port", turnServerPort);
		form.addTextSingleField("Type", turnServerType);
		form.addTextSingleField("Transport", turnServerTransport);
		form.addBooleanField("Requires username and password", false);
		form.addTextSingleField("Username", turnUsername);
		form.addTextSingleField("Password", turnPassword);
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(JID.jidInstance("ext-disco", account.getJid().getDomain()), "comp-repo-item-add", Action.execute, form,
																	 new AdHocCommansModule.AdHocCommansAsyncCallback() {
																		 @Override
																		 protected void onResponseReceived(String s,
																										   String s1,
																										   State state,
																										   JabberDataElement jabberDataElement)
																				 throws JaxmppException {
																			 mutex.notify("item:add:success", "item:added");
																		 }

																		 @Override
																		 public void onError(Stanza stanza,
																							 XMPPException.ErrorCondition errorCondition)
																				 throws JaxmppException {
																			 mutex.notify("item:add:error:" + errorCondition, "item:added");
																		 }

																		 @Override
																		 public void onTimeout()
																				 throws JaxmppException {
																			 mutex.notify("item:add:timeout", "item:added");
																		 }
																	 });
		mutex.waitFor(3 * 1000, "item:added");
		assertTrue(mutex.isItemNotified("item:add:success"));
	}

	@Test(dependsOnMethods = {"addExtDiscoEntry"})
	public void discoExternalServices() throws Exception {
		final Mutex mutex = new Mutex();
		IQ iq = IQ.create();
		iq.setAttribute("to", account.getJid().getDomain());
		iq.setAttribute("type", "get");
		iq.setAttribute("id", UUID.randomUUID().toString());
		iq.addChild(ElementFactory.create("services", null, "urn:xmpp:extdisco:2"));
		jaxmpp.send(iq, new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("extdisco:error:" + errorCondition, "extdisco:done");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {
				Element services = stanza.getChildrenNS("services", "urn:xmpp:extdisco:2");
				if (services != null) {
					List<Element> children = services.getChildren();
					if (children != null) {
						for (Element service : children) {
							if (service.getName().equals("service")) {
								mutex.notify("extdisco:" + service.getAttribute("host") + ":" +
													 service.getAttribute("port") +
													 ":" + service.getAttribute("type") + ":" +
													 service.getAttribute("transport") + ":" +
													 service.getAttribute("username") + ":" +
													 service.getAttribute("password"));
							}
						}
					}
				}
				mutex.notify("extdisco:success", "extdisco:done");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("extdisco:timeout", "extdisco:done");
			}
		});
		mutex.waitFor(3 * 1000, "extdisco:done");
		assertTrue(mutex.isItemNotified("extdisco:" + turnServerHostname + ":" + turnServerPort + ":" + turnServerType + ":" + turnServerTransport + ":" + turnUsername + ":" + turnPassword));
	}

	@Test(dependsOnMethods = {"discoExternalServices"})
	public void removeExtDiscoEntry() throws Exception {
		final Mutex mutex = new Mutex();
		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addHiddenField("command-marker", "command-marker");
		form.addListSingleField("item-list", turnServiceKey);
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(JID.jidInstance("ext-disco", account.getJid().getDomain()), "comp-repo-item-remove", Action.execute, form,
																	 new AdHocCommansModule.AdHocCommansAsyncCallback() {
																		 @Override
																		 protected void onResponseReceived(String s,
																										   String s1,
																										   State state,
																										   JabberDataElement jabberDataElement)
																				 throws JaxmppException {
																			 mutex.notify("item:remove:success", "item:removed");
																		 }

																		 @Override
																		 public void onError(Stanza stanza,
																							 XMPPException.ErrorCondition errorCondition)
																				 throws JaxmppException {
																			 mutex.notify("item:remove:error:" + errorCondition, "item:removed");
																		 }

																		 @Override
																		 public void onTimeout()
																				 throws JaxmppException {
																			 mutex.notify("item:remove:timeout", "item:removed");
																		 }
																	 });
		mutex.waitFor(3 * 1000, "item:removed");
		assertTrue(mutex.isItemNotified("item:remove:success"));
	}

}
