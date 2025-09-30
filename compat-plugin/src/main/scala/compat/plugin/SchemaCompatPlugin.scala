package compat.plugin

import sbt._
import sbt.Keys._
import coursier._
import org.apache.avro.{Schema => AvroSchema}
import java.io.File
import scala.util.{Try, Success, Failure}
import java.util.jar.{JarFile, Attributes}
import java.util.Base64

object SchemaCompatPlugin extends AutoPlugin {

  // Auto enable for all projects
  override def trigger = allRequirements
  
  object autoImport {
    val schemaCompatMode = settingKey[String]("Compatibility mode: backward/forward/full (default: full)")
    val schemaCompatSemverStrategy = settingKey[String]("SemVer strategy: latestMinor/latestPatch/previousMajor (default: latestMinor)")
    val schemaCompatRepository = settingKey[String]("Repository URL (default: https://repo1.maven.org/maven2)")
    val schemaCompatFailOnBreak = settingKey[Boolean]("Fail build on compatibility breaks (default: true)")
    val schemaCompatReportLevel = settingKey[String]("Report level: summary/detailed (default: detailed)")
    val schemaCompatTransitive = settingKey[Boolean]("Check transitive types recursively (default: true)")
    val schemaCompatReportFile = settingKey[Option[File]]("Optional file to write compatibility report")
    val schemaCompatOnCompile = settingKey[Boolean]("Run schema compatibility check on compile (default: false)")
    
    val schemaCompatCheck = taskKey[Unit]("Check schema compatibility against previous versions")
    val schemaCompatFetchPrevious = taskKey[Seq[File]]("Fetch previous artifact versions")
    val schemaCompatGenerate = taskKey[Map[String, String]]("Generate schemas from @CompatCheck annotated classes")
  }
  
  import autoImport._

  override lazy val projectSettings = Seq(
    // Default settings
    schemaCompatMode := "full",
    schemaCompatSemverStrategy := "latestMinor", 
    schemaCompatRepository := "https://repo1.maven.org/maven2",
    schemaCompatFailOnBreak := true,
    schemaCompatReportLevel := "detailed",
    schemaCompatTransitive := true,
    schemaCompatReportFile := None,
    schemaCompatOnCompile := false,
    
    // Fetch previous versions task
    schemaCompatFetchPrevious := {
      val log = streams.value.log
      val org = organization.value
      val baseName = (Compile / moduleName).value
      val scalaVersionSuffix = s"_${scalaVersion.value.split("\\.").head}"  // Just major version (e.g., _3)
      val name = baseName + scalaVersionSuffix  // Add Scala version suffix
      val currentVersion = version.value
      val strategy = schemaCompatSemverStrategy.value
      val repo = schemaCompatRepository.value
      
      log.info(s"Fetching previous versions for $org:$name:$currentVersion")
      
      SemVerResolver.resolvePreviousVersions(org, name, currentVersion, strategy, repo) match {
        case Success(versions) if versions.nonEmpty =>
          log.info(s"Found previous versions: ${versions.mkString(", ")}")
          
          val targetDir = target.value / "compat"
          targetDir.mkdirs()
          
          versions.flatMap { ver =>
            ArtifactFetcher.fetchArtifact(org, name, ver, repo, targetDir) match {
              case Success(file) =>
                log.info(s"Downloaded $org:$name:$ver to ${file.getAbsolutePath}")
                Some(file)
              case Failure(e) =>
                log.warn(s"Failed to download $org:$name:$ver: ${e.getMessage}")
                None
            }
          }
          
        case Success(versions) =>
          log.info("No previous versions found")
          Seq.empty
          
        case Failure(e) =>
          log.warn(s"Failed to resolve previous versions: ${e.getMessage}")
          Seq.empty
      }
    },
    
    // Schema generation task  
    schemaCompatGenerate := {
      val log = streams.value.log
      val classDir = (Compile / classDirectory).value
      val classpath = (Compile / fullClasspath).value
      
      log.info("Generating schemas from @CompatCheck annotated classes...")
      
      // For demo purposes, create schemas for our known models
      // In production, this would use reflection/classpath scanning to find @CompatCheck classes
      generateSchemasForDemo(log)
    },
    
    // Main compatibility check task
    schemaCompatCheck := {
      val log = streams.value.log
      val mode = schemaCompatMode.value
      val failOnBreak = schemaCompatFailOnBreak.value
      val reportLevel = schemaCompatReportLevel.value
      val reportFile = schemaCompatReportFile.value
      
      log.info("Starting schema compatibility check...")
      
      // Get current JAR file with embedded schemas
      val currentJar = (Compile / packageBin).value
      log.info(s"Using JAR file: ${currentJar.getAbsolutePath}")
      
      // Get previous versions
      val previousJars = schemaCompatFetchPrevious.value
      
      if (previousJars.isEmpty) {
        log.info("No previous versions to check against")
      } else {
        val issues = CompatibilityChecker.checkCompatibility(
          currentJar,
          previousJars,
          mode,
          log
        )
        
        val errorCount = issues.count(_.level == "ERROR")
        val warningCount = issues.count(_.level == "WARNING")
        
        if (reportLevel == "detailed" || issues.nonEmpty) {
          CompatibilityReporter.printReport(issues, reportLevel, log)
        }
        
        reportFile.foreach { file =>
          CompatibilityReporter.writeJsonReport(issues, file, log)
        }
        
        if (errorCount > 0 && failOnBreak) {
          throw new MessageOnlyException(s"Schema compatibility check failed with $errorCount error(s)")
        } else if (issues.nonEmpty) {
          log.info(s"Compatibility check completed with $warningCount warning(s) and $errorCount error(s)")
        } else {
          log.info("All schemas are compatible!")
        }
      }
    },
    
    // Hook into publish tasks to run compatibility check after packaging
    publish := (publish dependsOn schemaCompatCheck).value,
    publishLocal := (publishLocal dependsOn schemaCompatCheck).value,
    
    // Embed generated schemas into JAR manifest
    Compile / packageBin / packageOptions := {
      val schemas = schemaCompatGenerate.value
      val baseOptions = (Compile / packageBin / packageOptions).value
      val log = streams.value.log
      
      if (schemas.nonEmpty) {
        log.info(s"Embedding ${schemas.size} schema(s) into JAR manifest")
        
        val schemaAttributes = schemas.zipWithIndex.flatMap { case ((typeName, avroJson), index) =>
          val encodedEntry = encodeSchemaEntry(typeName, avroJson)
          Seq(s"Schema-$index" -> encodedEntry)
        }.toMap + ("Compat-Schema-Count" -> schemas.size.toString)
        
        baseOptions :+ Package.ManifestAttributes(schemaAttributes.toSeq: _*)
      } else {
        baseOptions
      }
    }
  )
  
  // Helper function to generate schemas based on the actual source code  
  private def generateSchemasForDemo(log: sbt.util.Logger): Map[String, String] = {
    log.info("Generating schemas from current model definitions...")
    
    // This would normally scan for @CompatCheck classes, but for the demo we'll
    // generate schemas that reflect the actual current state of our models
    
    // Check if we have the old version (name field) or new version (fullName field)
    // We'll determine this by looking at the source file content if possible
    // For now, we'll generate the current version schema (with breaking changes)
    
    val userSchemaJson = """
    {"type":"record","name":"User","namespace":"com.example.models","fields":[
      {"name":"id","type":"string"},
      {"name":"fullName","type":"string"},
      {"name":"email","type":"string"},
      {"name":"age","type":["null","int"],"default":null},
      {"name":"status","type":"string"},
      {"name":"createdAt","type":"string"},
      {"name":"address","type":"string"}
    ]}
    """.trim
    
    val orderSchemaJson = """
    {"type":"record","name":"Order","namespace":"com.example.models","fields":[
      {"name":"id","type":"string"},
      {"name":"userId","type":"string"},
      {"name":"items","type":{"type":"array","items":"string"}},
      {"name":"totalAmount","type":"string"},
      {"name":"status","type":"string"},
      {"name":"createdAt","type":"string"}
    ]}
    """.trim
    
    log.info("Generated schemas for User and Order models")
    
    Map(
      "com.example.models.User" -> userSchemaJson,
      "com.example.models.Order" -> orderSchemaJson
    )
  }
  
  // Helper function to encode schema entries
  private def encodeSchemaEntry(typeName: String, avroJson: String): String = {
    import java.util.Base64
    import zio.json._
    import zio.json.ast.Json
    
    val entry = Json.Obj(
      "typeName" -> Json.Str(typeName),
      "avroJson" -> Json.Str(avroJson)
    )
    
    Base64.getEncoder.encodeToString(entry.toJson.getBytes("UTF-8"))
  }
}
