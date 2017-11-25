package tigase.tests.server;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslModule;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractJaxmppTest;
import tigase.tests.utils.Account;

import static org.testng.AssertJUnit.assertTrue;

public class TestNonSaslAuthentication
		extends AbstractJaxmppTest {

	private Jaxmpp jaxmpp;
	private Account user;

	@BeforeMethod
	public void prepareAccountAndJaxmpp() throws JaxmppException, InterruptedException {
		user = createAccount().setLogPrefix("jaxmpp_").build();
	}

	@Test
	public void testAuth() throws JaxmppException {
		jaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			SaslModule saslModule = jaxmpp.getModule(SaslModule.class);
			if (saslModule != null) {
				jaxmpp.getModulesManager().unregister(saslModule);
			}
			return jaxmpp;
		}).build();

		jaxmpp.login(true);

		assertTrue(jaxmpp.isConnected());
	}

}