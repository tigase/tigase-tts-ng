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

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoveryModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.*;
import static tigase.TestLogger.log;

public class TestDiscovery
		extends AbstractTest {

	private final ConnectionsProvider connectionsProvider = new ConnectionsProvider();

	@BeforeClass
	public void setUp() throws Exception {
		Account user1 = createAccount().setLogPrefix("user1").build();
		Jaxmpp jaxmpp1 = user1.createJaxmpp().setConfigurator(c -> {
			return c;
		}).setConnected(true).build();
		JID mixJID1 = findMIXComponent(jaxmpp1);
		connectionsProvider.add(user1, jaxmpp1, mixJID1);

		Account user2 = createAccount().setLogPrefix("user2").build();
		Jaxmpp jaxmpp2 = user2.createJaxmpp().setConfigurator(c -> {
			return c;
		}).setConnected(true).build();
		JID mixJID2 = findMIXComponent(jaxmpp2);
		connectionsProvider.add(user2, jaxmpp2, mixJID2);
	}

	@DataProvider(name = "userConnectionsProvider")
	public Object[][] createClients() throws Exception {
		return connectionsProvider.getConnections();
	}

	public static JID findMIXComponent(final Jaxmpp jaxmpp) throws Exception {
		final String category = "conference";
		final String type = "mix";

		final Mutex mutex = new Mutex();

		final String domain = ResourceBinderModule.getBindedJID(jaxmpp.getSessionObject()).getBareJid().getDomain();

		final ArrayList<JID> jids = new ArrayList<>();

		final MutableObject<JID> componentJID = new MutableObject<>(null);

		jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getItems(JID.jidInstance(domain), new DiscoveryModule.DiscoItemsAsyncCallback() {
					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						items.forEach(item -> jids.add(item.getJid()));
						mutex.notify("items");
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						AssertJUnit.fail("Timeout");
					}
				});
		mutex.waitFor(30000, "items");

		for (final JID jid : jids) {
			jaxmpp.getModulesManager()
					.getModule(DiscoveryModule.class)
					.getInfo(jid, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
						@Override
						protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
													  Collection<String> features) throws XMLException {

							for (DiscoveryModule.Identity identity : identities) {
								if ((type == null || type.equals(identity.getType())) &&
										(category == null || category.equals(identity.getCategory()))) {
									componentJID.setValue(jid);
								}
							}

							mutex.notify("info:" + jid);
						}

						@Override
						public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
								throws JaxmppException {
							mutex.notify("info:" + jid);
						}

						@Override
						public void onTimeout() throws JaxmppException {
							mutex.notify("info:" + jid);
						}
					});
		}

		mutex.waitFor(30000, jids.stream().map(jid -> "info:" + jid).toArray(String[]::new));

		assertNotNull("MIX component not found!", componentJID.getValue());

		TestLogger.log("Found component: " + componentJID.getValue());

		return componentJID.getValue();
	}

	@Test(dataProvider = "userConnectionsProvider")
	public void testDetermineMIXSupportWithTOParameter(final ConnectionsProvider.UserDetails con) throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getInfo(JID.jidInstance(con.user.getJid()), new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						try {
							assertTrue("Feature urn:xmpp:mix:pam:2 not supported by Client's Server",
									   features.contains("urn:xmpp:mix:pam:2"));
							assertTrue("Feature urn:xmpp:mix:pam:2#archive not supported by Client's Server",
									   features.contains("urn:xmpp:mix:pam:2#archive"));

							mutex.notify("discovered", "discovered:OK");
						} catch (Throwable e) {
							e.printStackTrace();
							mutex.notify("discovered");
						}
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Timeout");
					}
				});
		mutex.waitFor(30000, "discovered");
		assertTrue("Client's Server doesn't supports MIX", mutex.isItemNotified("discovered:OK"));
	}

	@Test(enabled = false, dataProvider = "userConnectionsProvider")
	public void testDetermineMIXSupportWithoutTOParameter(final ConnectionsProvider.UserDetails con) throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getInfo(null, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						try {
							assertTrue("Feature urn:xmpp:mix:pam:2 not supported by Client's Server",
									   features.contains("urn:xmpp:mix:pam:2"));
							assertTrue("Feature urn:xmpp:mix:pam:2#archive not supported by Client's Server",
									   features.contains("urn:xmpp:mix:pam:2#archive"));

							mutex.notify("discovered", "discovered:OK");
						} catch (Throwable e) {
							e.printStackTrace();
							mutex.notify("discovered");
						}
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Timeout");
					}
				});
		mutex.waitFor(30000, "discovered");
		assertTrue("Client's Server doesn't supports MIX", mutex.isItemNotified("discovered:OK"));
	}

	@Test(dataProvider = "userConnectionsProvider")
	public void testDiscoveringService(final ConnectionsProvider.UserDetails con) throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getInfo(con.mixJID, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						try {
							assertTrue("Feature urn:xmpp:mix:core:1 not found in " + con.mixJID,
									   features.contains("urn:xmpp:mix:core:1"));
							assertTrue("Feature urn:xmpp:mix:core:1#searchable not found in " + con.mixJID,
									   features.contains("urn:xmpp:mix:core:1#searchable"));

							assertTrue("Identity not found in " + con.mixJID, identities.stream()
									.anyMatch(identity -> identity.getType().equals("mix") &&
											identity.getCategory().equals("conference")));
							mutex.notify("discovered", "discovered:OK");
						} catch (Throwable e) {
							e.printStackTrace();
							mutex.notify("discovered");
						}
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Timeout");
					}
				});

		mutex.waitFor(30000, "discovered");
		assertTrue(mutex.isItemNotified("discovered:OK"));
	}

	@Test(dataProvider = "userConnectionsProvider")
	public void testDiscoveringChannels(final ConnectionsProvider.UserDetails con) throws Exception {
		final Mutex mutex = new Mutex();
		final List<JID> channels = new ArrayList<>();

		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getItems(con.mixJID, new DiscoveryModule.DiscoItemsAsyncCallback() {
					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						channels.addAll(items.stream().map(DiscoveryModule.Item::getJid).collect(Collectors.toList()));
						mutex.notify("discovered", "discovered:OK");
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discovered");
						AssertJUnit.fail("Timeout");
					}
				});
		mutex.waitFor(30000, "discovered");
		assertTrue(mutex.isItemNotified("discovered:OK"));
		log("Found " + channels.size() + " channels.");

		assertTrue("No channels found!", channels.size() > 0);

		for (JID channel : channels) {
			discoverChannelInformation(con, channel);
			discoverNodesFromChannel(con, channel);
			determineInformationAboutChannel(con, channel);
			determineParticipants(con, channel);
		}
	}

	private void determineParticipants(final ConnectionsProvider.UserDetails con, final JID channelJID)
			throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(PubSubModule.class)
				.retrieveItem(channelJID.getBareJid(), "urn:xmpp:mix:nodes:participants",
							  new PubSubModule.RetrieveItemsAsyncCallback() {
								  @Override
								  protected void onEror(IQ response, XMPPException.ErrorCondition error,
														PubSubErrorCondition pubSubErrorCondition)
										  throws JaxmppException {
									  AssertJUnit.fail("Error " + error + " " + pubSubErrorCondition);
								  }

								  @Override
								  public void onTimeout() throws JaxmppException {
									  AssertJUnit.fail("Timeout");
								  }

								  @Override
								  protected void onRetrieve(IQ responseStanza, String nodeName,
															Collection<Item> items) {
									  assertEquals("Invalid node name!", "urn:xmpp:mix:nodes:participants", nodeName);

									  try {
										  for (Item item : items) {
											  Element part = item.getPayload()
													  .getChildrenNS("participant", "urn:xmpp:mix:core:1");
											  assertNotNull("No participant info in item", part);
											  assertNotNull("Missing nickname", part.getFirstChild("nick"));
											  assertNotNull("Missing JID", part.getFirstChild("jid"));
										  }
										  mutex.notify("determinedParticipants");
									  } catch (Exception e) {
										  fail(e);
									  }

								  }
							  });
		mutex.waitFor(30000, "determinedParticipants");
		assertTrue(mutex.isItemNotified("determinedParticipants"));
	}

	private void determineInformationAboutChannel(final ConnectionsProvider.UserDetails con, final JID channelJID)
			throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(PubSubModule.class)
				.retrieveItem(channelJID.getBareJid(), "urn:xmpp:mix:nodes:info",
							  new PubSubModule.RetrieveItemsAsyncCallback() {
								  @Override
								  protected void onRetrieve(IQ responseStanza, String nodeName,
															Collection<Item> items) {
									  try {
										  assertEquals("Invalid node name!", "urn:xmpp:mix:nodes:info", nodeName);
										  assertEquals("Too many info items", 1, items.size());

										  final JabberDataElement form = new JabberDataElement(
												  (Element) items.iterator().next());

										  assertEquals("Invalid FORM_TYPE", "urn:xmpp:mix:core:1",
													   form.getField("FORM_TYPE").getFieldValue());

										  mutex.notify("determinedChannelInfo", "determinedChannelInfo:OK");
									  } catch (Throwable e) {
										  e.printStackTrace();
										  mutex.notify("determinedChannelInfo");
									  }
								  }

								  @Override
								  protected void onEror(IQ response, XMPPException.ErrorCondition error,
														PubSubErrorCondition pubSubErrorCondition)
										  throws JaxmppException {
									  mutex.notify("determinedChannelInfo");
									  AssertJUnit.fail("Error " + error + " " + pubSubErrorCondition);
								  }

								  @Override
								  public void onTimeout() throws JaxmppException {
									  mutex.notify("determinedChannelInfo");
									  AssertJUnit.fail("Timeout");
								  }
							  });
		mutex.waitFor(30000, "determinedChannelInfo");
		assertTrue(mutex.isItemNotified("determinedChannelInfo:OK"));
	}

	private void discoverNodesFromChannel(final ConnectionsProvider.UserDetails con, final JID channelJID)
			throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getItems(channelJID, "mix", new DiscoveryModule.DiscoItemsAsyncCallback() {

					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						try {
							assertEquals("Invalid node name!", "mix", attribute);

							List<String> nodes = items.stream()
									.filter(item -> item.getJid().equals(channelJID))
									.map(DiscoveryModule.Item::getNode)
									.collect(Collectors.toList());

							assertEquals("It seems there were unknown JID in items", items.size(), nodes.size());

//							assertTrue("Missing urn:xmpp:mix:nodes:presence",
//									   nodes.contains("urn:xmpp:mix:nodes:presence"));
							assertTrue("Missing urn:xmpp:mix:nodes:participants",
									   nodes.contains("urn:xmpp:mix:nodes:participants"));
							assertTrue("Missing urn:xmpp:mix:nodes:messages",
									   nodes.contains("urn:xmpp:mix:nodes:messages"));
							assertTrue("Missing urn:xmpp:mix:nodes:config",
									   nodes.contains("urn:xmpp:mix:nodes:config"));

							mutex.notify("discoveredNodes", "discoveredNodes:OK");
						} catch (Throwable e) {
							e.printStackTrace();
							mutex.notify("discoveredNodes");
						}
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("discoveredNodes");
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discoveredNodes");
						AssertJUnit.fail("Timeout");
					}
				});

		mutex.waitFor(30000, "discoveredNodes");
		assertTrue(mutex.isItemNotified("discoveredNodes:OK"));
	}

	private void discoverChannelInformation(final ConnectionsProvider.UserDetails con, final JID channelJID)
			throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getInfo(channelJID, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						try {
							assertTrue("Feature not found", features.contains("urn:xmpp:mix:core:1"));
							assertTrue("Feature not found", features.contains("urn:xmpp:mam:2"));
							assertTrue("Identity not found", identities.stream()
									.anyMatch(identity -> identity.getType().equals("mix") &&
											identity.getCategory().equals("conference")));
							mutex.notify("discoveredInfo", "discoveredInfo:OK");
						} catch (Throwable e) {
							e.printStackTrace();
							mutex.notify("discoveredInfo:OK");
						}
					}

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
							throws JaxmppException {
						mutex.notify("discoveredInfo");
						AssertJUnit.fail("Error " + error);
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("discoveredInfo");
						AssertJUnit.fail("Timeout");
					}
				});
		mutex.waitFor(30000, "discoveredInfo");
		assertTrue(mutex.isItemNotified("discoveredInfo:OK"));
	}

}
