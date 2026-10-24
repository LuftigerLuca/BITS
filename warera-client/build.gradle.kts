plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("org.springframework:spring-web")
    implementation(libs.kotlinx.serialization.json)
    // Part of the constructor of RestWareraClient.
    api(libs.resilience4j.ratelimiter)
    api(libs.resilience4j.retry)
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework:spring-test")
}
