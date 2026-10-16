plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":engine"))
    implementation(project(":data"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(project(":engine"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
