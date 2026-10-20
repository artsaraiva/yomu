plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

detekt {
    source.setFrom(fileTree(rootDir) {
        include("app/src/**/*.kt", "core/src/**/*.kt", "ml/src/**/*.kt", "pipeline/src/**/*.kt")
    })
    config.setFrom("config/detekt/detekt.yml")
}
