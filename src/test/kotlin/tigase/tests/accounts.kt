/**
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
package tigase.tests

import tigase.TestLogger
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.xmpp.BareJID
import java.util.*

fun loadProperties(): Properties {
	val props = Properties()
	AbstractTest::class.java.getResource("/server.properties")!!
		.openStream()
		.use {
			props.load(it)
		}
	System.getProperties()
		.stringPropertyNames()
		.filter { it.matches(Regex("(?u)^(imap|test|server)[.].*")) }
		.forEach {
			props.setProperty(
				it,
				System.getProperties()
					.getProperty(it)
			)
		}
	return props
}

fun createHalcyonAdmin(): Halcyon = Halcyon().also { h ->
	loadProperties().let { props ->
		val domains = props.getProperty("server.domains")
			.split(",")
		h.configure {
			val d = props.getProperty("test.admin.domain", domains[0])
			userJID = BareJID(props.getProperty("test.admin.username") ?: "admin", d)
			password = props.getProperty("test.admin.password") ?: props.getProperty("test.admin.username") ?: "admin"
		}
	}
	h.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { TestLogger.log(" >> ${it.element.getAsString()}") }
	h.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { TestLogger.log(" << ${it.element.getAsString()}") }
}
