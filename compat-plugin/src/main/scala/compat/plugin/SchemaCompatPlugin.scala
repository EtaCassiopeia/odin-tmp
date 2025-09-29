package compat.plugin

import sbt._
import sbt.Keys._
import coursier._
import org.apache.avro.{Schema => AvroSchema}
import java.io.File
import scala.util.{Try, Success, Failure}
import java.util.jar.{JarFile, Attributes}

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
      val name = (Compile / moduleName).value
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
    
    // Main compatibility check task
    schemaCompatCheck := {
      val log = streams.value.log
      val mode = schemaCompatMode.value
      val failOnBreak = schemaCompatFailOnBreak.value
      val reportLevel = schemaCompatReportLevel.value
      val reportFile = schemaCompatReportFile.value
      
      log.info("Starting schema compatibility check...")
      
      // Get compiled classes directory instead of JAR to avoid cycle
      val compiledClasses = (Compile / classDirectory).value
      log.info(s"Using compiled classes from: ${compiledClasses.getAbsolutePath}")
      
      // Get previous versions
      val previousJars = schemaCompatFetchPrevious.value
      
      if (previousJars.isEmpty) {
        log.info("No previous versions to check against")
      } else {
        val issues = CompatibilityChecker.checkCompatibility(
          compiledClasses,
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
    publishLocal := (publishLocal dependsOn schemaCompatCheck).value
    
    // Note: JAR manifest metadata has been removed to prevent cyclic dependencies
    // If needed, this can be added to a separate task that doesn't depend on streams
  )
}