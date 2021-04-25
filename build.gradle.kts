import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.namics.oss.gradle.license"
description = "Gradle plugin enforces licenses of dependencies to comply with definitions."

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.14.0"
    `java-gradle-plugin`
    id("de.gliderpilot.semantic-release") version "1.4.1"
    id("com.github.hierynomus.license-base") version "0.15.0"
    id("com.github.ben-manes.versions") version "0.38.0"
    id("io.gitlab.arturbosch.detekt") version "1.16.0"
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))

    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.dom4j:dom4j:2.1.3")
    implementation("xerces:xercesImpl:2.12.1")
    implementation("org.yaml:snakeyaml:1.28")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
}

repositories {
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") {
        content {
            includeGroup("org.jetbrains.kotlinx")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    allRules = true
    config = files(projectDir.resolve("detekt.yml"))
    reports {
        txt.enabled = false
    }
}

gradlePlugin {
    plugins {
        register("com.namics.oss.gradle.license-enforce-plugin") {
            id = "com.namics.oss.gradle.license-enforce-plugin"
            implementationClass = "com.namics.oss.gradle.license.LicenseEnforcePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/namics/gradle-license-enforce-plugin"
    vcsUrl = "https://github.com/namics/gradle-license-enforce-plugin"
    description = project.description
    tags = listOf("dependency-management", "license", "enforce")
    (plugins) {
        "com.namics.oss.gradle.license-enforce-plugin" {
            // id is captured from java-gradle-plugin configuration
            displayName = "Gradle dependency licenses enforcement plugin"
            description = "Under development! Gradle dependency licenses enforcement plugin"
            tags = listOf("dependency-management", "license", "enforce")
            version = project.version.toString()
        }
    }
    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
        version = project.version.toString()
    }
}

if (!version.toString().endsWith("-SNAPSHOT")) {
    tasks.getByName("release") {
        finalizedBy("publishPlugins")
    }
}

tasks.create("licenseHeader") {
    dependsOn("licenseFormatMain", "licenseFormatTest")
}

tasks {
    withType(KotlinCompile::class) {
        kotlinOptions {
            javaParameters = true
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }

    withType<Test> {
        useJUnitPlatform()
    }

    dependencyUpdates {
        resolutionStrategy {
            componentSelection {
                all {
                    if (candidate.group.startsWith("org.jetbrains.kotlin")) {
//                        println("$candidate")
                        if (candidate.version != embeddedKotlinVersion) {
                            reject("Only the in Gradle embedded Kotlin version is allowed")
                        }
                    }

                    val rc = listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "pr").any { qualifier ->
                        candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
                    }

                    if (rc) {
                        reject("Release candidate")
                    }
                }
            }
        }
        checkConstraints = true
        checkForGradleUpdate = true
        revision = "release"
    }

    check {
        dependsOn(dependencyUpdates)
    }
}
