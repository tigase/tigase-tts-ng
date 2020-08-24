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
package tigase.tests.mix;

import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.List;

public final class ConnectionsProvider {

	private final List<UserDetails> connections = new ArrayList<>();

	public Object[][] getConnections() {
		Object[][] result = new Object[connections.size()][];
		for (int i = 0; i < connections.size(); i++) {
			result[i] = new Object[]{connections.get(i)};
		}
		return result;
	}

	public void add(Account user, Jaxmpp jaxmpp, JID mixJID) {
		this.connections.add(new UserDetails(user, jaxmpp, mixJID));
	}

	public static class UserDetails {

		public final Account user;
		public final Jaxmpp jaxmpp;
		public final JID mixJID;

		UserDetails(Account user, Jaxmpp jaxmpp, JID mixJID) {
			this.user = user;
			this.jaxmpp = jaxmpp;
			this.mixJID = mixJID;
		}

		@Override
		public String toString() {
			return user.getLogPrefix();
		}
	}
}
