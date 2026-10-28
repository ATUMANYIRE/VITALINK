package org.vitalink.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleBenchmarkTest {
    @Test fun measureHostJvmRuleLatency() {
        var sink = 0
        repeat(10_000) { sink += ClinicalRules.assess(160, 90, emptySet()).triggers.size }
        val samples = LongArray(5_000) {
            val start = System.nanoTime()
            sink += ClinicalRules.assess(160, 90, emptySet()).triggers.size
            System.nanoTime() - start
        }.sorted()
        assertTrue(sink > 0)
        val result = """{"measurement":"host-jvm-rules-only","unit":"nanoseconds","warmup":10000,"samples":5000,"median":${samples[2500]},"p95":${samples[4750]},"jvm":"${System.getProperty("java.version")}","os":"${System.getProperty("os.name")}","arch":"${System.getProperty("os.arch")}","processors":${Runtime.getRuntime().availableProcessors()},"deviceBenchmark":false}"""
        File("build/reports/rule-benchmark.json").apply { parentFile.mkdirs(); writeText(result) }
        println(result)
    }
}
