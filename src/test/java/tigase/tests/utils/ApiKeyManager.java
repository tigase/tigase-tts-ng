/*
 * ApiKeyManager.java
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

package tigase.tests.utils;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.FixedField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.ListSingleField;
import tigase.jaxmpp.core.client.xmpp.forms.TextSingleField;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.testng.Assert.assertTrue;
import static tigase.TestLogger.log;

public class ApiKeyManager extends AbstractManager {

	private final ConcurrentHashMap<Object, Set<ApiKey>> apiKeys = new ConcurrentHashMap<>();

	public ApiKeyManager(AbstractTest test) {
		super(test);
	}

	public void scopeFinished(Object key) {
		apiKeys.getOrDefault(key, new HashSet<>()).forEach(apiKey -> {
			try {
				removeApiKey(apiKey);
			} catch (JaxmppException | InterruptedException e) {
				Logger.getLogger("tigase").log(Level.WARNING, "failed to remove api key " + apiKey, e);
			}
		});
	}

	public void add(ApiKey apiKey) {
		Object key = getScopeKey();
		if (apiKeys.computeIfAbsent(key, (k) -> new CopyOnWriteArraySet<>()).add(apiKey)) {
			log("added api-key = " + apiKey.getKey());
		}
	}

	public void remove(ApiKey apiKey) {
		Object key = getScopeKey();
		if (apiKeys.getOrDefault(key, new HashSet<>()).remove(apiKey)) {
			log("removed vhost = " + apiKey.getKey());
		}
	}

	public void addApiKey(ApiKey apiKey) throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
		assertTrue(addApiKey(mutex, adminJaxmpp, apiKey));
		add(apiKey);
	}

	public void removeApiKey(ApiKey apiKey) throws JaxmppException, InterruptedException {
		Mutex mutex = new Mutex();
		Jaxmpp adminJaxmpp = test.getJaxmppAdmin();
		assertTrue(removeApiKey(mutex, adminJaxmpp, apiKey));
		remove(apiKey);
	}

	protected boolean addApiKey(Mutex mutex, Jaxmpp adminJaxmpp, ApiKey apiKey)
			throws InterruptedException, JaxmppException {
		String id = UUID.randomUUID().toString();
		AdHocCommansModule adhocModule = adminJaxmpp.getModule(AdHocCommansModule.class);
		BareJID adminJid = adminJaxmpp.getSessionObject().getUserBareJid();
		JID restModuleJid = JID.jidInstance("rest", "http." + adminJid.getDomain());
		String addApiKeyCmd = "api-key-add";
		adhocModule.execute(restModuleJid, addApiKeyCmd, null, null,
							new AdHocCommansModule.AdHocCommansAsyncCallback() {

								@Override
								public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										throws JaxmppException {
									mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd + ":error:" + error,
												 "vhost-man:" + id + ":" + addApiKeyCmd);
								}

								@Override
								public void onTimeout() throws JaxmppException {
									mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd + ":timeout",
												 "vhost-man:" + id + ":" + addApiKeyCmd);
								}

								@Override
								protected void onResponseReceived(String sessionid, String node, State status,
																  JabberDataElement data) throws JaxmppException {

									((TextSingleField) data.getField("API Key")).setFieldValue(apiKey.getKey());
									
									adminJaxmpp.getModule(AdHocCommansModule.class)
											.execute(restModuleJid, addApiKeyCmd,
													 Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

														@Override
														public void onError(Stanza responseStanza,
																			XMPPException.ErrorCondition error)
																throws JaxmppException {
															mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd + ":error:" + error,
																		 "vhost-man:" + id + ":" + addApiKeyCmd);
														}

														@Override
														public void onTimeout() throws JaxmppException {
															mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd + ":timeout",
																		 "vhost-man:" + id + ":" + addApiKeyCmd);
														}

														@Override
														protected void onResponseReceived(String sessionid, String node,
																						  State status,
																						  JabberDataElement data)
																throws JaxmppException {
															FixedField nff = data.getField("Note");
															if (nff != null) {
																mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd + ":success");
															} else {
																mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd + ":" + data.getAsString());
															}
															mutex.notify("vhost-man:" + id + ":" + addApiKeyCmd);

														}
													});
								}
							});
		mutex.waitFor(10 * 1000, "vhost-man:" + id + ":" + addApiKeyCmd);
		return mutex.isItemNotified("vhost-man:" + id + ":" + addApiKeyCmd + ":success");
	}

	protected boolean removeApiKey(Mutex mutex, Jaxmpp adminJaxmpp, ApiKey apiKey)
			throws InterruptedException, JaxmppException {
		String id = UUID.randomUUID().toString();
		AdHocCommansModule adhocModule = adminJaxmpp.getModule(AdHocCommansModule.class);
		BareJID adminJid = adminJaxmpp.getSessionObject().getUserBareJid();
		JID restModuleJid = JID.jidInstance("rest", "http." + adminJid.getDomain());
		String removeApiKeyCmd = "api-key-remove";
		adhocModule.execute(restModuleJid, removeApiKeyCmd, null, null,
							new AdHocCommansModule.AdHocCommansAsyncCallback() {

								@Override
								public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
										throws JaxmppException {
									mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd + ":error:" + error,
												 "vhost-man:" + id + ":" + removeApiKeyCmd);
								}

								@Override
								public void onTimeout() throws JaxmppException {
									mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd + ":timeout",
												 "vhost-man:" + id + ":" + removeApiKeyCmd);
								}

								@Override
								protected void onResponseReceived(String sessionid, String node, State status,
																  JabberDataElement data) throws JaxmppException {

									((ListSingleField) data.getField("item-list")).setFieldValue(apiKey.getKey());

									adminJaxmpp.getModule(AdHocCommansModule.class)
											.execute(restModuleJid, removeApiKeyCmd,
													 Action.execute, data, new AdHocCommansModule.AdHocCommansAsyncCallback() {

														@Override
														public void onError(Stanza responseStanza,
																			XMPPException.ErrorCondition error)
																throws JaxmppException {
															mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd + ":error:" + error,
																		 "vhost-man:" + id + ":" + removeApiKeyCmd);
														}

														@Override
														public void onTimeout() throws JaxmppException {
															mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd + ":timeout",
																		 "vhost-man:" + id + ":" + removeApiKeyCmd);
														}

														@Override
														protected void onResponseReceived(String sessionid, String node,
																						  State status,
																						  JabberDataElement data)
																throws JaxmppException {
															FixedField nff = data.getField("Note");
															if (nff != null) {
																mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd + ":success");
															} else {
																mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd + ":" + data.getAsString());
															}
															mutex.notify("vhost-man:" + id + ":" + removeApiKeyCmd);

														}
													});
								}
							});
		mutex.waitFor(10 * 1000, "vhost-man:" + id + ":" + removeApiKeyCmd);
		return mutex.isItemNotified("vhost-man:" + id + ":" + removeApiKeyCmd + ":success");
	}

}
