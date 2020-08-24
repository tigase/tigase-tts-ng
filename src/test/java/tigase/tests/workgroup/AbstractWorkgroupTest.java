/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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
package tigase.tests.workgroup;

import org.testng.IHookable;
import tigase.jaxmpp.core.client.JID;
import tigase.tests.AbstractSkippableTest;

public class AbstractWorkgroupTest
		extends AbstractSkippableTest
		implements IHookable {

	@Override
	protected JID getComponentJID() {
		return JID.jidInstance("wg." + getDomain(0));
	}

	@Override
	protected String getComponentName() {
		return "WorkGroup";
	}
}
