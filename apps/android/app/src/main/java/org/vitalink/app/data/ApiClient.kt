package org.vitalink.app.data

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.vitalink.app.BuildConfig
import java.time.Instant
import java.util.concurrent.TimeUnit

data class Session(val token: String, val expiresAt: String, val username: String, val role: String, val facilityId: String)

class SessionStore(context: Context, private val cipher: PayloadCipher, name: String = "secure-session") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun save(raw: String) {
        require(preferences.edit().putString("encrypted", Base64.encodeToString(cipher.encrypt(raw, "auth"), Base64.NO_WRAP)).commit()) { "Session persistence failed" }
    }
    fun read(): Session? {
        val blob = preferences.getString("encrypted", null) ?: return null
        val j = JSONObject(cipher.decrypt(Base64.decode(blob, Base64.NO_WRAP), "auth"))
        val user = j.getJSONObject("user")
        val session = Session(j.getString("token"), j.getString("expiresAt"), user.getString("username"), user.getString("role"), user.getString("facilityId"))
        if (Instant.parse(session.expiresAt) <= Instant.now()) { clear(); return null }
        return session
    }
    fun clear() { check(preferences.edit().clear().commit()) { "Session removal failed" } }
}

data class HttpResult(val code: Int, val body: String)
class ApiClient(val sessions: SessionStore) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun login(username: String, password: String): Session {
        val body = JSONObject().put("username", username).put("password", password).toString()
        val r = execute(Request.Builder().url("${BuildConfig.API_BASE_URL}/auth/login").post(body.toRequestBody(jsonType)).build())
        require(r.code == 200) { "Sign-in failed. Check credentials and connectivity." }
        val j = JSONObject(r.body)
        val user = j.getJSONObject("user")
        require(user.getString("role") in setOf("HEALTH_WORKER", "ADMIN")) { "A health-worker account is required for this device." }
        require(j.getString("token").length >= 24 && Instant.parse(j.getString("expiresAt")) > Instant.now()) { "Invalid authentication response" }
        sessions.save(r.body)
        return requireNotNull(sessions.read())
    }
    fun logout() {
        val session = sessions.read()
        try { if (session != null) execute(request("/auth/logout", session).post("".toRequestBody(jsonType)).build()) }
        finally { sessions.clear() }
    }
    fun submit(id: String, raw: String, session: Session): HttpResult = execute(request("/sync/referrals", session)
        .header("Idempotency-Key", id).post(raw.toRequestBody(jsonType)).build())
    fun status(id: String, session: Session) = execute(request("/referrals/$id", session).get().build())
    private fun request(path: String, session: Session) = Request.Builder().url(BuildConfig.API_BASE_URL + path)
        .header("Authorization", "Bearer ${session.token}").header("Accept", "application/json")
    private fun execute(request: Request): HttpResult = client.newCall(request).execute().use {
        val body = it.body ?: return@use HttpResult(it.code, "")
        require(body.contentLength() <= 1_048_576L) { "Response too large" }
        val source = body.source()
        source.request(1_048_577L)
        require(source.buffer.size <= 1_048_576L) { "Response too large" }
        HttpResult(it.code, source.readUtf8())
    }
}
