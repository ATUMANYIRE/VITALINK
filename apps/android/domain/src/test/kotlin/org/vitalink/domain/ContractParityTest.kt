package org.vitalink.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ContractParityTest {
    @Test fun sharedClinicalCasesMatchNativeRules() {
        val cases = JsonParser.parseString(File(System.getProperty("vitalink.cases")).readText()).asJsonArray
        assertEquals(16, cases.size())
        cases.forEach { element ->
            val j = element.asJsonObject
            val s = if (j["systolic"].isJsonNull) null else j["systolic"].asInt
            val d = if (j["diastolic"].isJsonNull) null else j["diastolic"].asInt
            val symptoms = j["symptoms"].asJsonArray.map { Symptom.valueOf(it.asString) }.toSet()
            val findings = ClinicalRules.assess(s, d, symptoms)
            assertEquals(j["name"].asString, j["urgency"].asString, findings.urgency.name)
            assertEquals(j["name"].asString, j["ruleTriggers"].asJsonArray.map { it.asString }.toSet(), findings.triggers.toSet())
        }
    }
}
