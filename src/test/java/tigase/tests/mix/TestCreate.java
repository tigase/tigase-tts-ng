package tigase.tests.mix;

import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.utils.Account;

public class TestCreate
		extends AbstractTest {

	public static final String CHANNEL_NAME = "testchannel";

	private Jaxmpp jaxmpp;
	private JID mixJID;

	@BeforeClass
	public void setUp() throws Exception {
		jaxmpp = getJaxmppAdmin();
		this.mixJID = TestDiscovery.findMIXComponent(jaxmpp);
	}

	@Test
	public void testCreateChannel() throws Exception {
		ElementBuilder request = ElementBuilder.create("iq")
				.setAttribute("id", "create-01")
				.setAttribute("type", "set")
				.setAttribute("to", mixJID.toString())
				.child("create")
				.setXMLNS("urn:xmpp:mix:core:1")
				.setAttribute("channel", CHANNEL_NAME);
		Response result = sendRequest(jaxmpp, (IQ) Stanza.create(request.getElement()));
		if (result instanceof Response.Success) {
			Element create = result.getResponse().getChildrenNS("create", "urn:xmpp:mix:core:1");
			AssertJUnit.assertEquals(CHANNEL_NAME, create.getAttribute("channel"));
		} else if (result instanceof Response.Error) {
			AssertJUnit.fail("Cannot create channel " + CHANNEL_NAME + "@" + mixJID + ": " +
									 ((Response.Error) result).getError());
		}
	}

}
