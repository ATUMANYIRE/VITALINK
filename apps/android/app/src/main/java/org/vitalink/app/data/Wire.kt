package org.vitalink.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.vitalink.domain.*

object Wire {
    fun assessment(a: Assessment) = JSONObject().put("id", a.id).put("patientId", a.patientId)
        .put("measuredAt", a.measuredAt).put("systolic", a.systolic ?: JSONObject.NULL)
        .put("diastolic", a.diastolic ?: JSONObject.NULL).put("gestationalWeeks", a.gestationalWeeks ?: JSONObject.NULL)
        .put("symptoms", JSONArray(a.symptoms.sortedBy { it.name }.map { it.name }))
        .put("source", a.source).put("ruleVersion", a.ruleVersion)

    fun parseAssessment(j: JSONObject): Assessment {
        val values = j.getJSONArray("symptoms")
        val symptoms = (0 until values.length()).map { Symptom.valueOf(values.getString(it)) }
        require(symptoms.distinct().size == symptoms.size) { "Duplicate symptom" }
        return Assessment(j.getString("id"), j.getString("patientId"), j.getString("measuredAt"),
            nullableInt(j, "systolic"), nullableInt(j, "diastolic"), nullableInt(j, "gestationalWeeks"),
            symptoms.toSet(), j.getString("source"), j.getString("ruleVersion"))
    }

    private fun nullableInt(j: JSONObject, key: String): Int? {
        require(j.has(key)) { "Missing response field" }
        if (j.isNull(key)) return null
        val n = j.get(key)
        require(n is Number && n.toDouble().isFinite() && n.toDouble() == n.toInt().toDouble()) { "Invalid integer" }
        return n.toInt()
    }

    fun request(id: String, facilityId: String, a: Assessment) = JSONObject()
        .put("id", id).put("facilityId", facilityId).put("assessment", assessment(a)).toString()

    fun receipt(raw: String): ServerReceipt {
        val j = JSONObject(raw)
        val triggers = j.getJSONArray("ruleTriggers")
        return ServerReceipt(j.getString("id"), j.getString("facilityId"), parseAssessment(j.getJSONObject("assessment")),
            ReferralState.valueOf(j.getString("status")), Urgency.valueOf(j.getString("urgency")),
            (0 until triggers.length()).map { triggers.getString(it) }, j.getString("receivedAt"),
            if (j.isNull("acknowledgedAt")) null else j.getString("acknowledgedAt"),
            if (j.isNull("acknowledgedBy")) null else j.getString("acknowledgedBy"))
    }
}
