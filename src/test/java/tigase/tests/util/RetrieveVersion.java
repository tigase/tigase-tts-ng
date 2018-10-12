/*
 * RetrieveVersion.java
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
package tigase.tests.util;

import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.forms.AbstractField;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.TextMultiField;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;

import java.util.logging.Level;

import static org.testng.Assert.assertTrue;
import static tigase.TestLogger.log;

public class RetrieveVersion
		extends AbstractTest {

	@Test(groups = {"utils"}, description = "Retrieve server version")
	public void retrieveServerVersion() {

		setLoggerLevel(Level.OFF, false);
		forEachClusterNode(this::retrieveVersion);
	}

	// Unavailable on 8.0.0 and above
	@Test(groups = {"utils"}, description = "Retrieve server components", enabled = false)
	public void retrieveComponents() {

		setLoggerLevel(Level.OFF, false);
		forEachClusterNode(this::retrieveComponents);
	}

	// Unavailable on 8.0.0 and above
	@Test(groups = {"utils"}, description = "Retrieve server configuration", enabled = false)
	public void retrieveServerConfiguration() {

		setLoggerLevel(Level.OFF, false);
		forEachClusterNode(this::retrieveConfiguration);
	}


	private void forEachClusterNode(Consumer<Jaxmpp> function) {
		String[] instanceHostnames = getInstanceHostnames();
		if (instanceHostnames != null & instanceHostnames.length > 0) {
			for (String node : instanceHostnames) {
				log(" == " + node + " ==", false);
				try {
					Jaxmpp jaxmpp = getAdminJaxmppForClusterNode(node);
					function.accept(jaxmpp);
					jaxmpp.disconnect();
				} catch (Exception ex) {
					log(" == " + node + " == - failure = " + ex.getMessage());
				}
			}
		}
	}

	private void retrieveVersion(Jaxmpp adminJaxmpp) throws Exception, JaxmppException {
		// version
		log(" == Retrieve version", false);
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setTo(JID.jidInstance(adminJaxmpp.getSessionObject().getUserBareJid().getDomain()));
		iq.addChild(ElementFactory.create("query", null, "jabber:iq:version"));

		sendAndWait(adminJaxmpp, iq, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				log("error" + responseStanza.getAsString(), false);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
				final Element query = responseStanza.getChildrenNS("query", "jabber:iq:version");
//					log( "onSuccess: " + responseStanza.getAsString() );
				log("onSuccess", false);
				log("", false);
				if (query != null && query.getChildren() != null && !query.getChildren().isEmpty()) {
					for (Element child : query.getChildren()) {
						log(child.getName() + ": " + child.getValue(), false);
					}
				}
				log("", false);
			}

			@Override
			public void onTimeout() throws JaxmppException {
				log("onTimeout");
			}
		});
	}

	private void retrieveConfiguration(Jaxmpp adminJaxmpp) throws Exception {
		// configuration
		log(" == Retrieve configuration", false);
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setTo(JID.jidInstance("message-router", adminJaxmpp.getSessionObject().getUserBareJid().getDomain()));

		JabberDataElement jde = new JabberDataElement(XDataType.submit);
		Element command = ElementFactory.create("command", null, "http://jabber.org/protocol/commands");
		command.setAttribute("node", "config-list");
		jde.addListSingleField("comp-name", "sess-man");
		command.addChild(jde);
		iq.addChild(command);

		sendAndWait(adminJaxmpp, iq, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				log("error" + responseStanza.getAsString(), false);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
//					log( "onSuccess: " + responseStanza.getAsString() );
				log("onSuccess", false);
				Element command = responseStanza.getChildrenNS("command", "http://jabber.org/protocol/commands");
				if (command != null) {
					Element x = command.getChildrenNS("x", "jabber:x:data");

//						log( "onSuccess, x: " + x );
					if (x != null) {
						JabberDataElement jde2 = new JabberDataElement(x);
						log("", false);
						if (jde2 != null && jde2.getFields() != null && !jde2.getFields().isEmpty()) {
							for (AbstractField field : jde2.getFields()) {
								log(field.getVar() + ": " + field.getFieldValue(), false);
							}
						}
						log("", false);
					}
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				log("onTimeout");
			}
		});
	}

	private void retrieveComponents(Jaxmpp adminJaxmpp) throws Exception {
		// components
		log(" == Retrieve components", false);
		IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.setTo(JID.jidInstance(adminJaxmpp.getSessionObject().getUserBareJid().getDomain()));

		JabberDataElement jde = new JabberDataElement(XDataType.submit);
		Element command = ElementFactory.create("command", null, "http://jabber.org/protocol/commands");
		command.setAttribute("node", "comp-manager");
		jde.addListSingleField("action", "List");
		command.addChild(jde);
		iq.addChild(command);

		sendAndWait(adminJaxmpp, iq, new AsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				log("error" + responseStanza.getAsString(), false);
			}

			@Override
			public void onSuccess(Stanza responseStanza) throws JaxmppException {
//					log( "onSuccess: " + responseStanza.getAsString() );
				log("onSuccess", false);
				Element command = responseStanza.getChildrenNS("command", "http://jabber.org/protocol/commands");
				if (command != null) {
					Element x = command.getChildrenNS("x", "jabber:x:data");

//						log( "onSuccess, x: " + x );
					if (x != null) {
						JabberDataElement jde2 = new JabberDataElement(x);
						log("", false);
						if (jde2 != null && jde2.getFields() != null && !jde2.getFields().isEmpty()) {
							for (AbstractField field : jde2.getFields()) {
								if (field instanceof TextMultiField) {
									log(field.getVar() + ": " + ((TextMultiField) field).getFieldValue()[0], false);
								}
							}
						}
						log("", false);
					}
				}
			}

			@Override
			public void onTimeout() throws JaxmppException {
				log("onTimeout", false);
			}
		});

		adminJaxmpp.disconnect();

		log("\n\n\n");

	}

	interface Consumer<T> {
		void accept(T t) throws Exception;
	}

	private Jaxmpp getAdminJaxmppForClusterNode(String hostname) throws JaxmppException {
		Jaxmpp adminJaxmpp = getAdminAccount().createJaxmpp().setHost(hostname).setConnected(true).build();
		assertTrue(adminJaxmpp.isConnected(), "contact was not connected");
		return adminJaxmpp;
	}
}
