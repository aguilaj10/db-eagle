import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kover)
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
                    "*\$Companion"
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
