repositories {
  mavenCentral()
  maven {
    url = uri("https://repo.danubetech.com/repository/maven-public")
  }
  maven("https://jitpack.io")
  maven("https://repository.jboss.org/nexus/content/repositories/thirdparty-releases/")
}

dependencies {
  implementation("com.nimbusds:nimbus-jose-jwt:9.34")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
  implementation("org.bouncycastle:bcprov-jdk15to18:1.77")

  testImplementation(project(":dids"))
  testImplementation(project(":crypto"))
  testImplementation("io.github.erdtman:java-json-canonicalization:1.1")
}

tasks.test {
  useJUnitPlatform()
}