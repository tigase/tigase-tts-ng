package tigase.tests.mix;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
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

	static class UserDetails {

		final Account user;
		final Jaxmpp jaxmpp;
		final JID mixJID;

		UserDetails(Account user, Jaxmpp jaxmpp, JID mixJID) {
			this.user = user;
			this.jaxmpp = jaxmpp;
			this.mixJID = mixJID;
		}
	}

	private final List<UserDetails> connections = new ArrayList<>();

	@BeforeClass
	public void setUp() throws Exception {
		Account user = createAccount().setLogPrefix("user1").build();
		Jaxmpp jaxmpp = user.createJaxmpp().setConfigurator(c -> {
			return c;
		}).setConnected(true).build();

		JID mixJID = JID.jidInstance("mix." + user.getJid().getDomain());
		connections.add(new UserDetails(user, jaxmpp, mixJID));
	}

	@DataProvider(name = "userConnectionsProvider")
	public Object[][] createClients() throws Exception {
		Object[][] result = new Object[connections.size()][];
		for (int i = 0; i < connections.size(); i++) {
			result[i] = new Object[]{connections.get(i)};
		}
		return result;
	}

	@Test(dataProvider = "userConnectionsProvider")
	public void testDiscoveringService(final UserDetails con) throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getInfo(con.mixJID, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						assertTrue("Feature not found", features.contains("urn:xmpp:mix:core:1"));
						assertTrue("Feature not found", features.contains("urn:xmpp:mix:core:1#searchable"));

						assertTrue("Identity not found", identities.stream()
								.anyMatch(identity -> identity.getType().equals("mix") &&
										identity.getCategory().equals("conference")));
						mutex.notify("discovered");
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

		mutex.waitFor(30000, "discovered");
		assertTrue(mutex.isItemNotified("discovered"));
	}

	@Test(dataProvider = "userConnectionsProvider")
	public void testDiscoveringChannels(final UserDetails con) throws Exception {
		final Mutex mutex = new Mutex();
		final List<JID> channels = new ArrayList<>();

		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getItems(con.mixJID, new DiscoveryModule.DiscoItemsAsyncCallback() {
					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						channels.addAll(items.stream().map(DiscoveryModule.Item::getJid).collect(Collectors.toList()));
						mutex.notify("discovered");
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
		mutex.waitFor(30000, "discovered");
		assertTrue(mutex.isItemNotified("discovered"));
		log("Found " + channels.size() + " channels.");

		for (JID channel : channels) {
			discoverChannelInformation(con, channel);
			discoverNodesFromChannel(con, channel);
			determineInformationAboutChannel(con, channel);
			determineParticipants(con, channel);
		}
	}

	private void determineParticipants(final UserDetails con, final JID channelJID) throws Exception {
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

	private void determineInformationAboutChannel(final UserDetails con, final JID channelJID) throws Exception {
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

										  mutex.notify("determinedChannelInfo");
									  } catch (JaxmppException e) {
										  fail(e);
									  }
								  }

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
							  });
		mutex.waitFor(30000, "determinedChannelInfo");
		assertTrue(mutex.isItemNotified("determinedChannelInfo"));
	}

	private void discoverNodesFromChannel(final UserDetails con, final JID channelJID) throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getItems(channelJID, "mix", new DiscoveryModule.DiscoItemsAsyncCallback() {

					@Override
					public void onInfoReceived(String attribute, ArrayList<DiscoveryModule.Item> items)
							throws XMLException {
						assertEquals("Invalid node name!", "mix", attribute);

						List<String> nodes = items.stream()
								.filter(item -> item.getJid().equals(channelJID))
								.map(DiscoveryModule.Item::getNode)
								.collect(Collectors.toList());

						assertEquals("It seems there were unknown JID in items", items.size(), nodes.size());

						assertTrue("Missing urn:xmpp:mix:nodes:presence",
								   nodes.contains("urn:xmpp:mix:nodes:presence"));
						assertTrue("Missing urn:xmpp:mix:nodes:participants",
								   nodes.contains("urn:xmpp:mix:nodes:participants"));
						assertTrue("Missing urn:xmpp:mix:nodes:messages",
								   nodes.contains("urn:xmpp:mix:nodes:messages"));
						assertTrue("Missing urn:xmpp:mix:nodes:config", nodes.contains("urn:xmpp:mix:nodes:config"));

						mutex.notify("discoveredNodes");
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

		mutex.waitFor(30000, "discoveredNodes");
		assertTrue(mutex.isItemNotified("discoveredNodes"));
	}

	private void discoverChannelInformation(final UserDetails con, final JID channelJID) throws Exception {
		final Mutex mutex = new Mutex();
		con.jaxmpp.getModulesManager()
				.getModule(DiscoveryModule.class)
				.getInfo(channelJID, new DiscoveryModule.DiscoInfoAsyncCallback(null) {
					@Override
					protected void onInfoReceived(String node, Collection<DiscoveryModule.Identity> identities,
												  Collection<String> features) throws XMLException {
						assertTrue("Feature not found", features.contains("urn:xmpp:mix:core:1"));
						assertTrue("Feature not found", features.contains("urn:xmpp:mam:2"));
						assertTrue("Identity not found", identities.stream()
								.anyMatch(identity -> identity.getType().equals("mix") &&
										identity.getCategory().equals("conference")));
						mutex.notify("discoveredInfo");
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
		mutex.waitFor(30000, "discoveredInfo");
		assertTrue(mutex.isItemNotified("discoveredInfo"));
	}

}
