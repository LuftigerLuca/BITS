plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

application {
    mainClass = "eu.luftiger.bits.bot.MainKt"
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.jda) {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.lettuce.core)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.mockk)
}
