package compat.schema.core

import zio.schema.{Schema, DeriveSchema}
import scala.quoted.*
import scala.compiletime.summonInline

/** Automatic derivation for CompatSchema using Scala 3 macros */
object AutoDerivation:
  
  /** Derive CompatSchema for types annotated with @CompatCheck */
  inline def derived[T]: CompatSchema[T] = ${ derivedImpl[T] }
  
  /** Summon a CompatSchema instance (with compile-time error if missing annotation) */
  inline def summon[T]: CompatSchema[T] = summonInline[CompatSchema[T]]
  
  private def derivedImpl[T: Type](using Quotes): Expr[CompatSchema[T]] =
    import quotes.reflect.*
    
    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol
    
    // Check for @CompatCheck annotation
    val hasCompatCheck = sym.annotations.exists { ann =>
      ann.tpe <:< TypeRepr.of[CompatCheck]
    }
    
    if !hasCompatCheck then
      report.errorAndAbort(s"Type ${tpe.show} must be annotated with @CompatCheck for automatic derivation")
    
    // Extract compatibility mode if present
    val compatModeExpr = sym.annotations.collectFirst {
      case ann if ann.tpe <:< TypeRepr.of[CompatMode] =>
        ann match
          case Apply(_, List(Literal(StringConstant(mode)))) => Expr(mode)
          case _ => '{ "full" }
    }.getOrElse('{ "full" })
    
    // Store schema information for compile-time collection
    val typeName = tpe.show
    
    '{
      val baseSchema = DeriveSchema.gen[T]
      CompatSchema.generateAvroSchema(baseSchema) match
        case Left(error) => 
          throw new RuntimeException(s"Failed to generate Avro schema for ${${Expr(typeName)}}: $error")
        case Right(avroSchema) =>
          // Add metadata
          avroSchema.addProp("compatMode", $compatModeExpr)
          avroSchema.addProp("scalaType", ${Expr(typeName)})
          
          // Store schema for compile-time collection (if registry is available)
          try {
            SchemaRegistry.register(${Expr(typeName)}, avroSchema.toString(true))
          } catch {
            case _: Throwable => // Registry not available during compilation, ignore
          }
          
          // Write schema to file for SBT plugin to read
          try {
            val outputDir = new java.io.File("target/compat-schemas")
            outputDir.mkdirs()
            val schemaFile = new java.io.File(outputDir, s"${${Expr(typeName.replace(".", "_"))}}.json")
            val writer = new java.io.PrintWriter(schemaFile)
            writer.println(avroSchema.toString(true))
            writer.close()
          } catch {
            case _: Throwable => // File writing failed, ignore
          }
          
          CompatSchema(
            baseSchema,
            avroSchema,
            (_: T) => Right(()),
            (previous, mode) => List.empty, // Simplified for demo - would implement proper checking
            Map(
              "compatMode" -> $compatModeExpr,
              "scalaType" -> ${Expr(typeName)}
            )
          )
    }
