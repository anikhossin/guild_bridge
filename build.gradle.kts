plugins {
    kotlin("jvm") version "2.4.20"
    id("net.fabricmc.fabric-loom") version "1.17.17"
}

fun prop(name: String): String = property(name) as String

version = "${prop("mod_version")}-mc${prop("minecraft_version")}"
group = prop("maven_group")

base {
    archivesName.set("guild-bridge")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
    implementation(include("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")!!)

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

tasks.processResources {
    val modVersion = project.version.toString()
    val minecraftVersion = prop("minecraft_version")
    inputs.property("version", modVersion)
    inputs.property("minecraft_version", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to modVersion,
                "minecraft_version" to minecraftVersion,
            ),
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

tasks.test {
    useJUnitPlatform()
}
