plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.geometry)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.composeIcons.fontAwesome)
    implementation(compose.desktop.currentOs)

    implementation("com.github.qawaz.compose-code-editor:codeeditor-desktop:3.1.1")

    implementation(libs.koin.core)
    implementation(libs.coroutines.swing)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.multiplatform.settings)
    testImplementation(libs.multiplatform.settings.coroutines)
    testImplementation(libs.multiplatform.settings.test)
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
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
            )
            packageName = "DBEagle"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))

                // Bundle identifier (required for macOS packaging)
                bundleID = "com.dbeagle.app"

                // DMG customization
                dockName = "DBEagle"

                // Optional code signing (requires Apple Developer ID certificate)
                // Set these via environment variables or gradle.properties:
                // - DBEAGLE_MAC_SIGN_IDENTITY: Developer ID certificate name
                // - DBEAGLE_MAC_KEYCHAIN: Path to keychain (optional, uses default if not set)
                val signIdentity =
                    System.getenv("DBEAGLE_MAC_SIGN_IDENTITY")
                        ?: findProperty("dbeagle.mac.sign.identity") as? String

                if (signIdentity != null) {
                    signing {
                        sign.set(true)
                        identity.set(signIdentity)

                        val keychainPath =
                            System.getenv("DBEAGLE_MAC_KEYCHAIN")
                                ?: findProperty("dbeagle.mac.keychain") as? String
                        if (keychainPath != null) {
                            keychain.set(keychainPath)
                        }
                    }
                }

                // Optional notarization (requires signing + Apple ID credentials)
                // Set these via environment variables or gradle.properties:
                // - DBEAGLE_APPLE_ID: Apple ID email
                // - DBEAGLE_APPLE_TEAM_ID: 10-character team ID
                // - DBEAGLE_APPLE_APP_PASSWORD: App-specific password (from appleid.apple.com)
                val appleId =
                    System.getenv("DBEAGLE_APPLE_ID")
                        ?: findProperty("dbeagle.apple.id") as? String
                val appleTeamId =
                    System.getenv("DBEAGLE_APPLE_TEAM_ID")
                        ?: findProperty("dbeagle.apple.team.id") as? String
                val appleAppPassword =
                    System.getenv("DBEAGLE_APPLE_APP_PASSWORD")
                        ?: findProperty("dbeagle.apple.app.password") as? String

                if (signIdentity != null && appleId != null && appleTeamId != null && appleAppPassword != null) {
                    notarization {
                        appleID.set(appleId)
                        password.set(appleAppPassword)
                        teamID.set(appleTeamId)
                    }
                }
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))

                // MSI installer configuration
                menuGroup = "DBEagle"
                upgradeUuid = "3e4f5c6b-7d8a-9b1c-2d3e-4f5a6b7c8d9e"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/icon_512x512.png"))
            }
        }
    }
}
