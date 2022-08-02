plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"

    id("net.mamoe.mirai-console") version "2.12.1"
    id("me.him188.maven-central-publish") version "1.0.0-dev-3"
    id("me.him188.kotlin-jvm-blocking-bridge") version "2.1.0-170.1"
}

group = "xyz.cssxsh.mirai"
version = "2.2.2"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("cssxsh", "mirai-selenium-plugin")
    licenseFromGitHubProject("AGPL-3.0", "master")
    workingDir = System.getenv("PUBLICATION_TEMP")?.let { file(it).resolve(projectName) }
        ?: project.buildDir.resolve("publishing-tmp")
    publication {
        artifact(tasks.getByName("buildPlugin"))
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
    api("com.github.jknack:handlebars:4.3.0") {
        exclude("org.slf4j")
    }
    compileOnly("com.google.auto.service:auto-service-annotations:1.0.1")
    compileOnly("net.mamoe:mirai-core-utils:2.12.1")
    // test
    testImplementation(kotlin("test", "1.6.21"))
    testImplementation("org.icepear.echarts:echarts-java:1.0.3")
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