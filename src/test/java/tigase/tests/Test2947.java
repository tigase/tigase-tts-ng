package tigase.tests;

import java.util.Date;

import org.testng.Assert;
import org.testng.annotations.Test;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule.NotificationReceivedHandler;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;

public class Test2947 extends AbstractTest {

	@Test(groups = { "Offline Message" }, description = "Offline Message Sink Provider")
	public void testOfflineMessageSinkProvider() throws Exception {
		final Mutex mutex = new Mutex();

		final BareJID userAJID = createUserAccount("userA");
		final BareJID userBJID = createUserAccount("userB");

		final Jaxmpp ownerJaxmpp = createJaxmppAdmin();

//		final BareJID pubSubJID = BareJID.bareJIDInstance("pubsub."
//				+ ownerJaxmpp.getSessionObject().getUserBareJid().getDomain());
//		final String nodeName = "message_sink";

		// final JID publisherJID = JID.jidInstance("sess-man", "coffeebean");

		ownerJaxmpp.login(true);

		PubSubModule pubSub = ownerJaxmpp.getModule(PubSubModule.class);
		// pubSub.createNode(pubSubJID, nodeName, new PubSubAsyncCallback() {
		//
		// @Override
		// public void onTimeout() throws JaxmppException {
		// mutex.notify("pubSubNodeCreate", "pubSubNodeCreate:timeout");
		// }
		//
		// @Override
		// public void onSuccess(Stanza responseStanza) throws JaxmppException {
		// // TODO Auto-generated method stub
		// mutex.notify("pubSubNodeCreate", "pubSubNodeCreate:success");
		//
		// }
		//
		// @Override
		// protected void onEror(IQ response, ErrorCondition errorCondition,
		// PubSubErrorCondition pubSubErrorCondition)
		// throws JaxmppException {
		// TestLogger.log("PubSUb error: errorCondition" + errorCondition +
		// "; pubSubErrorCondition="
		// + pubSubErrorCondition);
		// Assert.fail("Node creation failed " + errorCondition);
		// mutex.notify("pubSubNodeCreate", "pubSubNodeCreate:error");
		// }
		// });
		//
		// mutex.waitFor(10 * 1000, "pubSubNodeCreate");
		// Assert.assertTrue(mutex.isItemNotified("pubSubNodeCreate:success"),
		// "PubSub node is not created!");

		// pubSub.setAffiliation(pubSubJID, nodeName, publisherJID,
		// Affiliation.publisher, new PubSubAsyncCallback() {
		//
		// @Override
		// public void onTimeout() throws JaxmppException {
		// mutex.notify("pubSubSetPublisher", "pubSubSetPublisher:timeout");
		// }
		//
		// @Override
		// public void onSuccess(Stanza responseStanza) throws JaxmppException {
		// mutex.notify("pubSubSetPublisher", "pubSubSetPublisher:success");
		// }
		//
		// @Override
		// protected void onEror(IQ response, ErrorCondition errorCondition,
		// PubSubErrorCondition pubSubErrorCondition)
		// throws JaxmppException {
		// TestLogger.log("PubSub error: errorCondition" + errorCondition +
		// "; pubSubErrorCondition="
		// + pubSubErrorCondition);
		// Assert.fail("Publisher set failed " + errorCondition);
		// mutex.notify("pubSubSetPublisher", "pubSubSetPublisher:error");
		// }
		// });
		// mutex.waitFor(10 * 1000, "pubSubSetPublisher");
		// Assert.assertTrue(mutex.isItemNotified("pubSubSetPublisher:success"),
		// "Publisher is not defined!");

		pubSub.addNotificationReceivedHandler(new NotificationReceivedHandler() {

			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID pubSubJID, String nodeName,
					String itemId, Element payload, Date delayTime, String itemType) {
				try {
					Message msg = (Message) Message.create(payload);
					mutex.notify("received:" + msg.getBody());
				} catch (JaxmppException e) {
					e.printStackTrace();
					Assert.fail(e.getMessage());
				}
			}
		});

		Jaxmpp userAJaxmpp = createJaxmpp("userA", userAJID);
		userAJaxmpp.login(true);

		final String body = "body-" + nextRnd();
		final Message msg1 = Message.create();
		msg1.setTo(JID.jidInstance(userBJID));
		msg1.setBody(body);
		msg1.setType(StanzaType.chat);
		msg1.setId(nextRnd());
		userAJaxmpp.send(msg1);

		mutex.waitFor(1000 * 30, "received:" + body);

		Assert.assertTrue(mutex.isItemNotified("received:" + body), "Notification from PubSub not received!");

		removeUserAccount(userBJID);
		removeUserAccount(userAJaxmpp);

		// pubSub.deleteNode(pubSubJID, nodeName, new PubSubAsyncCallback() {
		//
		// @Override
		// public void onTimeout() throws JaxmppException {
		// mutex.notify("pubSubNodeDelete", "pubSubNodeDelete:timeout");
		// }
		//
		// @Override
		// public void onSuccess(Stanza responseStanza) throws JaxmppException {
		// mutex.notify("pubSubNodeDelete", "pubSubNodeDelete:success");
		// }
		//
		// @Override
		// protected void onEror(IQ response, ErrorCondition errorCondition,
		// PubSubErrorCondition pubSubErrorCondition)
		// throws JaxmppException {
		// TestLogger.log("PubSub error: errorCondition" + errorCondition +
		// "; pubSubErrorCondition="
		// + pubSubErrorCondition);
		// Assert.fail("Node delete failed " + errorCondition);
		// mutex.notify("pubSubNodeDelete", "pubSubNodeDelete:error");
		// }
		// });
		//
		// mutex.waitFor(10 * 1000, "pubSubNodeDelete");
		// Assert.assertTrue(mutex.isItemNotified("pubSubNodeDelete:success"),
		// "Node is not deleted");
	}
}
