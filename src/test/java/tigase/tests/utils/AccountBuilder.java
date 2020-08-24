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

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.tests.AbstractTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by andrzej on 22.04.2017.
 */
public class AccountBuilder {

	private final AbstractTest test;
	private String domain;
	private String email;
	private String logPrefix;
	private String password;
	private boolean register = true;
	private String username;
	private List<Consumer<Account>> registrationSuccessHandlers = new ArrayList<>();
	private List<Consumer<Account>> unregistrationHandlers = new ArrayList<>();

	public AccountBuilder(AbstractTest abstractTest) {
		this.test = abstractTest;
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	public AccountBuilder setLogPrefix(String logPrefix) {
		this.logPrefix = logPrefix;
		return this;
	}

	public AccountBuilder setRegister(boolean register) {
		this.register = register;
		return this;
	}

	public String getUsername() {
		return username;
	}

	public AccountBuilder setUsername(String username) {
		this.username = username;
		return this;
	}

	public String getPassword() {
		return password;
	}

	public AccountBuilder setPassword(String password) {
		this.password = password;
		return this;
	}

	public String getDomain() {
		return domain;
	}

	public AccountBuilder setDomain(String domain) {
		this.domain = domain;
		return this;
	}

	public String getEmail() {
		return email;
	}

	public AccountBuilder setEmail(String email) {
		this.email = email;
		return this;
	}

	public AccountBuilder addRegistrationSuccessHandler(Consumer<Account> registrationSuccessHandler) {
		this.registrationSuccessHandlers.add(registrationSuccessHandler);
		return this;
	}

	public AccountBuilder addUnregistrationHandler(Consumer<Account> unregistrationHandler) {
		this.unregistrationHandlers.add(unregistrationHandler);
		return this;
	}

	public Account build() throws JaxmppException, InterruptedException {
		if (domain == null) {
			domain = test.getDomain(0);
		}
		if (username == null) {
			username = logPrefix + "_" + AbstractTest.nextRnd();
		}
		if (password == null) {
			password = username;
		}
		if (email == null) {
			email = test.getEmailAccountForUser(username).email;
		}

		Account account = new Account(test, logPrefix, BareJID.bareJIDInstance(username, domain), password, this.unregistrationHandlers);
		if (register) {
			Account acc = test.accountManager.registerAccount(this, account);
			registrationSuccessHandlers.forEach(handler -> handler.accept(acc));
			return acc;
		}

		return account;
	}

}
