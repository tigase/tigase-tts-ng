package tigase.tests.mix;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static tigase.tests.mix.TestCreate.CHANNEL_NAME;

public class TestJoin
		extends AbstractTest {

	private Jaxmpp jaxmpp;
	private JID mixJID;
	private Account user;

	@BeforeClass
	public void setUp() throws Exception {
		this.user = createAccount().setLogPrefix("user1").build();
		this.jaxmpp = user.createJaxmpp().setConfigurator(c -> {
			return c;
		}).setConnected(true).build();

		this.mixJID = JID.jidInstance("mix." + user.getJid().getDomain());
	}

	@Test
	public void testJoinUser1() throws Exception {
		final Mutex mutex = new Mutex();

		final ArrayList<Element> receivedParicipants = new ArrayList<>();

		final PubSubModule.NotificationReceivedHandler handler = new PubSubModule.NotificationReceivedHandler() {
			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID pubSubJID,
											   String nodeName, String itemId, Element payload, Date delayTime,
											   String itemType) {
				if (nodeName.equals("urn:xmpp:mix:nodes:participants")) {
					receivedParicipants.add(payload);
					mutex.notify("receivedParicipants");
				}
			}
		};

		try {
			jaxmpp.getEventBus()
					.addHandler(PubSubModule.NotificationReceivedHandler.NotificationReceivedEvent.class, handler);

			ElementBuilder joinRequest = ElementBuilder.create("iq")
					.setAttribute("id", "join-1")
					.setAttribute("type", "set")
					.setAttribute("to", user.getJid().toString())
					.child("client-join")
					.setXMLNS("urn:xmpp:mix:pam:2")
					.setAttribute("channel", CHANNEL_NAME + "@" + mixJID)
					.child("join")
					.setXMLNS("urn:xmpp:mix:core:1")
					.child("subscribe")
					.setXMLNS("urn:xmpp:mix:nodes:messages")
					.up()
					.child("subscribe")
					.setXMLNS("urn:xmpp:mix:nodes:presence")
					.up()
					.child("subscribe")
					.setXMLNS("urn:xmpp:mix:nodes:participants")
					.up()
					.child("subscribe")
					.setXMLNS("urn:xmpp:mix:nodes:info")
					.up()
					.child("nick")
					.setValue("third witch");

			Response response = sendRequest(jaxmpp, (IQ) Stanza.create(joinRequest.getElement()));

			if (response instanceof Response.Success) {
				Element cj = response.getResponse().getChildrenNS("client-join", "urn:xmpp:mix:pam:2");
				Element j = cj.getChildrenNS("join", "urn:xmpp:mix:core:1");

				// jid is not always required?? or maybe it is?
				// according to MIX:Core participant id should be placed within `id` attribute but PAM for some reason
				// forces usage of `jid` attribute in for `participant-id#channel-jid`
				// if this will be an issue in the furure we can do "rewrite" on the server level
				assertNotNull(j.getAttribute("jid"));

				assertNotNull("Missing node urn:xmpp:mix:nodes:messages",
							  findNode(j, "subscribe", "urn:xmpp:mix:nodes:messages"));
				assertNotNull("Missing node urn:xmpp:mix:nodes:presence",
							  findNode(j, "subscribe", "urn:xmpp:mix:nodes:presence"));
				assertNotNull("Missing node urn:xmpp:mix:nodes:participants",
							  findNode(j, "subscribe", "urn:xmpp:mix:nodes:participants"));
				assertNotNull("Missing node urn:xmpp:mix:nodes:info",
							  findNode(j, "subscribe", "urn:xmpp:mix:nodes:info"));

				assertEquals("Invalid nickname", "third witch", j.getFirstChild("nick").getValue());

			} else if (response instanceof Response.Error) {
				AssertJUnit.fail("Cannot join channel " + CHANNEL_NAME + "@" + mixJID + ": " +
										 ((Response.Error) response).getError());
			}



		} finally {
			jaxmpp.getEventBus().remove(handler);
		}
	}

	private static Element findNode(Element element, String name, String node) {
		try {
			List<Element> el = element.getChildren(name);
			for (Element e : el) {
				if (e.getAttribute("node").equals(node)) {
					return e;
				}
			}
		} catch (Exception e) {
			fail(e);
		}
		return null;
	}

}
