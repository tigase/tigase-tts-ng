/*
 * VHostBuilder.java
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

package tigase.tests.utils;

import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.HashMap;

public class VHostBuilder {

	private final VHostManager manager;
	private final String domain;
	private boolean updateIfExists = false;

	private final HashMap<String,Object> parameters = new HashMap<>();

	public VHostBuilder(VHostManager manager, String domain) {
		this.manager = manager;
		this.domain = domain;
	}

	public VHostBuilder setClientCertCA(String path) {
		parameters.put("client-trust-extension-ca-cert-path", path);
		return this;
	}

	public VHostBuilder setClientCertRequired(boolean value) {
		parameters.put("client-trust-extension-cert-required", value ? "true" : "false");
		return this;
	}

	public VHostBuilder updateIfExists(boolean value) {
		updateIfExists = value;
		return this;
	}

	public void build() throws JaxmppException, InterruptedException {
		manager.addVHost(domain, parameters, updateIfExists);
	}
}
