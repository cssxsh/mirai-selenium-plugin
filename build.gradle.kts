plugins {
    kotlin("jvm") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"

    id("net.mamoe.mirai-console") version "2.9.0-M1"
    id("net.mamoe.maven-central-publish") version "0.7.0"
}

group = "xyz.cssxsh.mirai"
version = "2.0.0-M1"

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
    maven("https://maven.aliyun.com/repository/central")
    mavenCentral()
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
}

dependencies {
    api("org.seleniumhq.selenium:selenium-java:4.1.0") {
        exclude("io.netty")
    }

    testImplementation(kotlin("test", "1.5.31"))
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        exclude("module-info.class")
        exclude {
            it.path.startsWith("kotlin")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}