import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

dependencies {
    kover(project(":core"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.dbeagle.AppKt",
                    "com.dbeagle.App*",
                    "com.dbeagle.di.*",
                    "com.dbeagle.edit.*",
                    "com.dbeagle.export.*",
                    "com.dbeagle.model.*",
                    "com.dbeagle.*Module",
                    "*.ComposableSingletons*",
                    "*\$\$serializer*",
                    "*\$Companion",
                )
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")

    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_function-naming" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}
