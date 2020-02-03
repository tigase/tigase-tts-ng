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
