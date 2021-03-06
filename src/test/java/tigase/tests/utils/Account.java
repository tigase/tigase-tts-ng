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
package tigase.tests.utils;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Created by andrzej on 22.04.2017.
 */
public class Account {

	protected final AbstractTest test;
	private final ConcurrentHashMap<Object, Set<Jaxmpp>> jaxmpps = new ConcurrentHashMap<>();
	private final BareJID jid;
	private final String logPrefix;
	private final String password;
	private final List<Consumer<Account>> unregistrationHandlers;

	public Account(AbstractTest test, String logPrefix, BareJID jid, String password) {
		this(test, logPrefix, jid, password, Collections.emptyList());
	}

	public Account(AbstractTest test, String logPrefix, BareJID jid, String password,
				   List<Consumer<Account>> unregistrationHandlers) {
		this.test = test;
		this.logPrefix = logPrefix;
		this.jid = jid;
		this.password = password;
		this.unregistrationHandlers = unregistrationHandlers;
	}

	public BareJID getJid() {
		return jid;
	}

	public String getPassword() {
		return password;
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	public JaxmppBuilder createJaxmpp() {
		return new JaxmppBuilder(this);
	}

	public void unregister() throws JaxmppException, InterruptedException {
		test.accountManager.unregisterAccount(this);
	}

	@Override
	public String toString() {
		return "Account[jid=" + jid.toString() + "]";
	}

	public void registerJaxmpp(Jaxmpp jaxmpp1) {
		Object key = getScopeKey();
		jaxmpps.computeIfAbsent(key, (k) -> new CopyOnWriteArraySet<>()).add(jaxmpp1);
	}

	public void unregisterJaxmpp(Jaxmpp jaxmpp1) {
		Object key = getScopeKey();
		jaxmpps.getOrDefault(key, new HashSet<>()).remove(jaxmpp1);
	}

	protected void scopeFinished() {
		Object key = getScopeKey();
		if (key != null) {
			jaxmpps.getOrDefault(key, new HashSet<>()).forEach(jaxmpp -> {
				try {
					if (jaxmpp.isConnected()) {
						jaxmpp.disconnect(true);
					}
					unregisterJaxmpp(jaxmpp);
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			});
		}
	}

	protected void accountUnregistered() {
		unregistrationHandlers.forEach(handler -> handler.accept(this));
	}

	private Object getScopeKey() {
		Object key = test.CURRENT_METHOD.get();
		if (key == null) {
			key = test.CURRENT_CLASS.get();
			if (key == null) {
				key = test.CURRENT_SUITE.get();
			}
		}

		return key;
	}
}
