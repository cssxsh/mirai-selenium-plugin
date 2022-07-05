plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"

    id("net.mamoe.mirai-console") version "2.12.0"
    id("net.mamoe.maven-central-publish") version "0.7.1"
    id("me.him188.kotlin-jvm-blocking-bridge") version "2.1.0-162.1"
}

group = "xyz.cssxsh.mirai"
version = "2.2.0"

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
    api("org.seleniumhq.selenium:selenium-java:4.3.0") {
        exclude("org.slf4j")
        exclude("io.netty")
        exclude("com.google.auto.service")
    }
    compileOnly("com.google.auto.service:auto-service:1.0.1")
    compileOnly("net.mamoe:mirai-core-utils:2.12.0")
    // test
    testImplementation(kotlin("test", "1.6.21"))
    testImplementation("org.icepear.echarts:echarts-java:1.0.3")
    testCompileOnly("org.projectlombok:lombok:1.18.24")
}

kotlin {
    explicitApi()
}

tasks {
    test {
        useJUnitPlatform()
    }
}