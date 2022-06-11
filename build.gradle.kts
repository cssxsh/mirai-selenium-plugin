plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"

    id("net.mamoe.mirai-console") version "2.11.1"
    id("net.mamoe.maven-central-publish") version "0.7.1"
}

group = "xyz.cssxsh.mirai"
version = "2.1.1"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("cssxsh", "mirai-selenium-plugin")
    licenseFromGitHubProject("AGPL-3.0", "master")
    publication {
        artifact(tasks.getByName("buildPlugin"))
        artifact(tasks.getByName("buildPluginLegacy"))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api("org.seleniumhq.selenium:selenium-java:4.2.2") {
        exclude("org.slf4j")
        exclude("io.netty")
        exclude("com.google.auto.service")
    }
    compileOnly("com.google.auto.service:auto-service:1.0.1")
    compileOnly("net.mamoe:mirai-core-utils:2.11.1")
    compileOnly("me.him188:kotlin-jvm-blocking-bridge-runtime-jvm:2.0.0-162.1")

    testImplementation(kotlin("test", "1.6.21"))
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

kotlin {
    explicitApi()
}

tasks {
    test {
        useJUnitPlatform()
    }
}