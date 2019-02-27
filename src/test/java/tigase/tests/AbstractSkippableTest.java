/*
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
package tigase.tests;

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

public abstract class AbstractSkippableTest
		extends AbstractTest
		implements IHookable {

	private Boolean componentAvailable = null;

	@Override
	public void run(IHookCallBack iHookCallBack, ITestResult iTestResult) {
		if (!isComponentAvailable()) {
			throw new SkipException("Skipping tests because " + getComponentName() + " component is not available!");
		}
		if (isComponentAvailable()) {
			iHookCallBack.runTestMethod(iTestResult);
		} else {
			iTestResult.setStatus(ITestResult.SKIP);
		}
	}

	abstract protected JID getComponentJID();
	abstract protected String getComponentName();

	protected boolean isComponentAvailable() {
		if (componentAvailable != null) {
			return componentAvailable;
		}

		try {
			final Mutex mutex = new Mutex();
			IQ iq = IQ.createIQ();
			iq.setType(StanzaType.get);
			iq.setTo(getComponentJID());
			iq.addChild(ElementFactory.create("ping", null, "urn:xmpp:ping"));

			getJaxmppAdmin().send(iq, 5000l, new AsyncCallback() {
				@Override
				public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
					if (error == XMPPException.ErrorCondition.feature_not_implemented) {
						componentAvailable = true;
					} else {
						componentAvailable = false;
					}
					mutex.notify("component:ping");
				}

				@Override
				public void onSuccess(Stanza responseStanza) throws JaxmppException {
					componentAvailable = true;
					mutex.notify("component:ping");
				}

				@Override
				public void onTimeout() throws JaxmppException {
					componentAvailable = false;
					mutex.notify("component:ping");
				}
			});
			mutex.waitFor(30 * 1000, "component:ping");
		} catch (Throwable ex) {
			componentAvailable = false;
			ex.printStackTrace();
		}
		return componentAvailable;
	}

}
