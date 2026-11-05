package com.vaultkeep.app.data.local

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.vaultkeep.app.domain.model.DiscoveredNasHost
import com.vaultkeep.app.domain.model.NasProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-network discovery via Android's NsdManager (mDNS/Bonjour). Finds
 * advertised SMB and WebDAV services; manual host/port entry in the setup
 * screen always works too, since not every NAS advertises itself (or the
 * discovery window closes before it's found).
 */
@Singleton
class NasDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    fun discover(): Flow<DiscoveredNasHost> = callbackFlow {
        val listeners = listOf(
            "_smb._tcp." to NasProtocol.SMB,
            "_webdav._tcp." to NasProtocol.WEBDAV,
            "_http._tcp." to NasProtocol.WEBDAV,
        ).map { (serviceType, protocol) ->
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            trySend(
                                DiscoveredNasHost(
                                    name = serviceInfo.serviceName,
                                    host = serviceInfo.host?.hostAddress ?: return,
                                    port = serviceInfo.port,
                                    protocol = protocol,
                                ),
                            )
                        }
                    })
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            }
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            discoveryListener
        }

        awaitClose {
            listeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
        }
    }
}
