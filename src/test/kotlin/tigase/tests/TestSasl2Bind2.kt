package tigase.tests

import org.testng.Assert.*
import org.testng.annotations.Test
import tigase.TestLogger
import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.State
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.toJID
import java.util.*

@OptIn(ReflectionModuleManager::class)
class Sasl2Bind2Test {

	@Test
	fun test_sasl1_connection() {
		TestLogger.log("Check SASL")
		val halcyon = createHalcyonAdmin()
		halcyon.getModule<SASLModule>().enabled = true
		halcyon.getModule<SASL2Module>().enabled = false
		halcyon.connectAndWait()
		assertEquals(AbstractHalcyon.State.Connected, halcyon.state, "Client should be connected to server.")
		assertEquals(State.Success, halcyon.authContext.state, "Client should be authenticated.")
		assertNotNull(halcyon.boundJID, "Client session should be bound.")

		var serverFeatures: List<String> = emptyList()
		halcyon.getModule<DiscoveryModule>()
			.info(halcyon.boundJID!!.domain.toJID())
			.response {
				it.onSuccess { info ->
					serverFeatures = info.features
				}
			}
			.send()


		halcyon.waitForAllResponses()
		assertTrue(serverFeatures.isNotEmpty(),"Not received requested server features.")

		halcyon.disconnect()
		assertEquals(AbstractHalcyon.State.Stopped, halcyon.state, "Client should be stopped.")
	}

	@Test
	fun test_sasl2_connection() {
		TestLogger.log("Check SASL2 & Bind2")
		val halcyon = createHalcyonAdmin()
		halcyon.getModule<SASLModule>().enabled = false
		halcyon.getModule<SASL2Module>().enabled = true
		halcyon.connectAndWait()
		assertEquals(AbstractHalcyon.State.Connected, halcyon.state, "Client should be connected to server.")
		assertEquals(State.Success, halcyon.authContext.state, "Client should be authenticated.")
		assertNotNull(halcyon.boundJID, "Client session should be bound.")

		var serverFeatures: List<String> = emptyList()
		halcyon.getModule<DiscoveryModule>()
			.info(halcyon.boundJID!!.domain.toJID())
			.response {
				it.onSuccess { info ->
					serverFeatures = info.features
				}
			}
			.send()


		halcyon.waitForAllResponses()
		assertTrue(serverFeatures.isNotEmpty(),"Not received requested server features.")

		halcyon.disconnect()
		assertEquals(AbstractHalcyon.State.Stopped, halcyon.state, "Client should be stopped.")
	}

}