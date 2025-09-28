package compat.schema.core

import zio.schema.Schema
import zio.schema.codec.AvroSchemaCodec
import org.apache.avro.{Schema => AvroSchema}
import scala.util.Try

/** Schema issue reported during compatibility checking */
case class SchemaIssue(
  path: String,
  message: String,
  level: IssueLevel,
  oldSchema: Option[String] = None,
  newSchema: Option[String] = None
)

enum IssueLevel:
  case Error, Warning, Info

/** Core type class for schema compatibility checking */
trait CompatSchema[T]:
  /** The underlying ZIO Schema */
  def schema: Schema[T]
  
  /** The Avro schema representation with metadata */
  def avro: AvroSchema
  
  /** Validate a value against refined constraints */
  def validate(value: T): Either[List[String], Unit] = Right(())
  
  /** Check compatibility against a previous schema */
  def isCompatible(previous: CompatSchema[_], mode: Mode): List[SchemaIssue]
  
  /** Metadata properties for the schema */
  def metadata: Map[String, String] = Map.empty

object CompatSchema:
  
  /** Create a CompatSchema from an existing Schema */
  def apply[T](
    baseSchema: Schema[T],
    avroSchema: AvroSchema,
    validator: T => Either[List[String], Unit] = (_: T) => Right(()),
    checker: (CompatSchema[_], Mode) => List[SchemaIssue] = (_, _) => List.empty,
    props: Map[String, String] = Map.empty
  ): CompatSchema[T] = new CompatSchema[T]:
    override def schema: Schema[T] = baseSchema
    override def avro: AvroSchema = avroSchema
    override def validate(value: T): Either[List[String], Unit] = validator(value)
    override def isCompatible(previous: CompatSchema[_], mode: Mode): List[SchemaIssue] = 
      checker(previous, mode)
    override def metadata: Map[String, String] = props

  /** Generate AvroSchema from ZIO Schema */
  def generateAvroSchema[T](schema: Schema[T]): Either[String, AvroSchema] =
    AvroSchemaCodec.encode(schema).map { json =>
      new AvroSchema.Parser().parse(json)
    }

  /** Default compatibility checker using Avro built-in checks */
  def defaultChecker(current: CompatSchema[_])(previous: CompatSchema[_], mode: Mode): List[SchemaIssue] =
    import org.apache.avro.SchemaCompatibility
    import scala.jdk.CollectionConverters._
    
    mode match
      case Mode.Backward => 
        val result = SchemaCompatibility.checkReaderWriterCompatibility(current.avro, previous.avro)
        result.getResult.getIncompatibilities.asScala.toList.map { incomp =>
          SchemaIssue(
            path = incomp.getLocation,
            message = s"[backward] ${incomp.getType} - ${incomp.getMessage}",
            level = IssueLevel.Error
          )
        }
      case Mode.Forward => 
        val result = SchemaCompatibility.checkReaderWriterCompatibility(previous.avro, current.avro)
        result.getResult.getIncompatibilities.asScala.toList.map { incomp =>
          SchemaIssue(
            path = incomp.getLocation,
            message = s"[forward] ${incomp.getType} - ${incomp.getMessage}",
            level = IssueLevel.Error
          )
        }  
      case Mode.Full => 
        val backwardResult = SchemaCompatibility.checkReaderWriterCompatibility(current.avro, previous.avro)
        val forwardResult = SchemaCompatibility.checkReaderWriterCompatibility(previous.avro, current.avro)
        
        val backwardIssues = backwardResult.getResult.getIncompatibilities.asScala.toList.map { incomp =>
          SchemaIssue(
            path = incomp.getLocation,
            message = s"[backward] ${incomp.getType} - ${incomp.getMessage}",
            level = IssueLevel.Error
          )
        }
        
        val forwardIssues = forwardResult.getResult.getIncompatibilities.asScala.toList.map { incomp =>
          SchemaIssue(
            path = incomp.getLocation,
            message = s"[forward] ${incomp.getType} - ${incomp.getMessage}",
            level = IssueLevel.Error
          )
        }
        
        backwardIssues ++ forwardIssues
