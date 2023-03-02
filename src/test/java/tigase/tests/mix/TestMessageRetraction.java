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

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.mam.MessageArchiveManagementModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.core.client.xmpp.utils.RSM;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.JaxmppHostnameVerifier;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static tigase.jaxmpp.j2se.connectors.socket.SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY;
import static tigase.jaxmpp.j2se.connectors.socket.SocketConnector.HOSTNAME_VERIFIER_KEY;

public class TestMessageRetraction extends AbstractTest {

	private Jaxmpp jaxmpp;
	private JID mixJID;
	private String channelName;

	@BeforeClass
	public void setUp() throws Exception {
		JaxmppHostnameVerifier hostnameVerifier = new JaxmppHostnameVerifier() {
			@Override
			public boolean verify(String hostname, Certificate certificate) {
				return true;
			}
		};

		this.jaxmpp = getAdminAccount().createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new MessageArchiveManagementModule());
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_DISABLED_KEY, true);
			jaxmpp.getSessionObject().setUserProperty(HOSTNAME_VERIFIER_KEY, hostnameVerifier);
			return jaxmpp;
		}).setConnected(true).build();
		this.mixJID = TestDiscovery.findMIXComponent(jaxmpp);

		this.channelName = "channel-" + nextRnd();
	}

	@Test
	public void testMessageRetraction() throws Exception {
		Date start = new Date();
		createChannel();
		try {
			Response result = sendRequest(jaxmpp, createJoinRequest(jaxmpp, "user"));
			AssertJUnit.assertTrue("User3 cannot join to Channel", result instanceof Response.Success);

			final Mutex mutex = new Mutex();
			final String msg = "msg-" + nextRnd();

			List<Message> receivedMessages = new ArrayList<>();
			JID channelJid = JID.jidInstance(channelName, mixJID.getDomain());
			AtomicBoolean processMessages = new AtomicBoolean(true);

			jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, (sessionObject, streamPacket) -> {
				try {
					if (!processMessages.get()) {
						return;
					}
					if (streamPacket instanceof Message &&
							streamPacket.getAttribute("from").startsWith(channelJid.toString())) {
						System.out.println(streamPacket);
						if (((Message) streamPacket).getType() == StanzaType.groupchat) {
							receivedMessages.add((Message) streamPacket);
							mutex.notify("received:message:" + receivedMessages.size());
						}
					}
				} catch (XMLException e) {
					throw new RuntimeException(e);
				}
			});

			ElementBuilder msgBuilder = ElementBuilder.create("message")
					.setAttribute("type", "groupchat")
					.setAttribute("id", "msg-1")
					.setAttribute("to", channelName + "@" + mixJID)
					.child("body")
					.setValue(msg);

			jaxmpp.send(Stanza.create(msgBuilder.getElement()));

			mutex.waitFor(2000, "received:message:1");

			String idToModerate = receivedMessages.get(0).getAttribute("id");
			AssertJUnit.assertNotNull(idToModerate);
			msgBuilder = ElementBuilder.create("message")
					.setAttribute("type", "groupchat")
					.setAttribute("id", "msg-2")
					.setAttribute("to", channelName + "@" + mixJID)
					.child("retract").setXMLNS("urn:xmpp:mix:misc:0").setAttribute("id", idToModerate);
			jaxmpp.send(Stanza.create(msgBuilder.getElement()));

			mutex.waitFor(2000, "received:message:2");

			Element receivedRetract = receivedMessages.get(1).getChildrenNS("retract", "urn:xmpp:mix:misc:0");
			AssertJUnit.assertNotNull(receivedRetract);
			AssertJUnit.assertEquals(idToModerate, receivedRetract.getAttribute("id"));

			MessageArchiveManagementModule.Query query = new MessageArchiveManagementModule.Query();
			//query.setStart(start);
			String queryId = UUID.randomUUID().toString();
			processMessages.set(false);
			List<Message> mamMessages = new ArrayList<>();
			jaxmpp.getEventBus().addHandler(MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler.MessageArchiveItemReceivedEvent.class,
											new MessageArchiveManagementModule.MessageArchiveItemReceivedEventHandler() {
												@Override
												public void onArchiveItemReceived(SessionObject sessionObject, String s,
																				  String s1, Date date, Message message)
														throws JaxmppException {
													mamMessages.add(message);
												}
											});
			jaxmpp.getModule(MessageArchiveManagementModule.class).queryItems(query, channelJid, queryId, new RSM(100),
																			  new MessageArchiveManagementModule.ResultCallback() {
																				  @Override
																				  public void onError(Stanza stanza,
																									  XMPPException.ErrorCondition errorCondition)
																						  throws JaxmppException {
																					  mutex.notify("mam:fetch:error:" + errorCondition.name());
																					  mutex.notify("mam:fetch:completed");
																				  }

																				  @Override
																				  public void onTimeout()
																						  throws JaxmppException {
																					  mutex.notify("mam:fetch:error:timeout");
																					  mutex.notify("mam:fetch:completed");
																				  }

																				  @Override
																				  public void onSuccess(String s,
																										boolean b,
																										tigase.jaxmpp.core.client.xmpp.utils.RSM rsm)
																						  throws JaxmppException {
																					  mutex.notify("mam:fetch:success");
																					  mutex.notify("mam:fetch:completed");
																				  }
																			  });
			mutex.waitFor(10*1000, "mam:fetch:completed");
			AssertJUnit.assertTrue(mutex.isItemNotified("mam:fetch:success"));
			Thread.sleep(100);
			System.out.println(mamMessages);
			AssertJUnit.assertEquals(2, mamMessages.size());

			Element retractedEl = mamMessages.get(0).getChildrenNS("retracted", "urn:xmpp:mix:misc:0");
			AssertJUnit.assertNotNull(retractedEl.getAttribute("by"));
			AssertJUnit.assertNotNull(retractedEl.getAttribute("time"));

			Element retract = mamMessages.get(1).getChildrenNS("retract", "urn:xmpp:mix:misc:0");
			AssertJUnit.assertNotNull(retract);
			AssertJUnit.assertEquals(idToModerate, receivedRetract.getAttribute("id"));
		} finally {
			destroyChannel();
		}
	}

	private void createChannel() throws JaxmppException, InterruptedException {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "create-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("create")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", channelName);
		Response result = sendRequest(jaxmpp, (IQ) Stanza.create(request.getElement()));
		if (result instanceof Response.Success) {
			Element create = result.getResponse().getChildrenNS("create", "urn:xmpp:mix:core:1");
			AssertJUnit.assertEquals(channelName, create.getAttribute("channel"));
		} else if (result instanceof Response.Error) {
			AssertJUnit.fail("Cannot create channel " + channelName + "@" + mixJID + ": " +
									 ((Response.Error) result).getError());
		}
	}

	private void destroyChannel() throws JaxmppException, InterruptedException {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "destroy-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("destroy")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", channelName);

		Response result = sendRequest(getJaxmppAdmin(), (IQ) Stanza.create(request.getElement()));
		AssertJUnit.assertTrue("Cannot destroy channel!", result instanceof Response.Success);
	}

	private IQ createJoinRequest(Jaxmpp jaxmpp, String nickname) throws Exception {
		ElementBuilder joinRequest = ElementBuilder.create("iq")
				.setAttribute("id", "join-" + jaxmpp.getSessionObject().getUserBareJid().getLocalpart())
				.setAttribute("type", "set")
				.setAttribute("to", jaxmpp.getSessionObject().getUserBareJid().toString())
				.child("client-join")
				.setXMLNS("urn:xmpp:mix:pam:2")
				.setAttribute("channel", channelName + "@" + mixJID)
				.child("join")
				.setXMLNS("urn:xmpp:mix:core:1")
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:messages")
				.up()
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:presence")
				.up()
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:participants")
				.up()
				.child("subscribe")
				.setAttribute("node", "urn:xmpp:mix:nodes:info")
				.up()
				.child("nick")
				.setValue(nickname);

		return (IQ) Stanza.create(joinRequest.getElement());
	}
}
