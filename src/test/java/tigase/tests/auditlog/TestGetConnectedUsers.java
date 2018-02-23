package tigase.tests.auditlog;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.Action;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.State;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestGetConnectedUsers extends AbstractAuditlogTest {

	private static final String USER_PREFIX = "auditlog-";
	private static Date start = new Date();
	
	private Account user1;
	private Jaxmpp user1Jaxmpp;
	private Account user2;
	private Jaxmpp user2Jaxmpp;
	
	@BeforeClass
	public void setUp() throws Exception {
		if (!isAuditlogAvailable()) {
			return;
		}

		user1 = createAccount().setLogPrefix(USER_PREFIX).build();
		user1Jaxmpp = user1.createJaxmpp().setConnected(true).build();

		user2 = createAccount().setLogPrefix(USER_PREFIX).build();
		user2Jaxmpp = user2.createJaxmpp().setConnected(false).build();
	}

	@Test
	public void retrieveConnectedUsers1() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getConnectedUsers(mutex, user1.getJid().getDomain(), null);

		assertTrue(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveConnectedUsers1"})
	public void retrieveConnectedUsers1WithFilter() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getConnectedUsers(mutex, user1.getJid().getDomain(), USER_PREFIX);

		assertTrue(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));

		results = getConnectedUsers(mutex, user1.getJid().getDomain(), "non-auditlog-");

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveConnectedUsers1WithFilter"})
	public void loginUser2() throws JaxmppException {
		user2Jaxmpp.login(true);
	}

	@Test(dependsOnMethods = {"loginUser2"})
	public void retrieveConnectedUsers2() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getConnectedUsers(mutex, user1.getJid().getDomain(), null);

		assertTrue(existsInResults(user1.getJid(), results));
		assertTrue(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveConnectedUsers2"})
	public void retrieveConnectedUsers2WithFilter() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getConnectedUsers(mutex, user1.getJid().getDomain(), USER_PREFIX);

		assertTrue(existsInResults(user1.getJid(), results));
		assertTrue(existsInResults(user2.getJid(), results));

		results = getConnectedUsers(mutex, user1.getJid().getDomain(), "non-auditlog-");

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveConnectedUsers2WithFilter"})
	public void disconnectUser2() throws JaxmppException {
		user2Jaxmpp.disconnect(true);
	}

	@Test(dependsOnMethods = {"disconnectUser2"})
	public void retrieveConnectedUsers3() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getConnectedUsers(mutex, user1.getJid().getDomain(), null);

		assertTrue(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	@Test(dependsOnMethods = {"retrieveConnectedUsers3"})
	public void retrieveConnectedUsers3WithFilter() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		List<Element> results = getConnectedUsers(mutex, user1.getJid().getDomain(), USER_PREFIX);

		assertTrue(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));

		results = getConnectedUsers(mutex, user1.getJid().getDomain(), "non-auditlog-");

		assertFalse(existsInResults(user1.getJid(), results));
		assertFalse(existsInResults(user2.getJid(), results));
	}

	private List<Element> getConnectedUsers(Mutex mutex, String domain, String userLike)
			throws JaxmppException, InterruptedException {
		String id = UUID.randomUUID().toString();

		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addListSingleField("domain", domain);
		if (userLike != null) {
			form.addTextSingleField("jidLike", userLike);
		}
		List<Element> results = new ArrayList<>();
		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(getAuditlogJID(), "get-connected-users",
																Action.execute, form, new AdHocCommansModule.AdHocCommansAsyncCallback() {

					@Override
					public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
						mutex.notify(id + ":get-connected-users:failure:" + error.getElementName());
						mutex.notify(id + ":get-connected-users:completed");
					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify(id + ":get-connected-users:failure:timeout");
						mutex.notify(id + ":get-connected-users:completed");
					}

					@Override
					protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
							throws JaxmppException {
						mutex.notify(id + ":get-connected-users:success");
						List<Element> children = data.getChildren();
						boolean started = false;
						boolean ended = false;
						for (int i=0; i<children.size(); i++) {
							Element el = children.get(i);
							if (started && !ended) {
								if ("item".equals(el.getName())) {
									results.add(el);
								} else {
									ended = true;
								}
							} else if (!started) {
								if ("reported".equals(el.getName()) && "Connected users".equals(el.getAttribute("label"))) {
									started = true;
								}
							}
						}
						mutex.notify(id + ":get-connected-users:completed");
					}
				});

		mutex.waitFor(30 * 1000, id + ":get-connected-users:completed");
		assertTrue(mutex.isItemNotified(id + ":get-connected-users:success"));

		return results;
	}

	private boolean existsInResults(BareJID jid, List<Element> results) {
		return results.stream().filter(el -> {
			try {
				return jid.toString().equals(el.getFirstChild("field").getFirstChild("value").getValue());
			} catch (XMLException ex) {
				throw new RuntimeException(ex);
			}
		}).findAny().isPresent();
	}

}
