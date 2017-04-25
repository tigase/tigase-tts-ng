/*
 * ExampleJaxmppTest.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
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
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;

import java.util.logging.Level;

import static org.testng.Assert.assertTrue;
import static tigase.TestLogger.log;

public class RetrieveStatistics extends AbstractTest {

	@Test(groups = { "utils" }, description = "Retrieve server statistics")
	public void retrieveServerVersion() throws InterruptedException, JaxmppException, Exception {

		setLoggerLevel( Level.OFF, false );

		String[] instanceHostnames = getInstanceHostnames();
		if ( instanceHostnames != null & instanceHostnames.length > 0 ){
			for ( String node : instanceHostnames ) {
				log( " == " + node + " ==", false );
				retrieveStatistics( node );

			}
		}
	}

	private void retrieveStatistics(String hostname) throws JaxmppException, Exception {
		Jaxmpp adminJaxmpp = getAdminAccount().createJaxmpp().setHost(hostname).setConnected(true).build();
		assertTrue( adminJaxmpp.isConnected(), "contact was not connected" );
		if ( adminJaxmpp.isConnected() ){

			// retrieve statistics
			log( "\n\n\n" );
			log( " == Retrieve statistics" );
			IQ iq = IQ.create();
			iq.setType( StanzaType.set );
			iq.setTo( JID.jidInstance( "stats", adminJaxmpp.getSessionObject().getUserBareJid().getDomain() ) );


			JabberDataElement jde = new JabberDataElement( XDataType.submit );
			Element command = ElementFactory.create( "command", null, "http://jabber.org/protocol/commands" );
			command.setAttribute( "node", "stats" );
			jde.addListSingleField( "Stats level", "FINER" );
			command.addChild( jde );
			iq.addChild( command );

			sendAndWait( adminJaxmpp, iq, new AsyncCallback() {

				@Override
				public void onError( Stanza responseStanza, XMPPException.ErrorCondition error ) throws JaxmppException {
					log( "error" + responseStanza.getAsString() );
				}

				@Override
				public void onSuccess( Stanza responseStanza ) throws JaxmppException {
//					log( "onSuccess: " + responseStanza.getAsString() );
					log( "onSuccess" );
					Element command = responseStanza.getChildrenNS( "command", "http://jabber.org/protocol/commands" );
					if ( command != null ){
						Element x = command.getChildrenNS( "x", "jabber:x:data" );

//						log( "onSuccess, x: " + x );
						if ( x != null ){
							JabberDataElement jde2 = new JabberDataElement( x );
							log( "" );
							if ( jde2 != null && jde2.getFields() != null && !jde2.getFields().isEmpty() ){
								for ( AbstractField field : jde2.getFields() ) {
									log( field.getVar() + ": " + field.getFieldValue() );
								}
							}
							log( "" );
						}
					}
				}

				@Override
				public void onTimeout() throws JaxmppException {
					log( "onTimeout" );
				}
			} );

			adminJaxmpp.disconnect();
		}
	}

}
