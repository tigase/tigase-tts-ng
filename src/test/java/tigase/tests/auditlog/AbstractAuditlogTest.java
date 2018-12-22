/*
 * AbstractAuditlogTest.java
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

import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.SkipException;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;

public class AbstractAuditlogTest
		extends AbstractTest
		implements IHookable {

	private Boolean auditlogAvailable = null;

	@Override
	public void run(IHookCallBack iHookCallBack, ITestResult iTestResult) {
		if (!isAuditlogAvailable()) {
			throw new SkipException("Skipping tests because AuditLog component is not available!");
		}
		if (isAuditlogAvailable()) {
			iHookCallBack.runTestMethod(iTestResult);
		} else {
			iTestResult.setStatus(ITestResult.SKIP);
		}
	}

	protected JID getAuditlogJID() {
		return JID.jidInstance("audit-log@" + getDomain(0));
	}

	protected boolean isAuditlogAvailable() {
		if (auditlogAvailable != null) {
			return auditlogAvailable;
		}

		try {
			final Mutex mutex = new Mutex();
			IQ iq = IQ.createIQ();
			iq.setType(StanzaType.get);
			iq.setTo(getAuditlogJID());
			iq.addChild(ElementFactory.create("ping", null, "urn:xmpp:ping"));

			getJaxmppAdmin().send(iq, 5000l, new AsyncCallback() {
				@Override
				public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
					if (error == XMPPException.ErrorCondition.feature_not_implemented) {
						auditlogAvailable = true;
					} else {
						auditlogAvailable = false;
					}
					mutex.notify("auditlog:ping");
				}

				@Override
				public void onSuccess(Stanza responseStanza) throws JaxmppException {
					auditlogAvailable = true;
					mutex.notify("auditlog:ping");
				}

				@Override
				public void onTimeout() throws JaxmppException {
					auditlogAvailable = false;
					mutex.notify("auditlog:ping");
				}
			});
			mutex.waitFor(30 * 1000, "auditlog:ping");
		} catch (Throwable ex) {
			auditlogAvailable = false;
			ex.printStackTrace();
		}
		return auditlogAvailable;
	}

}
