plugins {
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}

subprojects { apply(plugin = "org.jlleitschuh.gradle.ktlint") }
detekt {
    source.setFrom(files("app/src/main/java"))
    config.setFrom(files("config/detekt.yml"))
}
