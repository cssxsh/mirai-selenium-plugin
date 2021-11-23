plugins {
    kotlin("jvm") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"

    id("net.mamoe.mirai-console") version "2.9.0-M1"
    id("net.mamoe.maven-central-publish") version "0.7.0"
}

group = "xyz.cssxsh.mirai"
version = "1.0.0"

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
}

dependencies {
    implementation("io.github.karlatemp.mxlib:mxlib-selenium:3.0-dev-20") {
        exclude("org.seleniumhq.selenium")
        exclude("junit")
        exclude("classworlds")
        exclude("io.netty")
        exclude("com.typesafe.netty")
    }
    implementation("org.seleniumhq.selenium:selenium-java:4.0.0") {
        exclude("io.netty")
        exclude("com.typesafe.netty")
        exclude("com.google.auto.service")
        exclude("com.google.guava")
        exclude("org.asynchttpclient")
        exclude("io.opentelemetry")
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