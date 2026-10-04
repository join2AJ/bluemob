package com.bluemob.app

import android.app.Application
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.NearbyMeshTransport

/** Holds app-wide singletons so the mesh survives screen rotation and activity restarts. */
class BlueMobApp : Application() {
    lateinit var identity: Identity
        private set
    lateinit var mesh: NearbyMeshTransport
        private set

    override fun onCreate() {
        super.onCreate()
        identity = Identity(this)
        mesh = NearbyMeshTransport(this, identity)
    }
}
