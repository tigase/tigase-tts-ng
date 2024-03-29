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
package tigase;

import org.testng.Reporter;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TestLogger {

	private static SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public static void log(String s) {
		log(s, true);
	}

	public static void log(String s, Object[] params) {
		log(s, true, params);
	}

	public static void log(String s, boolean newline) {
		log(s, newline, null);
	}

	public static void log(String s, boolean newline, Object[] params) {
		String date = dt.format(new Date()) + " | ";
		if (params != null) {
			s = java.text.MessageFormat.format(s, params);
		}
//		System.out.println(date + s);
		Reporter.log(date + s + (newline ? "\n" : ""));
//		Reporter.log( escapeHtml4( s ) );
	}

}
