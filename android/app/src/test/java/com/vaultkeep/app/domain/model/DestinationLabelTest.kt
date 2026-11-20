package com.vaultkeep.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationLabelTest {

    @Test
    fun `usb destination gets a fixed friendly label regardless of its uri`() {
        assertEquals("USB/SSD drive", destinationLabel("usb:content://com.android.externalstorage.documents/tree/XYZ"))
    }

    @Test
    fun `smb destination label includes the host`() {
        assertEquals("Home NAS (SMB) — 192.168.1.50", destinationLabel("smb:192.168.1.50"))
    }

    @Test
    fun `webdav destination label includes the host`() {
        assertEquals("Home NAS (WebDAV) — nas.local", destinationLabel("webdav:nas.local"))
    }

    @Test
    fun `cloud destination gets a fixed friendly label`() {
        assertEquals("Void Cloud", destinationLabel("cloud:void"))
    }

    @Test
    fun `drive destination gets a fixed friendly label`() {
        assertEquals("Google Drive", destinationLabel("drive:void"))
    }

    @Test
    fun `unknown prefix falls back to the raw id instead of crashing`() {
        assertEquals("something:else", destinationLabel("something:else"))
    }
}
