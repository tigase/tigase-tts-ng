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
package tigase.tests.utils;

import java.util.Collections;
import java.util.Map;

public class VHost {

	private final VHostManager manager;
	private final String domain;
	private final Map<String, Object> parameters;

	public VHost(VHostManager manager, String domain, Map<String, Object> parameters) {
		this.manager = manager;
		this.domain = domain;
		this.parameters = Collections.unmodifiableMap(parameters);
	}
	
}
