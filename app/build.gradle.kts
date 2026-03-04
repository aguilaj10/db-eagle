plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.geometry)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(compose.desktop.currentOs)
    
    implementation(libs.kodeview)

    implementation(libs.koin.core)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.dbeagle.AppKt")
}
