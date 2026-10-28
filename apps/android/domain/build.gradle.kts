plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.10.1")
}
tasks.test { systemProperty("vitalink.cases", rootProject.file("../../contracts/clinical-cases.json").absolutePath) }
tasks.test { testLogging { events("passed", "failed", "skipped") } }
