/*
 * DotTestListener.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
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
 *
 */
package tigase;

import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import static tigase.TestLogger.log;

public class DotTestListener
		extends TestListenerAdapter {

	@Override
	public void onStart(ITestContext testContext) {
		super.onStart(testContext);

		log("");
		log("Running: " + testContext.getName());
		log("------------------------------------");
	}

	@Override
	public void onTestStart(ITestResult result) {
		super.onTestStart(result);

		log("");
		log(result.getTestName() + " / " + result.getTestClass());
		log("------------------------------------");

	}

}
