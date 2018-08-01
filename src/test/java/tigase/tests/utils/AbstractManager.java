/*
 * AbstractManager.java
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

import tigase.tests.AbstractTest;

public abstract class AbstractManager {

	protected final AbstractTest test;

	public AbstractManager(AbstractTest test) {
		this.test = test;
	}

	public void scopeFinished() {
		Object key = getScopeKey();
		scopeFinished(key);
	}

	protected abstract void scopeFinished(Object scope);

	protected Object getScopeKey() {
		Object key;
		key = test.CURRENT_METHOD.get();
		if (key == null) {
			key = test.CURRENT_CLASS.get();
			if (key == null) {
				key = test.CURRENT_SUITE.get();
			}
		}

		return key;
	}

}
