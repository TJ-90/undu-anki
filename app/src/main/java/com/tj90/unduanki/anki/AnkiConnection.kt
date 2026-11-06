package com.tj90.unduanki.anki

enum class ConnectionState { NOT_INSTALLED, VERSION_NOT_VERIFIED, PERMISSION_NEEDED, NOT_READY, WORKING }

object AnkiConnection {
    /** Releases whose provider behavior is proven by docs/anki-compatibility.md. Anything else stays blocked. */
    val VERIFIED_VERSIONS: Set<String> = setOf("2.25.3")

    fun classify(installedVersion: String?, permissionGranted: Boolean, collectionReadable: Boolean): ConnectionState = when {
        installedVersion == null -> ConnectionState.NOT_INSTALLED
        installedVersion !in VERIFIED_VERSIONS -> ConnectionState.VERSION_NOT_VERIFIED
        !permissionGranted -> ConnectionState.PERMISSION_NEEDED
        !collectionReadable -> ConnectionState.NOT_READY
        else -> ConnectionState.WORKING
    }

    fun explain(state: ConnectionState, version: String?): String = when (state) {
        ConnectionState.NOT_INSTALLED -> "AnkiDroid is not installed. Install the official AnkiDroid, open it once, then come back."
        ConnectionState.VERSION_NOT_VERIFIED ->
            "AnkiDroid $version has not been tested with this app. Study stays off; this app is verified with AnkiDroid ${VERIFIED_VERSIONS.joinToString()}."
        ConnectionState.PERMISSION_NEEDED -> "This app needs permission to read and answer cards in AnkiDroid."
        ConnectionState.NOT_READY ->
            "AnkiDroid is installed but not ready. Open AnkiDroid, finish its first-time setup, and make sure its API is enabled in Settings, then try again."
        ConnectionState.WORKING -> "Connected to AnkiDroid $version."
    }
}
