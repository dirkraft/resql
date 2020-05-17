import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.dirkraft"
version = "1.0-SNAPSHOT"

plugins {
  kotlin("jvm") version "1.3.50"
  maven // for jitpack dependency
  `maven-publish`
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

repositories {
  jcenter()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
  implementation("com.github.seratch:kotliquery:1.3.0")
  implementation("com.zaxxer:HikariCP:3.2.0")
  implementation("org.postgresql:postgresql:42.2.6")
  implementation("org.jetbrains:annotations:16.0.2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.9")
  testImplementation("junit:junit:4.12")
}

// http://bastienpaul.fr/wordpress/2019/02/08/publish-a-kotlin-lib-with-gradle-kotlin-dsl/
val sourcesJar by tasks.creating(Jar::class) {
  archiveClassifier.set("sources")
  from(sourceSets.getByName("main").allSource)
}

publishing {
  publications {
    create<MavenPublication>("default") {
      from(components["kotlin"])
      artifact(sourcesJar)
    }
  }
  repositories {
    maven {
      url = uri("$buildDir/repository")
    }
  }
}