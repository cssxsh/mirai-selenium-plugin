plugins {
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.serialization") version "1.7.22"

    id("net.mamoe.mirai-console") version "2.14.0-RC"
    id("me.him188.maven-central-publish") version "1.0.0-dev-3"
    id("me.him188.kotlin-jvm-blocking-bridge") version "2.2.0-172.1"
}

group = "xyz.cssxsh.mirai"
version = "2.2.5"

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
    api("org.seleniumhq.selenium:selenium-java:4.7.2")
    testImplementation(kotlin("test"))
    testImplementation("org.icepear.echarts:echarts-java:1.0.7") {
        exclude("org.slf4j")
    }
    testCompileOnly("org.projectlombok:lombok:1.18.24")
    //
    implementation(platform("net.mamoe:mirai-bom:2.14.0-RC"))
    testCompileOnly("net.mamoe:mirai-console-compiler-common")
    //
    implementation(platform("org.slf4j:slf4j-parent:2.0.6"))
    testImplementation("org.slf4j:jcl-over-slf4j:2.0.6")
    testImplementation("org.slf4j:jul-to-slf4j:2.0.6")
    //
    implementation(platform("io.netty:netty-bom:4.1.87.Final"))
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