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
import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.builder.socketConnector
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.xmpp.BareJID
import tigase.jaxmpp.core.client.xmpp.modules.auth.ClientSaslException
import java.util.*
import tigase.halcyon.core.configuration.JIDPasswordAuthConfigBuilder
import tigase.halcyon.core.connector.ConnectionErrorEvent
import tigase.halcyon.core.connector.ConnectorStateChangeEvent

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

fun createHalcyonAdmin(): Halcyon {
	val props = loadProperties()
	return createHalcyon {
		val domains = props.getProperty("server.domains")
			.split(",")
		auth {
			userJID = BareJID(
				props.getProperty("test.admin.username") ?: "admin", props.getProperty("test.admin.domain", domains[0])
			)
			password { props.getProperty("test.admin.password") ?: props.getProperty("test.admin.username") ?: "admin" }
		}
		socketConnector {
			hostname = props.getProperty("server.cluster.nodes")
				.split(",")
				.random()
		}
	}.also { h ->
        h.eventBus.register<ConnectorStateChangeEvent>(ConnectorStateChangeEvent.TYPE) { TestLogger.log(" >> ${it.toString()} : from ${it.oldState} to ${it.newState}" ) }
        h.eventBus.register<ConnectionErrorEvent>(ConnectionErrorEvent.TYPE) { TestLogger.log(" >> ${it.toString()}") }
		h.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { TestLogger.log(" >> ${it.element.getAsString()}") }
		h.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { TestLogger.log(" << ${it.element.getAsString()}") }
	}
}

fun ensureAdminAccountExists() {
	val props = loadProperties()
	val domains = props.getProperty("server.domains")
		.split(",")
	val user =
		BareJID(props.getProperty("test.admin.username") ?: "admin", props.getProperty("test.admin.domain", domains[0]))
	val password = props.getProperty("test.admin.password") ?: props.getProperty("test.admin.username") ?: "admin"
	val host = props.getProperty("server.cluster.nodes")
		.split(",")
		.random()
	val halcyon = createHalcyon {
		auth {
			userJID = user
			password { password }
		}
		socketConnector {
			hostname = host
		}
	}.also { h ->
		h.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { TestLogger.log(" >> ${it.element.getAsString()}") }
		h.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { TestLogger.log(" << ${it.element.getAsString()}") }
	}

	try {
		halcyon.connectAndWait()
	} catch (e: ClientSaslException) {
		val crh=createHalcyon {
			register {
				domain = user.domain
				registrationHandler { form ->
					form.getFieldByVar("username")!!.fieldValue = user.localpart
					form.getFieldByVar("password")!!.fieldValue = password
					form.getFieldByVar("email")!!.fieldValue = user.toString()
					form
				}
			}
			socketConnector {
				hostname = host
			}
		}.also { h ->
			h.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { TestLogger.log(" >> ${it.element.getAsString()}") }
			h.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { TestLogger.log(" << ${it.element.getAsString()}") }
		}
		crh.connectAndWait()
		crh.waitForAllResponses()
		crh.disconnect()
	}

}