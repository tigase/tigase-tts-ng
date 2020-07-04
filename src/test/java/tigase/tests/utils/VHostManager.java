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
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.*;
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
public class VHostManager extends AbstractManager {

	private final ConcurrentHashMap<Object, Set<String>> vhosts = new ConcurrentHashMap<>();

	public VHostManager(AbstractTest test) {
		super(test);
	}

	public String addVHost(final String prefix) throws JaxmppException, InterruptedException {
		final String VHost = prefix + "_" + nextRnd().toLowerCase() + "." + test.getDomain();
		addVHost(VHost, Collections.emptyMap(), false);
		return VHost;
	}

	public void removeVHost(String VHost) throws JaxmppException, InterruptedException {
		Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
		final String removeVHostCommand = "comp-repo-item-remove";

		final Mutex mutex = new Mutex();

		final BareJID userBareJid = adminJaxmpp.getSessionObject().getUserBareJid();

		log("Removing Vhost: " + vhosts + " using admin: " + userBareJid);

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
			log("added vhost = " + vhost);
		}
	}

	public void remove(String vhost) {
		Object key = getScopeKey();
		if (vhosts.getOrDefault(key, new HashSet<>()).remove(vhost)) {
			log("removed vhost = " + vhost);
		}
	}

	protected void scopeFinished(Object key) {
		vhosts.getOrDefault(key, new HashSet<>()).forEach(account -> {
			try {
				removeVHost(account);
			} catch (JaxmppException | InterruptedException e) {
				log("failed to remove account " + account + ", exception: " + e);
			}
		});
	}

	protected void addVHost(String domain, Map<String, Object> parameters, boolean updateIfExists) throws JaxmppException,
																				  InterruptedException {
		Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
		final Mutex mutex = new Mutex();

		boolean result = addVHost(mutex, adminJaxmpp, domain, parameters);
		if (updateIfExists) {
			assertTrue(updateVHost(mutex, adminJaxmpp, domain, parameters), "VHost updated failed.");
		} else {
			assertTrue(result, "VHost adding failed.");
			add(domain);
		}
	}

	private boolean addVHost(Mutex mutex, Jaxmpp adminJaxmpp, String domain, Map<String, Object> parameters)
			throws InterruptedException, JaxmppException {
		final String id = UUID.randomUUID().toString();
		final String addVHostCommand = "comp-repo-item-add";
		final BareJID adminJID = adminJaxmpp.getSessionObject().getUserBareJid();
		final JID vhostManJid = JID.jidInstance("vhost-man", adminJID.getDomain());

		log("Adding Vhost: " + domain + " using admin: " + adminJID + ", id: " + id);

		AdHocCommansModule adhocModule = adminJaxmpp.getModule(AdHocCommansModule.class);
		adhocModule.execute(vhostManJid, addVHostCommand, null, null,
						 new AdHocCommansModule.AdHocCommansAsyncCallback() {

							 @Override
							 public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									 throws JaxmppException {
								 mutex.notify("vhost-man:" + id + ":" + addVHostCommand + ":error:" + error,
											  "vhost-man:" + id + ":" + addVHostCommand);
							 }

							 @Override
							 public void onTimeout() throws JaxmppException {
								 mutex.notify("vhost-man:" + id + ":" + addVHostCommand + ":timeout",
											  "vhost-man:" + id + ":" + addVHostCommand);
							 }

							 @Override
							 protected void onResponseReceived(String sessionid, String node, State status,
															   JabberDataElement data) throws JaxmppException {
								 ((TextSingleField) data.getField("Domain name")).setFieldValue(domain);

								 if (fillCommandForm(data, parameters, mutex, id, addVHostCommand)) {
									 return;
								 }

								 adminJaxmpp.getModule(AdHocCommansModule.class)
										 .execute(JID.jidInstance("vhost-man", adminJID.getDomain()), addVHostCommand,
												  Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

													 @Override
													 public void onError(Stanza responseStanza,
																		 XMPPException.ErrorCondition error)
															 throws JaxmppException {
														 mutex.notify("vhost-man:" + id + ":" + addVHostCommand + ":error:" + error,
																	  "vhost-man:" + id + ":" + addVHostCommand);
													 }

													 @Override
													 public void onTimeout() throws JaxmppException {
														 mutex.notify("vhost-man:" + id + ":" + addVHostCommand + ":timeout",
																	  "vhost-man:" + id + ":" + addVHostCommand);
													 }

													 @Override
													 protected void onResponseReceived(String sessionid, String node,
																					   State status,
																					   JabberDataElement data)
															 throws JaxmppException {
														 FixedField nff = data.getField("Note");
														 if (nff != null) {
															 mutex.notify("vhost-man:" + id + ":" + addVHostCommand + ":success");
														 } else {
															 mutex.notify("vhost-man:" + id + ":" + addVHostCommand + ":" + data.getAsString());
														 }
														 mutex.notify("vhost-man:" + id + ":" + addVHostCommand);

													 }
												 });
							 }
						 });
		mutex.waitFor(10 * 1000, "vhost-man:" + id + ":" + addVHostCommand);
		return mutex.isItemNotified("vhost-man:" + id + ":" + addVHostCommand + ":success");
	}

	private boolean fillCommandForm(JabberDataElement data, Map<String, Object> parameters, Mutex mutex, String id,
									String vhostCommand) throws tigase.jaxmpp.core.client.xml.XMLException {
		for (Map.Entry<String, Object> e : parameters.entrySet()) {
			Field f = data.getField(e.getKey());
			if (f == null) {
				mutex.notify("vhost-man:" + id + ":" + vhostCommand + ":missing-field:" +
									 e.getKey(), "vhost-man:" + id + ":" + vhostCommand);
				return true;
			}
			f.setFieldValue(e.getValue());
		}
		return false;
	}

	private boolean updateVHost(Mutex mutex, Jaxmpp adminJaxmpp, String domain, Map<String, Object> parameters)
			throws JaxmppException, InterruptedException {
		final String id = UUID.randomUUID().toString();
		final String updateVHostCommand = "comp-repo-item-update";
		final BareJID adminJID = adminJaxmpp.getSessionObject().getUserBareJid();
		final JID vhostManJid = JID.jidInstance("vhost-man", adminJID.getDomain());

		AdHocCommansModule adhocModule = adminJaxmpp.getModule(AdHocCommansModule.class);
		adhocModule.execute(
				vhostManJid, updateVHostCommand, null, null, new AdHocCommansModule.AdHocCommansAsyncCallback() {
					@Override
					protected void onResponseReceived(String sessionid, String node,
													  State status, JabberDataElement data)
							throws JaxmppException {
						((ListSingleField) data.getField("item-list")).setFieldValue(domain);

						adhocModule.execute(vhostManJid, updateVHostCommand, Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {
							@Override
							protected void onResponseReceived(String sessionid, String node, State status,
															  JabberDataElement data) throws JaxmppException {

								if (fillCommandForm(data, parameters, mutex, id, updateVHostCommand)) {
									return;
								}

								adhocModule.execute(vhostManJid, updateVHostCommand, Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {
									@Override
									protected void onResponseReceived(String sessionid, String node, State status,
																	  JabberDataElement data) throws JaxmppException {
										FixedField nff = data.getField("Note");
										if (nff != null) {
											mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":success");
										} else {
											mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":" + data.getAsString());
										}
										mutex.notify("vhost-man:" + id + ":" + updateVHostCommand);
									}

									@Override
									public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											throws JaxmppException {
										mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":error:" + error,
													 "vhost-man:" + id + ":" + updateVHostCommand);
									}

									@Override
									public void onTimeout() throws JaxmppException {
										mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":timeout",
													 "vhost-man:" + id + ":" + updateVHostCommand);
									}
								});
							}

							@Override
							public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
									throws JaxmppException {
								mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":error:" + error,
											 "vhost-man:" + id + ":" + updateVHostCommand);
							}

							@Override
							public void onTimeout() throws JaxmppException {
								mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":timeout",
											 "vhost-man:" + id + ":" + updateVHostCommand);
							}
						});
					}

					@Override
					public void onError(Stanza responseStanza,
										XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":error:" + error,
									 "vhost-man:" + id + ":" + updateVHostCommand);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("vhost-man:" + id + ":" + updateVHostCommand + ":timeout",
									 "vhost-man:" + id + ":" + updateVHostCommand);
					}
				});

		mutex.waitFor(10 * 1000, "vhost-man:" + id + ":" + updateVHostCommand);
		return mutex.isItemNotified("vhost-man:" + id + ":" + updateVHostCommand + ":success");
	}
	
}
