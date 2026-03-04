plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.jdbc)

    implementation(libs.postgresql)
    implementation(libs.sqlite.jdbc)

    implementation(libs.hikaricp)

    implementation(libs.koin.core)

    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.slf4j.simple)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgres)
}

tasks.test {
    useJUnitPlatform()
}
