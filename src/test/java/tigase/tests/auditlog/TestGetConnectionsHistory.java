/*
 * TestGetConnectionsHistory.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2018 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License,
 * or (at your option) any later version.
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
package tigase.tests.auditlog;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.JID;
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
import tigase.util.datetime.TimestampHelper;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

public class TestGetConnectionsHistory extends AbstractAuditlogTest {

	private static final String USER_PREFIX = "auditlog-";
	private Date start;

	private final TimestampHelper timestampHelper = new TimestampHelper();

	private Account user;
	private Jaxmpp userJaxmpp;

	@BeforeClass
	public void setUp() throws Exception {
		start = new Date();
		
		user = createAccount().setLogPrefix(USER_PREFIX).build();
		userJaxmpp = user.createJaxmpp().setConnected(false).build();
	}

	@Test
	public void retrieveBeforeFirstConnection() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		Result result = retrieveConnectionsHistory(mutex, start, new Date());

		assertTrue(result.currentState.isEmpty());
		assertEquals(0, result.numberOfDisconnections);
		assertEquals(0, result.numberOfConnections);
		assertEquals(0.0, result.avgConnectionDuration);
		assertEquals(0, result.numberOfConnectionFailures);
		assertTrue(result.history.isEmpty());
	}

	@Test(dependsOnMethods = {"retrieveBeforeFirstConnection"})
	public void loginUser1() throws JaxmppException, InterruptedException {
		userJaxmpp.login(true);
		Thread.sleep(1000);
	}

	@Test(dependsOnMethods = {"loginUser1"})
	public void retrieveAfterFirstConnection() throws JaxmppException, InterruptedException, ParseException {
		final Mutex mutex = new Mutex();

		Result result = retrieveConnectionsHistory(mutex, start, new Date());

		assertEquals(1, result.currentState.size());
		assertCurrentState(result.currentState.get(0), "Connected", new Date(), null);

		assertEquals(0, result.numberOfDisconnections);
		assertEquals(1, result.numberOfConnections);
		//assertEquals(0.0, result.avgConnectionDuration);
		assertEquals(0, result.numberOfConnectionFailures);
		assertEquals(1, result.history.size());
		assertHistoryEntry(result.history.get(0), new Date(), "Connected", 0.0, null);
	}

	@Test(dependsOnMethods = {"retrieveAfterFirstConnection"})
	public void disconnectUser1() throws JaxmppException, InterruptedException {
		userJaxmpp.disconnect(true);
		Thread.sleep(1000);
	}

	@Test(dependsOnMethods = {"disconnectUser1"})
	public void retrieveAfterFirstDisconnection() throws JaxmppException, InterruptedException, ParseException {
		final Mutex mutex = new Mutex();

		Result result = retrieveConnectionsHistory(mutex, start, new Date());

		assertEquals(1, result.currentState.size());
		assertCurrentState(result.currentState.get(0), "Disconnected", new Date(System.currentTimeMillis()-1500), new Date());

		assertEquals(1, result.numberOfDisconnections);
		assertEquals(1, result.numberOfConnections);
		//assertEquals(0.0, result.avgConnectionDuration);
		assertEquals(0, result.numberOfConnectionFailures);
		assertEquals(2, result.history.size());
		assertHistoryEntry(result.history.get(0), new Date(), "Disconnected", 9.0, null);
		assertHistoryEntry(result.history.get(1), new Date(), "Connected", 0.0, null);
	}

	@Test(dependsOnMethods = {"retrieveAfterFirstDisconnection"})
	public void loginUser2() throws JaxmppException, InterruptedException {
		userJaxmpp.login(true);
		Thread.sleep(1000);
	}

	@Test(dependsOnMethods = {"loginUser2"})
	public void retrieveAfterSecondConnection() throws JaxmppException, InterruptedException, ParseException {
		final Mutex mutex = new Mutex();

		Result result = retrieveConnectionsHistory(mutex, start, new Date());

		assertEquals(1, result.currentState.size());
		assertCurrentState(result.currentState.get(0), "Connected", new Date(), null);

		assertEquals(1, result.numberOfDisconnections);
		assertEquals(2, result.numberOfConnections);
		//assertEquals(0.0, result.avgConnectionDuration);
		assertEquals(0, result.numberOfConnectionFailures);
		assertEquals(3, result.history.size());
		assertHistoryEntry(result.history.get(0), new Date(), "Connected", 0.0, null);
		assertHistoryEntry(result.history.get(1), new Date(System.currentTimeMillis()-1000), "Disconnected", 9.0, null);
		assertHistoryEntry(result.history.get(2), new Date(System.currentTimeMillis()-2000), "Connected", 0.0, null);
	}

	private void assertCurrentState(Element elem, String state, Date connectedFrom, Date disconnectedFrom)
			throws XMLException, ParseException {
		List<Element> fields = elem.getChildren();
		if (!state.equals(fields.get(1).getFirstChild("value").getValue())) {
			assertTrue(false);
		}

		Element value = fields.get(2).getFirstChild("value");
		if (connectedFrom == null) {
			assertTrue(value == null || value.getValue() == null);
		} else {
			assertNotNull(value);
			Date ts = timestampHelper.parseTimestamp(value.getValue());
			assertTrue(Math.abs(connectedFrom.getTime() - ts.getTime()) < 10000);
		}

		value = fields.get(3).getFirstChild("value");
		if (disconnectedFrom == null) {
			assertTrue(value == null || value.getValue() == null);
		} else {
			assertNotNull(value);
			Date ts = timestampHelper.parseTimestamp(value.getValue());
			assertTrue(Math.abs(disconnectedFrom.getTime() - ts.getTime()) < 10000);
		}

		value = fields.get(4).getFirstChild("value");
		assertNotNull(value);
		assertTrue(value.getValue() != null && !value.getValue().isEmpty());

		value = fields.get(5).getFirstChild("value");
		assertNotNull(value);

		if (!(Double.parseDouble(value.getValue()) < 30.0)) {
			TestLogger.log("Connection duration over 30.0s!! what is going on? (real value: " + value.getValue() + ")");
		}
		assertTrue (Double.parseDouble(value.getValue()) < 60.0);
	}

	private void assertHistoryEntry(Element elem, Date ts, String state, double duration, String error)
			throws XMLException, ParseException {
		List<Element> fields = elem.getChildren();
 		Element value =fields.get(1).getFirstChild("value");
 		assertNotNull(value);
		Date x = timestampHelper.parseTimestamp(value.getValue());
		assertTrue(Math.abs(ts.getTime() - x.getTime()) < 10000);

		value = fields.get(2).getFirstChild("value");
		assertNotNull(value);
		assertEquals(state, value.getValue());

		value = fields.get(3).getFirstChild("value");
		assertNotNull(value);
		assertTrue(value.getValue() != null && !value.getValue().isEmpty());

		value = fields.get(4).getFirstChild("value");
		assertNotNull(value);
		assertTrue(Math.abs(duration - Double.parseDouble(value.getValue())) < 10.0);

		value = fields.get(5).getFirstChild("value");
		if (error == null) {
			assertFalse (value != null && value.getValue() != null && !value.getValue().isEmpty());
		} else {
			assertEquals(error, value.getValue());
		}
	}

	private Result retrieveConnectionsHistory(Mutex mutex, Date from, Date to)
			throws JaxmppException, InterruptedException {
		String id = UUID.randomUUID().toString();

		JabberDataElement form = new JabberDataElement(XDataType.submit);
		form.addJidSingleField("jid", JID.jidInstance(user.getJid()));
		form.addTextSingleField("from", timestampHelper.format(from));
		form.addTextSingleField("to", timestampHelper.format(to));

		final Result result = new Result();

		getJaxmppAdmin().getModule(AdHocCommansModule.class).execute(getComponentJID(), "get-connections-history", Action.execute, form, new AdHocCommansModule.AdHocCommansAsyncCallback() {

			@Override
			public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
				mutex.notify(id + ":connections-history:" + error.getElementName());
				mutex.notify(id + ":connections-history");
			}

			@Override
			public void onTimeout() throws JaxmppException {
				mutex.notify(id + ":connections-history:timeout");
				mutex.notify(id + ":connections-history");
			}

			@Override
			protected void onResponseReceived(String sessionid, String node, State status, JabberDataElement data)
					throws JaxmppException {
				List<Element> children = data.getChildren();

				ParserState state = ParserState.none;
				
				for (int i=0; i<children.size(); i++) {
					Element el = children.get(i);
					if (state != ParserState.none) {
						if ("item".equals(el.getName())) {
							switch (state) {
								case currentState:
									result.currentState.add(el);
									break;
								case history:
									result.history.add(el);
									break;
								case statistics:
									List<Element> fields = el.getChildren();
									result.numberOfDisconnections = Long.parseLong(fields.get(0).getFirstChild("value").getValue());
									result.numberOfConnections = Long.parseLong(fields.get(1).getFirstChild("value").getValue());
									result.avgConnectionDuration = Double.parseDouble(fields.get(2).getFirstChild("value").getValue());
									result.numberOfConnectionFailures = Long.parseLong(fields.get(3).getFirstChild("value").getValue());
									break;
							}
						} else {
							state = ParserState.none;
						}
					}
					if ("reported".equals(el.getName())) {
						String label = el.getAttribute("label");
						if (label != null) {
							switch (label) {
								case "Current state":
									state = ParserState.currentState;
									break;
								case "Statistics":
									state = ParserState.statistics;
									break;
								case "History of connections":
									state = ParserState.history;
									break;
								default:
									state = ParserState.none;
									break;
							}
						} else {
							state = ParserState.none;
						}
					}
				}
				mutex.notify(id + ":connections-history:success");
				mutex.notify(id + ":connections-history");
			}
		});

		mutex.waitFor(30 * 1000, id + ":connections-history");
		assertTrue(mutex.isItemNotified(id + ":connections-history:success"));

		return result;
	}

	private enum ParserState {
		none,
		currentState,
		statistics,
		history
	}

	private class Result {

		// current state
		private List<Element> currentState = new ArrayList<>();

		// statistics
		private long numberOfDisconnections = 0;
		private long numberOfConnections = 0;
		private double avgConnectionDuration = 0.0;
		private long numberOfConnectionFailures = 0;
		
		// history entries
		private List<Element> history = new ArrayList<>();

	}
}
