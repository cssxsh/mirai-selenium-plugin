plugins {
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.21"

    id("net.mamoe.mirai-console") version "2.13.2"
    id("me.him188.maven-central-publish") version "1.0.0-dev-3"
    id("me.him188.kotlin-jvm-blocking-bridge") version "2.2.0-172.1"
}

group = "xyz.cssxsh.mirai"
version = "2.2.4"

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
    api("com.github.jknack:handlebars:4.3.1")
    api("org.seleniumhq.selenium:selenium-java:4.7.1") {
        exclude(group = "io.netty")
    }
    testImplementation(kotlin("test"))
    testImplementation("org.icepear.echarts:echarts-java:1.0.7")
    testCompileOnly("org.projectlombok:lombok:1.18.24")
    //
    implementation(platform("net.mamoe:mirai-bom:2.13.2"))
    compileOnly("net.mamoe:mirai-console-compiler-common")
    testImplementation("net.mamoe:mirai-logging-slf4j")
    //
    implementation(platform("org.slf4j:slf4j-parent:2.0.5"))
    testImplementation("org.slf4j:slf4j-simple")
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