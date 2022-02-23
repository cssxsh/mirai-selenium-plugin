plugins {
    kotlin("jvm") version "1.6.0"
    kotlin("plugin.serialization") version "1.6.0"

    id("net.mamoe.mirai-console") version "2.10.0"
    id("net.mamoe.maven-central-publish") version "0.7.1"
}

group = "xyz.cssxsh.mirai"
version = "2.0.9"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("cssxsh", "mirai-selenium-plugin")
    licenseFromGitHubProject("AGPL-3.0", "master")
    publication {
        artifact(tasks.getByName("buildPlugin"))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    api("org.seleniumhq.selenium:selenium-java:4.1.2") {
        exclude("org.slf4j")
        exclude("io.netty")
        exclude("com.google.auto.service")
    }
    compileOnly("com.google.auto.service:auto-service:1.0.1")
    compileOnly("net.mamoe:mirai-core-utils:2.10.0")
    compileOnly("me.him188:kotlin-jvm-blocking-bridge-runtime-jvm:2.0.0-160.3")

    testImplementation(kotlin("test", "1.6.0"))
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

kotlin {
    explicitApi()
}

mirai {
    configureShadow {
        exclude("module-info.class")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}