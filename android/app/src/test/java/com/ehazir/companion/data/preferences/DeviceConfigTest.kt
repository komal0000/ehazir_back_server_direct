package com.ehazir.companion.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceConfigTest {

    @Test
    fun configuredReaders_migratesLegacyCommaSeparatedHosts() {
        val config = DeviceConfig(
            serverHost = "192.168.1.20, 192.168.1.21",
            serverPort = "2022"
        )

        assertEquals(
            listOf(
                RfidReaderConfig("192.168.1.20", "2022"),
                RfidReaderConfig("192.168.1.21", "2022")
            ),
            config.configuredReaders()
        )
    }

    @Test
    fun configuredReaders_prefersStructuredPerReaderPorts() {
        val config = DeviceConfig(
            serverHost = "legacy-host",
            serverPort = "2022",
            rfidReaders = listOf(
                RfidReaderConfig(" reader-one ", " 2022 "),
                RfidReaderConfig("reader-two", "3030")
            )
        )

        assertEquals(
            listOf(
                RfidReaderConfig("reader-one", "2022"),
                RfidReaderConfig("reader-two", "3030")
            ),
            config.configuredReaders()
        )
    }
}
