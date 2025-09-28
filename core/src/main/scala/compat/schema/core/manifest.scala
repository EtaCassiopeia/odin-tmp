package compat.schema.core

import java.util.jar.{JarFile, Manifest}
import java.util.Base64
import scala.util.{Try, Using}
import scala.jdk.CollectionConverters.*
import zio.json.*
import zio.json.ast.Json
import org.apache.avro.{Schema => AvroSchema}

/** Utilities for storing and retrieving schemas from JAR manifests */
object ManifestStorage:
  
  private val SCHEMA_COUNT_KEY = "Compat-Schema-Count"
  private val SCHEMA_PREFIX = "Schema-"
  
  case class StoredSchema(
    typeName: String,
    avroJson: String,
    metadata: Map[String, String]
  ):
    lazy val avroSchema: AvroSchema = new AvroSchema.Parser().parse(avroJson)
  
  /** Generate manifest attributes for embedding schemas */
  def generateManifestAttributes(schemas: Map[String, CompatSchema[?]]): Map[String, String] =
    val entries = schemas.toList.zipWithIndex.map { case ((typeName, compatSchema), index) =>
      val entry = StoredSchema(
        typeName = typeName,
        avroJson = compatSchema.avro.toString(true),
        metadata = compatSchema.metadata
      )
      s"$SCHEMA_PREFIX$index" -> encodeEntry(entry)
    }
    
    Map(SCHEMA_COUNT_KEY -> schemas.size.toString) ++ entries.toMap
  
  /** Extract schemas from a JAR file */
  def extractSchemasFromJar(jarPath: String): Either[String, Map[String, StoredSchema]] =
    Try {
      Using(new JarFile(jarPath)) { jar =>
        Option(jar.getManifest) match
          case None => 
            Map.empty[String, StoredSchema]
          case Some(manifest) =>
            val attributes = manifest.getMainAttributes
            val schemaCount = Option(attributes.getValue(SCHEMA_COUNT_KEY))
              .flatMap(_.toIntOption)
              .getOrElse(0)
            
            if schemaCount == 0 then
              Map.empty[String, StoredSchema]
            else
              (0 until schemaCount).foldLeft(Map.empty[String, StoredSchema]) { (acc, index) =>
                val key = s"$SCHEMA_PREFIX$index"
                Option(attributes.getValue(key))
                  .flatMap(decodeEntry)
                  .fold(acc)(entry => acc + (entry.typeName -> entry))
              }
      }.get
    }.toEither.left.map(_.getMessage).map(Right(_)).getOrElse(Left("Failed to extract schemas"))
  
  private def encodeEntry(entry: StoredSchema): String =
    import zio.Chunk
    val json = Json.Obj(
      Chunk(
        "typeName" -> Json.Str(entry.typeName),
        "avroJson" -> Json.Str(entry.avroJson),
        "metadata" -> Json.Obj(
          Chunk.fromIterable(entry.metadata.map { case (k, v) => k -> Json.Str(v) })
        )
      )
    )
    Base64.getEncoder.encodeToString(json.toJson.getBytes("UTF-8"))
  
  private def decodeEntry(encoded: String): Option[StoredSchema] =
    Try {
      val jsonStr = new String(Base64.getDecoder.decode(encoded), "UTF-8")
      Json.decoder.decodeJson(jsonStr) match
        case Right(Json.Obj(fields)) =>
          val fieldMap = fields.toMap
          for 
            typeName <- fieldMap.get("typeName").flatMap(_.asString)
            avroJson <- fieldMap.get("avroJson").flatMap(_.asString)
            metadataJson <- fieldMap.get("metadata")
            metadata <- metadataJson match
              case Json.Obj(metaFields) =>
                Some(metaFields.toMap.map { case (k, v) => k -> v.asString.getOrElse("") })
              case _ => Some(Map.empty[String, String])
          yield StoredSchema(typeName, avroJson, metadata)
        case _ => None
    }.toOption.flatten

  /** Collect all CompatSchema instances from the current compilation unit */
  def collectCurrentSchemas(): Map[String, CompatSchema[?]] =
    // This would be implemented by the SBT plugin using runtime reflection
    // or by collecting schemas during compilation
    // For now, return empty map as placeholder
    Map.empty