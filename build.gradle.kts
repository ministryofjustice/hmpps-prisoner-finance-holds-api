import com.fasterxml.jackson.databind.ObjectMapper
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI
import java.nio.file.Files


plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.8"
  kotlin("plugin.spring") version "2.4.20"
  id("org.jetbrains.kotlin.plugin.noarg") version "2.4.20"
  id("jacoco")
  kotlin("plugin.jpa") version "2.4.20"
  id("org.openapi.generator") version "7.25.0"
}

configure<JacocoPluginExtension> {
  toolVersion = "0.8.14"
}
configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
  named("ktlint") {
    resolutionStrategy.eachDependency {
      if (requested.group == "org.jetbrains.kotlin") {
        useVersion("2.2.0")
      }
    }
  }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.0.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.postgresql:postgresql:42.7.13")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.0.1")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48") {
    exclude(group = "io.swagger.core.v3")
  }

  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
}

kotlin {
  jvmToolchain(25)
}

tasks {

  // Disable the test task as we run the integration and unit tests separately
  named<Test>("test") {
    enabled = true
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }

  register<Test>("unitTest") {
    group = "verification"
    description = "Runs unit tests excluding integration tests"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["main"].output + configurations["testRuntimeClasspath"] + sourceSets["test"].output
//    Currently no tests outside this package
//    filter {
//      excludeTestsMatching("uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration*")
//    }
    extensions.configure(JacocoTaskExtension::class) {
      destinationFile = layout.buildDirectory.file("jacoco/unitTest.exec").get().asFile
    }
  }

  register<Test>("integrationTest") {
    description = "Runs the integration tests"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["main"].output + configurations["testRuntimeClasspath"] + sourceSets["test"].output
//    Currently no tests outside this package
//    filter {
//      includeTestsMatching("uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration*")
//    }
    extensions.configure(JacocoTaskExtension::class) {
      destinationFile = layout.buildDirectory.file("jacoco/integrationTest.exec").get().asFile
    }
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }

  testlogger {
    theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA
  }
}

// ==============================================================================
// OPEN API GENERATION CONFIGURATION
// ==============================================================================

val apiSpecs = mapOf(
  "generalledger" to "https://prisoner-finance-general-ledger-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
)

val cleanOpenApi = tasks.register("cleanOpenApi") {
  group = "openapi tools"
  description = "Cleans up previously downloaded specs and generated API clients"

  doLast {
    file("$rootDir/openapi-specs").deleteRecursively()
    file(layout.buildDirectory.dir("generated/openapi").get().asFile).deleteRecursively()
  }
}

val downloadAllOpenApiSpecs = tasks.register("downloadAllOpenApiSpecs") {
  group = "openapi tools"
  description = "Downloads all API specifications"
  dependsOn(cleanOpenApi)

  doLast {
    val destDir = file("$rootDir/openapi-specs")
    if (!destDir.exists()) destDir.mkdirs()
    val mapper = ObjectMapper()

    apiSpecs.forEach { (name, url) ->
      println("Downloading $name API spec from $url...")
      val destFile = file("$destDir/$name.json")

      val json = URI.create(url).toURL().readText()
      val formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
      Files.write(destFile.toPath(), formattedJson.toByteArray())

      println("Saved to $destFile")
    }
  }
}

val generateTasks = apiSpecs.map { (name, _) ->
  tasks.register<GenerateTask>("build${name.replaceFirstChar { it.titlecase() }}ApiClient") {
    group = "openapi tools"
    description = "Generates Kotlin client and models for $name"

    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")

    inputSpec.set("$rootDir/openapi-specs/$name.json")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)

    modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.$name")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.clients.$name")

    configOptions.set(
      mapOf(
        "serializationLibrary" to "jackson",
        "dateLibrary" to "java8",
        "enumPropertyNaming" to "original",
        "modelMutable" to "false",
        "useSpringBoot3" to "true",
        "useTags" to "true",
      ),
    )

    typeMappings.set(
      mapOf(
        "OffsetDateTime" to "java.time.Instant",
        "DateTime" to "java.time.Instant",
      ),
    )
    importMappings.set(
      mapOf(
        "java.time.LocalDateTime" to "java.time.Instant",
      ),
    )

    globalProperties.set(
      mapOf(
        "models" to "",
        "apis" to "",
        "supportingFiles" to "",
        "modelDocs" to "false",
        "modelTests" to "false",
        "apiDocs" to "false",
        "apiTests" to "false",
      ),
    )
  }
}

tasks.register("buildAllApiClients") {
  group = "openapi tools"
  description = "Cleans, downloads all JSON specs, and generates all API clients"
  dependsOn(generateTasks)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  dependsOn(generateTasks)
}

tasks.withType<KtLintCheckTask> {
  mustRunAfter(generateTasks)
}
tasks.withType<KtLintFormatTask> {
  mustRunAfter(generateTasks)
}

sourceSets {
  main {
    java {
      srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
    }
  }
}

configure<KtlintExtension> {
  filter {
    exclude {
      it.file.path.contains("generated/")
    }
  }
}

// JACOCO

tasks.register<JacocoReport>("jacocoUnitTestReport") {
  dependsOn("unitTest")
  executionData.setFrom(layout.buildDirectory.file("jacoco/unitTest.exec"))
  classDirectories.setFrom(sourceSets.main.get().output)
  sourceDirectories.setFrom(sourceSets.main.get().allSource)

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/unit"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/unit/jacoco.xml"))
  }

  doLast {
    val reportFile = reports.xml.outputLocation.get().asFile
    if (reportFile.exists()) {
      val content = reportFile.readText()
      val updatedContent = content.replaceFirst("name=\"${project.name}\"", "name=\"Unit Tests\"")
      reportFile.writeText(updatedContent)
    }
  }
}

tasks.register<JacocoReport>("jacocoTestIntegrationReport") {
  dependsOn("integrationTest")
  executionData.setFrom(layout.buildDirectory.file("jacoco/integrationTest.exec"))

  classDirectories.setFrom(sourceSets.main.get().output)
  sourceDirectories.setFrom(sourceSets.main.get().allSource)

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/integration"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/integration/jacoco.xml"))
  }

  doLast {
    val reportFile = reports.xml.outputLocation.get().asFile
    if (reportFile.exists()) {
      val content = reportFile.readText()
      val updatedContent = content.replaceFirst("name=\"${project.name}\"", "name=\"Integration Tests\"")
      reportFile.writeText(updatedContent)
    }
  }
}

tasks.register<JacocoReport>("combineJacocoReports") {
  dependsOn("jacocoUnitTestReport", "jacocoTestIntegrationReport")

  executionData(
    layout.buildDirectory.file("jacoco/unitTest.exec"),
    layout.buildDirectory.file("jacoco/integrationTest.exec"),
  )

  classDirectories.setFrom(sourceSets.main.get().output)
  sourceDirectories.setFrom(sourceSets.main.get().allSource)

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/combined"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/combined/jacoco.xml"))
  }

  doLast {
    val reportFile = reports.xml.outputLocation.get().asFile
    if (reportFile.exists()) {
      val content = reportFile.readText()
      val updatedContent = content.replaceFirst("name=\"${project.name}\"", "name=\"Combined Tests\"")
      reportFile.writeText(updatedContent)
    }
  }
}

tasks.named("check") {
  dependsOn("unitTest", "integrationTest", "combineJacocoReports")
}
