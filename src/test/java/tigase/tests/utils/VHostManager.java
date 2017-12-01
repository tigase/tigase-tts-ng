/*
 * VHostManager.java
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
package tigase.tests.utils;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.*;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.testng.Assert.assertTrue;
import static tigase.TestLogger.log;
import static tigase.tests.AbstractTest.nextRnd;

/**
 * Created by andrzej on 22.04.2017.
 */
public class VHostManager {

	private final AbstractTest test;
	private final ConcurrentHashMap<Object, Set<String>> vhosts = new ConcurrentHashMap<>();

	public VHostManager(AbstractTest test) {
		this.test = test;
	}

	public String addVHost(final String prefix) throws JaxmppException, InterruptedException {
		Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
		final String addVHostCommand = "comp-repo-item-add";
		final String VHost = prefix + "_" + nextRnd().toLowerCase() + "." + test.getDomain();
		final String mutexCommand = addVHostCommand + "-" + VHost;

		final Mutex mutex = new Mutex();

		final BareJID adminJID = adminJaxmpp.getSessionObject().getUserBareJid();

		log("jaxmppa: " + adminJaxmpp.getSessionObject().getUserBareJid());
		adminJaxmpp.getModule(AdHocCommansModule.class)
				.execute(JID.jidInstance("vhost-man", adminJID.getDomain()), addVHostCommand, null, null,
						 new AdHocCommansModule.AdHocCommansAsyncCallback() {

							 @Override
							 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									 throws JaxmppException {
								 mutex.notify("1:" + mutexCommand, "1:error");
							 }

							 @Override
							 public void onTimeout() throws JaxmppException {
								 mutex.notify("1:" + mutexCommand, "1:timeout");

							 }

							 @Override
							 protected void onResponseReceived(String sessionid, String node, State status,
															   JabberDataElement data) throws JaxmppException {
								 mutex.notify("1:" + mutexCommand, "1:success");

								 ((TextSingleField) data.getField("Domain name")).setFieldValue(VHost);

								 data.setAttribute("type", "submit");

								 adminJaxmpp.getModule(AdHocCommansModule.class)
										 .execute(JID.jidInstance("vhost-man", adminJID.getDomain()), addVHostCommand,
												  null, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

													 @Override
													 public void onError(Stanza responseStanza,
																		 XMPPException.ErrorCondition error)
															 throws JaxmppException {
														 mutex.notify("2:" + mutexCommand, "2:error");
													 }

													 @Override
													 public void onTimeout() throws JaxmppException {
														 mutex.notify("2:" + mutexCommand, "2:timeout");
													 }

													 @Override
													 protected void onResponseReceived(String sessionid, String node,
																					   State status,
																					   JabberDataElement data)
															 throws JaxmppException {
														 FixedField nff = data.getField("Note");
														 if (nff != null) {
															 mutex.notify(mutexCommand + ":success");
														 }
														 mutex.notify("2:" + mutexCommand);

													 }
												 });
							 }
						 });
		mutex.waitFor(10 * 1000, "1:" + mutexCommand, "2:" + mutexCommand);
		assertTrue(mutex.isItemNotified(addVHostCommand + "-" + VHost + ":success"), "VHost adding failed.");

		add(VHost);

		return VHost;
	}

	public void removeVHost(String VHost) throws JaxmppException, InterruptedException {
		Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
		final String removeVHostCommand = "comp-repo-item-remove";

		final Mutex mutex = new Mutex();

		final BareJID userBareJid = adminJaxmpp.getSessionObject().getUserBareJid();
		adminJaxmpp.getModule(AdHocCommansModule.class)
				.execute(JID.jidInstance("vhost-man", userBareJid.getDomain()), removeVHostCommand, null, null,
						 new AdHocCommansModule.AdHocCommansAsyncCallback() {

							 @Override
							 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									 throws JaxmppException {
							 }

							 @Override
							 public void onTimeout() throws JaxmppException {
							 }

							 @Override
							 protected void onResponseReceived(String sessionid, String node, State status,
															   JabberDataElement data) throws JaxmppException {

								 ListSingleField ff = ((ListSingleField) data.getField("item-list"));

								 ff.clearOptions();
								 ff.setFieldValue(VHost);

								 JabberDataElement r = new JabberDataElement(
										 data.createSubmitableElement(XDataType.submit));
								 adminJaxmpp.getModule(AdHocCommansModule.class)
										 .execute(JID.jidInstance("vhost-man", userBareJid.getDomain()),
												  removeVHostCommand, null, r,
												  new AdHocCommansModule.AdHocCommansAsyncCallback() {

													  @Override
													  public void onError(Stanza responseStanza,
																		  XMPPException.ErrorCondition error)
															  throws JaxmppException {
													  }

													  @Override
													  public void onTimeout() throws JaxmppException {
													  }

													  @Override
													  protected void onResponseReceived(String sessionid, String node,
																						State status,
																						JabberDataElement data)
															  throws JaxmppException {
														  FixedField nff = data.getField("Note");
														  if (nff != null) {
															  mutex.notify(
																	  "remove:" + VHost + ":" + nff.getFieldValue());
														  }
														  mutex.notify("domainRemoved:" + VHost);

													  }
												  });
							 }
						 });
		mutex.waitFor(10 * 1000, "domainRemoved:" + VHost);

		assertTrue(mutex.isItemNotified("remove:" + VHost + ":Operation successful"), "VHost removal failed.");
		remove(VHost);
	}

	public void add(String vhost) {
		Object key = getScopeKey();
		if (vhosts.computeIfAbsent(key, (k) -> new CopyOnWriteArraySet<>()).add(vhost)) {
			System.out.println("added vhost = " + vhost);
		}
	}

	public void remove(String vhost) {
		Object key = getScopeKey();
		if (vhosts.getOrDefault(key, new HashSet<>()).remove(vhost)) {
			System.out.println("removed vhost = " + vhost);
		}
	}

	public void scopeFinished() {
		Object key = getScopeKey();
		vhosts.getOrDefault(key, new HashSet<>()).forEach(account -> {
			try {
				removeVHost(account);
			} catch (JaxmppException | InterruptedException e) {
				Logger.getLogger("tigase").log(Level.WARNING, "failed to remove account " + account, e);
			}
		});
	}

	private Object getScopeKey() {
		Object key;
		key = test.CURRENT_METHOD.get();
		if (key == null) {
			key = test.CURRENT_CLASS.get();
			if (key == null) {
				key = test.CURRENT_SUITE.get();
			}
		}

		return key;
	}

}
