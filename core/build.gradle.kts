import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kotlin")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}
dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
