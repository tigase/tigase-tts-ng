/*
 * Account.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests.utils;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by andrzej on 22.04.2017.
 */
public class Account {

	protected final AbstractTest test;
	private final ConcurrentHashMap<Object, Set<Jaxmpp>> jaxmpps = new ConcurrentHashMap<>();
	private final BareJID jid;
	private final String logPrefix;
	private final String password;

	public Account(AbstractTest test, String logPrefix, BareJID jid, String password) {
		this.test = test;
		this.logPrefix = logPrefix;
		this.jid = jid;
		this.password = password;
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
