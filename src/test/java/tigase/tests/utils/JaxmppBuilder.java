/*
 * JaxmppBuilder.java
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

import tigase.TestLogger;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.stanzas.StreamPacket;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

import java.util.function.Function;

/**
 * Created by andrzej on 22.04.2017.
 */
public class JaxmppBuilder {

	private final Account account;
	private Function<Jaxmpp, Jaxmpp> configurator;
	private boolean connected = false;
	private String resource;
	private String host;
	private String logPrefix;

	JaxmppBuilder(Account account) {
		this.account = account;
	}

	public Jaxmpp build() throws JaxmppException {
		Jaxmpp jaxmpp1 = account.test.accountManager.createJaxmpp(
				logPrefix == null ? account.getLogPrefix() : logPrefix);
		jaxmpp1.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		jaxmpp1.getEventBus().addHandler(SocketConnector.StanzaReceivedHandler.StanzaReceivedEvent.class, new Connector.StanzaReceivedHandler() {
			@Override
			public void onStanzaReceived(SessionObject sessionObject, StreamPacket stanza) {
				try {
					TestLogger.log(" >> "+stanza.getAsString());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}
		});
		jaxmpp1.getEventBus().addHandler(SocketConnector.StanzaSendingHandler.StanzaSendingEvent.class, new Connector.StanzaSendingHandler() {
			@Override
			public void onStanzaSending(SessionObject sessionObject, Element stanza) throws JaxmppException {
				try {
					TestLogger.log(" << "+stanza.getAsString());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}

		});
		if (null == host) {
			String instanceHostname = account.test.getInstanceHostname();
			if (instanceHostname != null) {
				jaxmpp1.getConnectionConfiguration().setServer(instanceHostname);
			}
		} else {
			jaxmpp1.getConnectionConfiguration().setServer(host);
		}

		if (account.getJid() != null) {
			jaxmpp1.getConnectionConfiguration().setUserJID(account.getJid());
		}
		if (account.getPassword() != null) {
			jaxmpp1.getConnectionConfiguration().setUserPassword(account.getPassword());
		}
		if (resource != null) {
			jaxmpp1.getConnectionConfiguration().setResource(resource);
		}
//			if (domain != null)
//				jaxmpp1.getConnectionConfiguration().setDomain(account.);

		if (configurator != null) {
			jaxmpp1 = configurator.apply(jaxmpp1);
		}

		account.registerJaxmpp(jaxmpp1);

		if (connected) {
			jaxmpp1.login(true);
			assert (jaxmpp1.isConnected());
		}

		return jaxmpp1;
	}

	public String getHost() {
		return this.host;
	}

	public JaxmppBuilder setHost(String host) {
		this.host = host;
		return this;
	}

	public JaxmppBuilder setLogPrefix(String logPrefix) {
		this.logPrefix = logPrefix;
		return this;
	}

	public JaxmppBuilder setConnected(boolean connected) {
		this.connected = connected;
		return this;
	}

	public JaxmppBuilder setConfigurator(Function<Jaxmpp, Jaxmpp> configurator) {
		this.configurator = configurator;
		return this;
	}

	public JaxmppBuilder setResource(String resource) {
		this.resource = resource;
		return this;
	}
}
