package com.bluemob.app

import android.app.Application
import com.bluemob.app.chat.ChatRepository
import com.bluemob.app.contacts.ContactsStore
import com.bluemob.app.identity.Identity
import com.bluemob.app.location.LocationTracker
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.util.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Holds app-wide singletons so the mesh survives screen rotation and activity restarts. */
class BlueMobApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var identity: Identity
        private set
    lateinit var contacts: ContactsStore
        private set
    lateinit var mesh: NearbyMeshTransport
        private set
    lateinit var location: LocationTracker
        private set
    lateinit var chats: ChatRepository
        private set
    lateinit var connectivity: Connectivity
        private set

    override fun onCreate() {
        super.onCreate()
        identity = Identity(this)
        contacts = ContactsStore(this)
        mesh = NearbyMeshTransport(this, identity, contacts)
        location = LocationTracker(this)
        chats = ChatRepository(mesh, appScope)
        connectivity = Connectivity(this)

        // Share our GPS position with connected phones only while the user allows it.
        appScope.launch {
            identity.shareLocation.collect { share -> if (share) location.start() else location.stop() }
        }
        appScope.launch {
            combine(identity.shareLocation, location.location) { share, loc -> if (share) loc else null }
                .collect { mesh.updateMyLocation(it) }
        }
        appScope.launch {
            combine(identity.displayName, identity.avatar) { _, _ -> }.drop(1).collect { mesh.broadcastProfile() }
        }
    }
}
