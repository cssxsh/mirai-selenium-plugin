plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"

    id("net.mamoe.mirai-console") version "2.13.0-RC2"
    id("me.him188.maven-central-publish") version "1.0.0-dev-3"
    id("me.him188.kotlin-jvm-blocking-bridge") version "2.2.0-172.1"
}

group = "xyz.cssxsh.mirai"
version = "2.2.3"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("cssxsh", "mirai-selenium-plugin")
    licenseFromGitHubProject("AGPL-3.0")
    workingDir = System.getenv("PUBLICATION_TEMP")?.let { file(it).resolve(projectName) }
        ?: buildDir.resolve("publishing-tmp")
    publication {
        artifact(tasks["buildPlugin"])
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api("org.seleniumhq.selenium:selenium-java:4.5.2") {
        exclude("org.slf4j")
        exclude("io.netty")
        exclude("com.google.auto.service")
    }
    api("com.github.jknack:handlebars:4.3.1") {
        exclude("org.slf4j")
    }
    compileOnly("com.google.auto.service:auto-service-annotations:1.0.1")
    // test
    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple:2.0.3")
    testImplementation("net.mamoe:mirai-logging-slf4j:2.13.0-RC2")
    testImplementation("org.icepear.echarts:echarts-java:1.0.7")
    testCompileOnly("org.projectlombok:lombok:1.18.24")
}

kotlin {
    explicitApi()
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

tasks {
    test {
        useJUnitPlatform()
    }
}