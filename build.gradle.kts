import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    java
    kotlin("jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow").version("7.1.2")
    `java-library`
    `maven-publish`
}
group = "com.wang-lab"
version = "0.2.0-alpha-pre-release"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("gov.nist.math:jama:1.0.3")
    implementation("ch.obermuhlner:big-math:2.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
}
java {
    withJavadocJar()
    withSourcesJar()
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(8))
    }
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
    manifest.attributes.apply {
        put("Implementation-Version", archiveVersion)
        put("Main-Class", "com.wang_lab.mkm_core.Main")
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
