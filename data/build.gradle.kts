plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
}

sqldelight {
    databases {
        create("TrailmarkDb") {
            packageName.set("dev.toufik.trailmark.data.db")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":engine"))
    implementation(libs.sqldelight.runtime)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
