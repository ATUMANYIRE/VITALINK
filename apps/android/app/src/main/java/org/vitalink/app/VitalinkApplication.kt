package org.vitalink.app

import android.app.Application
import androidx.room.Room
import org.vitalink.app.data.*

class VitalinkApplication : Application() {
    val cipher by lazy { PayloadCipher() }
    val sessionStore by lazy { SessionStore(this, cipher) }
    val database by lazy {
        Room.databaseBuilder(this, VitalinkDatabase::class.java, "vitalink-encrypted-payloads.db")
            .addMigrations(VitalinkDatabase.MIGRATION_1_2).build()
    }
    val repository by lazy { ReferralRepository(database, cipher, ApiClient(sessionStore)) }
    override fun onCreate() { super.onCreate(); SyncWorker.schedule(this) }
}
