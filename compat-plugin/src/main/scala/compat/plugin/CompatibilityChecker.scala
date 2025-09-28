package compat.plugin

import org.apache.avro.{Schema => AvroSchema}
import org.apache.avro.SchemaCompatibility
import java.io.File
import java.util.jar.JarFile
import scala.util.{Try, Using}
import scala.jdk.CollectionConverters.*
import sbt.util.Logger
import java.util.Base64

case class CompatibilityIssue(
  schemaName: String,
  path: String,
  message: String,
  level: String, // ERROR, WARNING, INFO
  oldVersion: Option[String] = None,
  newVersion: Option[String] = None
)

object CompatibilityChecker {
  
  def checkCompatibility(
    currentJar: File,
    previousJars: Seq[File],
    mode: String,
    log: Logger
  ): List[CompatibilityIssue] = {
    
    log.info(s"Checking compatibility for ${currentJar.getName} against ${previousJars.size} previous version(s)")
    
    // Extract current schemas
    val currentSchemas = extractSchemas(currentJar) match {
      case Right(schemas) => schemas
      case Left(error) =>
        log.warn(s"Failed to extract schemas from current JAR: $error")
        return List.empty
    }
    
    if (currentSchemas.isEmpty) {
      log.info("No schemas found in current JAR")
      return List.empty
    }
    
    log.info(s"Found ${currentSchemas.size} schema(s) in current JAR: ${currentSchemas.keys.mkString(", ")}")
    
    // Check against each previous version
    previousJars.flatMap { previousJar =>
      log.info(s"Comparing against ${previousJar.getName}")
      
      extractSchemas(previousJar) match {
        case Right(previousSchemas) =>
          checkSchemasCompatibility(currentSchemas, previousSchemas, mode, log)
          
        case Left(error) =>
          log.warn(s"Failed to extract schemas from ${previousJar.getName}: $error")
          List.empty
      }
    }.toList
  }
  
  private def extractSchemas(jarFile: File): Either[String, Map[String, AvroSchema]] = {
    Try {
      val jar = new JarFile(jarFile)
      val schemas = Option(jar.getManifest) match {
        case None => 
          Map.empty[String, AvroSchema]
        case Some(manifest) =>
          val attributes = manifest.getMainAttributes
          val schemaCount = Option(attributes.getValue("Compat-Schema-Count"))
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(0)
          
          if (schemaCount == 0) {
            Map.empty[String, AvroSchema]
          } else {
            (0 until schemaCount).foldLeft(Map.empty[String, AvroSchema]) { (acc, index) =>
              val key = s"Schema-$index"
              Option(attributes.getValue(key))
                .flatMap(decodeSchemaEntry)
                .fold(acc) { case (typeName, avroJson) =>
                  Try(new AvroSchema.Parser().parse(avroJson)) match {
                    case scala.util.Success(schema) => acc + (typeName -> schema)
                    case scala.util.Failure(_) => acc
                  }
                }
            }
          }
      }
      jar.close()
      schemas
    }.toEither.left.map(_.getMessage)
  }
  
  private def decodeSchemaEntry(encoded: String): Option[(String, String)] = {
    Try {
      import zio.json.*
      import zio.json.ast.Json
      
      val jsonStr = new String(Base64.getDecoder.decode(encoded), "UTF-8")
      Json.decoder.decodeJson(jsonStr) match {
        case Right(Json.Obj(fields)) =>
          val fieldMap = fields.toMap
          for {
            typeName <- fieldMap.get("typeName").flatMap(_.asString)
            avroJson <- fieldMap.get("avroJson").flatMap(_.asString)
          } yield (typeName, avroJson)
        case _ => None
      }
    }.toOption.flatten
  }
  
  private def checkSchemasCompatibility(
    currentSchemas: Map[String, AvroSchema],
    previousSchemas: Map[String, AvroSchema],
    mode: String,
    log: Logger
  ): List[CompatibilityIssue] = {
    
    val issues = scala.collection.mutable.ListBuffer[CompatibilityIssue]()
    
    // Check existing schemas for compatibility
    currentSchemas.foreach { case (typeName, currentSchema) =>
      previousSchemas.get(typeName) match {
        case Some(previousSchema) =>
          val schemaIssues = checkSchemaCompatibility(typeName, previousSchema, currentSchema, mode)
          issues ++= schemaIssues
          
        case None =>
          // New schema - this is generally okay
          log.info(s"New schema detected: $typeName")
          issues += CompatibilityIssue(
            schemaName = typeName,
            path = "root",
            message = "New schema added",
            level = "INFO"
          )
      }
    }
    
    // Check for removed schemas
    previousSchemas.keySet.diff(currentSchemas.keySet).foreach { removedSchema =>
      issues += CompatibilityIssue(
        schemaName = removedSchema,
        path = "root",
        message = "Schema was removed",
        level = "ERROR"
      )
    }
    
    issues.toList
  }
  
  private def checkSchemaCompatibility(
    typeName: String,
    oldSchema: AvroSchema,
    newSchema: AvroSchema,
    mode: String
  ): List[CompatibilityIssue] = {
    
    val issues = scala.collection.mutable.ListBuffer[CompatibilityIssue]()
    
    mode.toLowerCase match {
      case "backward" =>
        val result = SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema)
        issues ++= convertAvroIncompatibilities(typeName, result.getResult.getIncompatibilities.asScala.toList)
        
      case "forward" =>
        val result = SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema)
        issues ++= convertAvroIncompatibilities(typeName, result.getResult.getIncompatibilities.asScala.toList)
        
      case "full" | _ =>
        val backwardResult = SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema)
        val forwardResult = SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema)
        
        issues ++= convertAvroIncompatibilities(typeName, backwardResult.getResult.getIncompatibilities.asScala.toList, "backward")
        issues ++= convertAvroIncompatibilities(typeName, forwardResult.getResult.getIncompatibilities.asScala.toList, "forward")
    }
    
    // Check for refined type compatibility if metadata is present
    val oldRefined = Option(oldSchema.getProp("refined"))
    val newRefined = Option(newSchema.getProp("refined"))
    
    (oldRefined, newRefined) match {
      case (Some(oldRef), Some(newRef)) if oldRef != newRef =>
        issues += CompatibilityIssue(
          schemaName = typeName,
          path = "refinement",
          message = s"Refined type constraint changed from $oldRef to $newRef",
          level = "WARNING"
        )
      case (Some(oldRef), None) =>
        issues += CompatibilityIssue(
          schemaName = typeName,
          path = "refinement", 
          message = s"Refined constraint $oldRef was removed",
          level = "WARNING"
        )
      case _ => // Other cases are generally okay
    }
    
    issues.toList
  }
  
  private def convertAvroIncompatibilities(
    typeName: String,
    incompatibilities: List[org.apache.avro.SchemaCompatibility.Incompatibility],
    direction: String = ""
  ): List[CompatibilityIssue] = {
    incompatibilities.map { incomp =>
      val prefix = if (direction.nonEmpty) s"[$direction] " else ""
      CompatibilityIssue(
        schemaName = typeName,
        path = incomp.getLocation,
        message = s"$prefix${incomp.getType} - ${incomp.getMessage}",
        level = "ERROR"
      )
    }
  }
}