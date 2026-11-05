package com.vaultkeep.app.domain

import com.vaultkeep.app.domain.model.NasConfig
import com.vaultkeep.app.domain.model.NasProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class NasConfigTest {

    @Test
    fun `displayName includes share when present`() {
        val config = NasConfig(
            protocol = NasProtocol.SMB,
            host = "192.168.1.50",
            port = 445,
            shareOrPath = "backups",
            username = "u",
            password = "p",
        )
        assertEquals("192.168.1.50/backups", config.displayName())
    }

    @Test
    fun `displayName omits trailing slash when share is blank`() {
        val config = NasConfig(
            protocol = NasProtocol.WEBDAV,
            host = "nas.local",
            port = 443,
            shareOrPath = "",
            username = "u",
            password = "p",
        )
        assertEquals("nas.local", config.displayName())
    }
}
