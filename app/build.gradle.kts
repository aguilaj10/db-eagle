plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
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
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.dbeagle.AppKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "DBEagle"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/icon_512x512.png"))
            }
        }
    }
}
