package org.vitalink.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vitalink.app.data.*
import org.vitalink.domain.*
import java.time.Instant
import java.util.UUID

data class Draft(val patientId: String = "", val systolic: String = "", val diastolic: String = "", val gestation: String = "", val symptoms: Set<Symptom> = emptySet()) {
    fun assessment() = Assessment(UUID.randomUUID().toString(), patientId.trim(), Instant.now().toString(), systolic.toIntOrNull(), diastolic.toIntOrNull(), gestation.toIntOrNull(), symptoms.toSet())
    fun errors(a: Assessment): List<String> = AssessmentValidation.errors(a) + buildList {
        if (systolic.isNotBlank() && systolic.toIntOrNull() == null) add("Systolic BP must be a whole number.")
        if (diastolic.isNotBlank() && diastolic.toIntOrNull() == null) add("Diastolic BP must be a whole number.")
        if (gestation.isNotBlank() && gestation.toIntOrNull() == null) add("Gestational age must be a whole number.")
    }
}
data class UiState(
    val draft: Draft = Draft(), val records: LocalRecords = LocalRecords(), val online: Boolean = false,
    val session: Session? = null, val busy: Boolean = false, val message: String? = null, val errors: List<String> = emptyList(),
    val storageError: Boolean = false,
    val recordsLoaded: Boolean = false,
    val savedAssessmentId: String? = null, val savedReferralId: String? = null,
)

class AssessmentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VitalinkApplication
    private val repo = app.repository
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private fun refreshConnectivity() {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        mutableState.update { it.copy(online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) }
    }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refreshConnectivity(); SyncWorker.enqueue(app) }
        override fun onLost(network: Network) { refreshConnectivity() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refreshConnectivity() }
    }
    init {
        refreshConnectivity()
        connectivity.registerDefaultNetworkCallback(callback)
        viewModelScope.launch {
            repo.records.catch { mutableState.update { it.copy(storageError = true, recordsLoaded = true, message = "Encrypted storage cannot be read. Data was not reset. Restore device access or use local clinical handoff procedures.") } }
                .collect { records -> mutableState.update { it.copy(records = records, recordsLoaded = true) } }
        }
        action {
            repo.recoverExpiredLeases()
            repo.purgeExpired()
            val session = app.sessionStore.read()
            mutableState.update { it.copy(session = session) }
            SyncWorker.enqueue(app)
        }
    }
    fun edit(transform: (Draft) -> Draft) { mutableState.update { it.copy(draft = transform(it.draft), errors = emptyList(), message = null) } }
    fun startNewAssessment() { mutableState.update { it.copy(draft = Draft(), errors = emptyList(), message = null) } }
    fun consumeSaveNavigation() { mutableState.update { it.copy(savedAssessmentId = null, savedReferralId = null) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null, errors = emptyList()) } }
    fun save(referral: Boolean) {
        val draft = state.value.draft
        val a = draft.assessment()
        val errors = draft.errors(a)
        if (errors.isNotEmpty()) { mutableState.update { it.copy(errors = errors) }; return }
        action {
            var referralId: String? = null
            if (referral) {
                val facility = state.value.session?.facilityId ?: "demo-facility"
                referralId = repo.createReferral(a, facility)
                SyncWorker.enqueue(app)
            } else repo.saveAssessment(a)
            mutableState.update { it.copy(draft = Draft(),
                savedAssessmentId = a.id, savedReferralId = referralId,
                message = if (referral) "Referral saved on this device. Delivery is unconfirmed until the server responds. For urgent findings, use the local escalation pathway now." else "Assessment saved securely on this device.") }
        }
    }
    fun refer(a: Assessment) = action {
        val referralId = repo.createReferral(a, state.value.session?.facilityId ?: "demo-facility")
        SyncWorker.enqueue(app)
        mutableState.update { it.copy(savedAssessmentId = a.id,
            savedReferralId = referralId, message = "Referral queued. This does not notify a clinician until delivery is confirmed.") }
    }
    fun login(username: String, password: String) = action {
        val session = repo.api.login(username, password)
        repo.unblockAfterLogin()
        mutableState.update { it.copy(session = session, message = "Signed in. Pending referrals will synchronize when connectivity is available.") }
        SyncWorker.enqueue(app)
    }
    fun logout() = action { repo.api.logout(); mutableState.update { it.copy(session = null, message = "Signed out. Local encrypted records remain available offline.") } }
    fun sync() = action {
        repo.synchronize()
        mutableState.update { it.copy(message = "Synchronization attempted. Inspect each referral's delivery and acknowledgment state. Periodic status polling may take up to 15 minutes; refresh manually for an immediate check.") }
        SyncWorker.enqueue(app)
    }
    fun deleteReferral(id: String) = action { repo.deleteReferral(id) }
    fun retryReferral(id: String) = action { repo.retryAfterReview(id); SyncWorker.enqueue(app) }
    fun deleteAssessment(id: String) = action { repo.deleteAssessment(id) }
    fun deleteAll() = action { repo.deleteAllLocalData(); mutableState.update { it.copy(session = null, message = "Local records and device session deleted. Server records are retained separately.") } }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (_: Exception) { mutableState.update { it.copy(message = "Operation could not complete. Check inputs, encrypted storage, credentials and connectivity. Existing committed records remain unchanged.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { connectivity.unregisterNetworkCallback(callback); super.onCleared() }
}
