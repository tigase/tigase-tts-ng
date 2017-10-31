package tigase.tests.server;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCard;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import static org.testng.AssertJUnit.assertTrue;

public class TestVcardTemp extends AbstractTest {

	private Jaxmpp jaxmpp1;

	private Account user1;

	@BeforeMethod
	public void setUp() throws JaxmppException, InterruptedException {
		user1 = createAccount().setLogPrefix("user1").build();
		jaxmpp1 = user1.createJaxmpp().setConnected(true).build();
	}

	@Test(groups = {"Phase 1"}, description = "Retrieve and set vcard-temp")
	public void testRetrieveAndSetVCardTemp() throws Exception {
		final Mutex mutex = new Mutex();
		final VCardModule module = jaxmpp1.getModule(VCardModule.class);

		module.retrieveVCard(JID.jidInstance(user1.getJid()), new VCardModule.VCardAsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				mutex.notify("retrieve");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify("retrieve");
			}

			@Override
			protected void onVCardReceived(VCard vCard) throws XMLException {
				mutex.notify("retrieved", "retrieve");

			}
		});
		mutex.waitFor(1000 * 20, "retrieve");

		assertTrue("Cannot retrieve vcard-temp", mutex.isItemNotified("retrieved"));

		// Trying to set vcard-temp with empty photo type and phot val. (Related to Bug #6293)
		VCard vCard = new VCard();
		vCard.setFullName("Tester Testerowsky");
		vCard.setPhotoType(null);
		vCard.setPhotoVal(null);

		final IQ iq = IQ.create();
		iq.setType(StanzaType.set);
		iq.addChild(vCard.makeElement());

		sendAndWait(jaxmpp1,iq, new AsyncCallback() {
			@Override
			public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
				Assert.fail("Cannot set vcard-temp");
			}

			@Override
			public void onSuccess(Stanza stanza) throws JaxmppException {

			}

			@Override
			public void onTimeout() throws JaxmppException {
				Assert.fail("Timeout during setting vcard-temp");

			}
		});


	}

}
