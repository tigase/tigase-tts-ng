/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2019 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.tests.workgroup;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.JidMultiField;
import tigase.jaxmpp.core.client.xmpp.forms.TextSingleField;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule.InvitationReceivedHandler;
import tigase.jaxmpp.core.client.xmpp.modules.workgroup.WorkgroupAgentModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.testng.Assert.assertTrue;

/**
 * XEP-0142: Workgroup Queues
 * https://xmpp.org/extensions/xep-0142.html
 */
public class TestWorkGroup
		extends AbstractTest {

	private static final String USER_PREFIX = "WG_";
	private final static String WG_XMLNS = "http://jabber.org/protocol/workgroup";
	private final Mutex mutex = new Mutex();
	private Jaxmpp agentJaxmpp;
	private Account agentUser;
	private Jaxmpp clientJaxmpp;
	private Account clientUser;
	private JID workgroupBaseJID;
	private JID workgroupSupportJID;

	@BeforeClass
	public void setUp() throws Exception {
		workgroupBaseJID = JID.jidInstance("wg." + getDomain(0));
		final String groupName = "support_" + nextRnd();
		workgroupSupportJID = JID.jidInstance(groupName, workgroupBaseJID.getDomain());
		agentUser = createAccount().setLogPrefix("agent").build();
		agentJaxmpp = agentUser.createJaxmpp().setConfigurator(jax -> {
			jax.getModulesManager().register(new WorkgroupAgentModule());
			return registerDiscoveryAndMuc(jax);
		}).setConnected(true).build();
		clientUser = createAccount().setLogPrefix("client").build();
		clientJaxmpp = clientUser.createJaxmpp()
				.setConfigurator(this::registerDiscoveryAndMuc)
				.setConnected(true)
				.build();
	}

	@Test(dependsOnMethods = {"testCreateGroup", "testFlow"}, alwaysRun = true)
	public void testDeleteGroup() throws Exception {
		assertTrue(deleteNewWorkGroup(workgroupSupportJID.getLocalpart()));
	}

	@Test(groups = {"features"})
	public void testComponentDiscovery() throws JaxmppException, InterruptedException {

		clientJaxmpp.getModule(DiscoveryModule.class)
				.getInfo(workgroupBaseJID, new DiscoveryModule.DiscoInfoAsyncCallback(null) {

					@Override
					public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition)
							throws JaxmppException {
						mutex.notify("discovery:components:workgroup");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovery:components:workgroup");
					}

					@Override
					protected void onInfoReceived(String s, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> receivedFeatures) throws XMLException {

						for (DiscoveryModule.Identity identity : identities) {
							if ("collaboration".equals(identity.getCategory()) &&
									"workgroup".equals(identity.getType())) {
								mutex.notify("discovery:components:workgroup:identity");
							}
						}

						if (receivedFeatures.contains("http://jabber.org/protocol/workgroup")) {
							mutex.notify("discovery:components:workgroup:http://jabber.org/protocol/workgroup");
						}
						mutex.notify("discovery:components:workgroup");
					}
				});

		mutex.waitFor(30 * 1000, "discovery:components:workgroup");
		assertTrue(mutex.isItemNotified("discovery:components:workgroup:identity"));
		assertTrue(mutex.isItemNotified("discovery:components:workgroup:http://jabber.org/protocol/workgroup"));
	}

	@Test
	public void testCreateGroup() throws Exception {
		assertTrue(createNewWorkGroup(workgroupSupportJID.getLocalpart(), JID.jidInstance(agentUser.getJid())));
	}

	@Test(dependsOnMethods = {"testCreateGroup"})
	public void testFlow() throws Exception {

		setLoggerLevel(Level.FINEST, false);

		final InvitationReceivedHandler handler = (sessionObject, invitation, inviterJID, roomJID) -> mutex.notify(
				"workgroup:invitation:" + sessionObject.getUserBareJid() + ":" + inviterJID);
		clientJaxmpp.getEventBus().addHandler(InvitationReceivedHandler.InvitationReceivedEvent.class, handler);
		agentJaxmpp.getEventBus().addHandler(InvitationReceivedHandler.InvitationReceivedEvent.class, handler);

		// Agent joins the room https://xmpp.org/extensions/xep-0142.html#proto-agent-presence
		final Presence agentJoinPresence = Presence.create();
		agentJoinPresence.setTo(workgroupSupportJID);
		final Element agentStatus = ElementBuilder.create("agent-status", WG_XMLNS).getElement();
		agentStatus.addChild(ElementBuilder.create("max-chats").setValue("5").getElement());
		agentJoinPresence.addChild(agentStatus);
		agentJaxmpp.send(agentJoinPresence);
		TimeUnit.SECONDS.sleep(1);

		final IQ acceptIQ = IQ.createIQ();

		agentJaxmpp.getEventBus()
				.addHandler(WorkgroupAgentModule.OfferReceivedHandler.OfferReceivedEvent.class,
							(sessionObject, userJID, workgroupJID, timeout) -> {
								if (fillOutAcceptIQ(userJID, workgroupJID, acceptIQ)) {
									mutex.notify("workgroup:offer:success");
								}
								mutex.notify("workgroup:offer");
							});

		// Client join queue https://xmpp.org/extensions/xep-0142.html#proto-user-join
		final IQ joinRequest = prepareJoinRequest(workgroupSupportJID);
		assertTrue(sendAndWait(clientJaxmpp, joinRequest, NullAsyncCallback.instance));

		mutex.waitFor(5 * 1000, "workgroup:offer");
		assertTrue(mutex.isItemNotified("workgroup:offer:success"));
		assertTrue(sendAndWait(agentJaxmpp, acceptIQ, NullAsyncCallback.instance), "Offer accept failed");

		// Agent and Client receive invitation to join room.
		mutex.waitFor(1 * 1000, getInvitationId(clientUser));
		mutex.waitFor(1 * 1000, getInvitationId(agentUser));
		assertTrue(mutex.isItemNotified(getInvitationId(clientUser)), "Client invitation not received");
		assertTrue(mutex.isItemNotified(getInvitationId(agentUser)), "Agent invitation not received");

		clientJaxmpp.getEventBus().remove(handler);
		agentJaxmpp.getEventBus().remove(handler);
	}

	private String getInvitationId(Account clientUser) {
		return "workgroup:invitation:" + clientUser.getJid() + ":" + workgroupSupportJID;
	}

	private boolean fillOutAcceptIQ(JID userJID, JID workgroupJID, IQ acceptIQ) {
		try {
			acceptIQ.setType(StanzaType.set);
			acceptIQ.setTo(workgroupJID);
			final Element offerAccept = ElementBuilder.create("offer-accept", WG_XMLNS)
					.setAttribute("jid", String.valueOf(userJID))
					.getElement();
			acceptIQ.addChild(offerAccept);
		} catch (XMLException e) {
			TestLogger.log("Exception while processing offer request: " + e.getMessage());
			return false;
		}
		return true;
	}

	private Jaxmpp registerDiscoveryAndMuc(Jaxmpp jax) {
		jax.getModulesManager().register(new DiscoveryModule());
		jax.getModulesManager().register(new MucModule());
		jax.getModulesManager().register(new WorkgroupAgentModule());
		return jax;
	}

	private boolean execWorkGroupGroupAdhoc(String name, String commandName, JID... agents)
			throws JaxmppException, InterruptedException {
		final Jaxmpp jaxmppAdmin = getJaxmppAdmin();
		final AdHocCommansModule module = jaxmppAdmin.getModulesManager().getModule(AdHocCommansModule.class);
		module.execute(workgroupBaseJID, commandName, Action.execute, null,
					   new AdHocCommansModule.AdHocCommansAsyncCallback() {
						   @Override
						   public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
								   throws JaxmppException {
							   mutex.notify("item:add:" + error, "item:add");
						   }

						   @Override
						   public void onTimeout() throws JaxmppException {
							   mutex.notify("item:add:timeout", "item:add");
						   }

						   @Override
						   protected void onResponseReceived(String sessionid, String node, State status,
															 JabberDataElement _data) throws JaxmppException {

							   TestLogger.log("Received dataform: " + _data.getAsString());
							   JabberDataElement data = new JabberDataElement(ElementFactory.create(_data));
							   ((TextSingleField) data.getField("name")).setFieldValue(name);

							   if ("create-workgroup-queue".equals(commandName) && agents != null &&
									   agents.length > 0) {
								   ((JidMultiField) data.getField("agents")).addFieldValue(agents);
							   }

							   TestLogger.log("Received dataform: " + data.getAsString());
							   module.execute(workgroupBaseJID, commandName, Action.execute, data,
											  new AdHocCommansModule.AdHocCommansAsyncCallback() {
												  @Override
												  public void onError(Stanza responseStanza,
																	  XMPPException.ErrorCondition error)
														  throws JaxmppException {
													  mutex.notify("item:add:" + error, "item:add");
												  }

												  @Override
												  public void onTimeout() throws JaxmppException {
													  mutex.notify("item:add:timeout", "item:add");
												  }

												  @Override
												  protected void onResponseReceived(String sessionid, String node,
																					State status,
																					JabberDataElement data)
														  throws JaxmppException {
													  if ("Workgroup Queue created".equals(data.getTitle())) {
														  mutex.notify("item:add:success");
													  } else {
														  mutex.notify("item:add:" + data.getTitle());
													  }
													  mutex.notify("item:add");
												  }
											  });
						   }
					   });

		mutex.waitFor(20 * 1000, "item:add");
		return mutex.isItemNotified("item:add:success");
	}

	private boolean createNewWorkGroup(String name, JID... agents) throws JaxmppException, InterruptedException {
		final String createNodeCommandName = "create-workgroup-queue";
		return execWorkGroupGroupAdhoc(name, createNodeCommandName, agents);
	}

	private boolean deleteNewWorkGroup(String name) throws JaxmppException, InterruptedException {
		final String commandName = "delete-workgroup-queue";
		return execWorkGroupGroupAdhoc(name, commandName);
	}

	private IQ prepareJoinRequest(JID to) throws JaxmppException {
		IQ joinRequest;
		joinRequest = IQ.createIQ();
		joinRequest.setTo(to);
		joinRequest.setFrom(JID.jidInstance(clientUser.getJid()));
		joinRequest.setType(StanzaType.set);
		final Element joinElement = ElementBuilder.create("join-queue", WG_XMLNS).getElement();
		joinElement.addChild(ElementBuilder.create("queue-notifications").getElement());
		joinRequest.addChild(joinElement);
		return joinRequest;
	}

	private static class NullAsyncCallback
			implements AsyncCallback {

		final static NullAsyncCallback instance = new NullAsyncCallback();

		@Override
		public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {

		}

		@Override
		public void onSuccess(Stanza responseStanza) throws JaxmppException {

		}

		@Override
		public void onTimeout() throws JaxmppException {

		}
	}
}
