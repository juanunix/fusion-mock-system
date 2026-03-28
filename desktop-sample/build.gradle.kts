plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
    jvm()
    
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":ktor"))
                implementation("io.ktor:ktor-client-mock:2.3.5")
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-cio:2.3.5") // Engine for JVM
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.fusion.mock.sample.desktop.MainKt"
    }
}
