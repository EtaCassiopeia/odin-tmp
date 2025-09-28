package compat.plugin

import sbt.util.Logger
import java.io.{File, PrintWriter}
import scala.util.Using

object CompatibilityReporter {
  
  def printReport(issues: List[CompatibilityIssue], reportLevel: String, log: Logger): Unit = {
    if (issues.isEmpty) {
      log.info("✓ All schemas are compatible!")
      return
    }
    
    val errors = issues.filter(_.level == "ERROR")
    val warnings = issues.filter(_.level == "WARNING")
    val infos = issues.filter(_.level == "INFO")
    
    log.info(s"\n=== Schema Compatibility Report ===")
    log.info(s"Total issues: ${issues.size} (${errors.size} errors, ${warnings.size} warnings, ${infos.size} info)")
    
    if (reportLevel == "detailed") {
      printDetailedReport(issues, log)
    } else {
      printSummaryReport(issues, log)
    }
  }
  
  private def printDetailedReport(issues: List[CompatibilityIssue], log: Logger): Unit = {
    val groupedBySchema = issues.groupBy(_.schemaName)
    
    groupedBySchema.foreach { case (schemaName, schemaIssues) =>
      log.info(s"\n📋 Schema: $schemaName")
      
      val errors = schemaIssues.filter(_.level == "ERROR")
      val warnings = schemaIssues.filter(_.level == "WARNING")
      val infos = schemaIssues.filter(_.level == "INFO")
      
      if (errors.nonEmpty) {
        log.error("  ❌ Errors:")
        errors.foreach { issue =>
          log.error(s"    • ${issue.path}: ${issue.message}")
        }
      }
      
      if (warnings.nonEmpty) {
        log.warn("  ⚠️  Warnings:")
        warnings.foreach { issue =>
          log.warn(s"    • ${issue.path}: ${issue.message}")
        }
      }
      
      if (infos.nonEmpty) {
        log.info("  ℹ️  Info:")
        infos.foreach { issue =>
          log.info(s"    • ${issue.path}: ${issue.message}")
        }
      }
    }
    
    // Add suggestions for common issues
    val errorTypes = issues.filter(_.level == "ERROR").map(_.message.split(" - ").head).distinct
    if (errorTypes.nonEmpty) {
      log.info(s"\n💡 Suggestions:")
      errorTypes.foreach { errorType =>
        getSuggestion(errorType).foreach(suggestion => log.info(suggestion))
      }
    }
  }
  
  private def printSummaryReport(issues: List[CompatibilityIssue], log: Logger): Unit = {
    val groupedByLevel = issues.groupBy(_.level)
    val groupedBySchema = issues.groupBy(_.schemaName)
    
    log.info(s"\n📊 Summary by schema:")
    groupedBySchema.foreach { case (schemaName, schemaIssues) =>
      val errorCount = schemaIssues.count(_.level == "ERROR")
      val warningCount = schemaIssues.count(_.level == "WARNING")
      val icon = if (errorCount > 0) "❌" else if (warningCount > 0) "⚠️" else "✓"
      log.info(s"  $icon $schemaName: $errorCount errors, $warningCount warnings")
    }
  }
  
  def writeJsonReport(issues: List[CompatibilityIssue], reportFile: File, log: Logger): Unit = {
    try {
      reportFile.getParentFile.mkdirs()
      
      Using(new PrintWriter(reportFile)) { writer =>
        import zio.json.*
        import zio.json.ast.Json
        
        // Convert issues to JSON
        val jsonIssues = issues.map { issue =>
          Json.Obj(
            "schemaName" -> Json.Str(issue.schemaName),
            "path" -> Json.Str(issue.path),
            "message" -> Json.Str(issue.message),
            "level" -> Json.Str(issue.level),
            "oldVersion" -> issue.oldVersion.fold(Json.Null: Json)(Json.Str(_)),
            "newVersion" -> issue.newVersion.fold(Json.Null: Json)(Json.Str(_))
          )
        }
        
        val report = Json.Obj(
          "timestamp" -> Json.Str(java.time.Instant.now().toString),
          "summary" -> Json.Obj(
            "total" -> Json.Num(issues.size),
            "errors" -> Json.Num(issues.count(_.level == "ERROR")),
            "warnings" -> Json.Num(issues.count(_.level == "WARNING")),
            "infos" -> Json.Num(issues.count(_.level == "INFO"))
          ),
          "issues" -> Json.Arr(zio.Chunk.fromIterable(jsonIssues))
        )
        
        writer.write(report.toJsonPretty)
        log.info(s"Compatibility report written to ${reportFile.getAbsolutePath}")
      }
    } catch {
      case e: Exception =>
        log.warn(s"Failed to write JSON report: ${e.getMessage}")
    }
  }
  
  private def getSuggestion(errorType: String): Option[String] = errorType match {
    case "READER_FIELD_MISSING_DEFAULT_VALUE" =>
      Some("• Consider adding default values to new required fields")
    case "FIELD_REMOVED" =>
      Some("• Instead of removing fields, consider making them optional")
    case "TYPE_MISMATCH" =>
      Some("• Type changes are generally breaking - consider using union types for gradual migration")
    case "MISSING_UNION_BRANCH" =>
      Some("• When modifying union types, ensure all previous branches are still supported")
    case _ =>
      None
  }
}