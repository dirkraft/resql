import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dirkraft"
version = "1.0-SNAPSHOT"

plugins {
  kotlin("jvm") version "1.3.50"
  maven // for jitpack dependency
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
  compile(kotlin("reflect"))
  implementation("com.github.seratch:kotliquery:1.3.0")
  implementation("com.zaxxer:HikariCP:3.2.0")
  implementation("org.postgresql:postgresql:42.2.6")
}
