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
