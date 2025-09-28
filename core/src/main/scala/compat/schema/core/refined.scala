package compat.schema.core

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.collection.{MinSize, MaxSize}
import zio.schema.Schema
import org.apache.avro.{Schema => AvroSchema}
import java.util.regex.Pattern
import scala.util.{Try, Random}

/** Refined type CompatSchema instances */
object refined:
  
  /** CompatSchema for NonEmpty String */
  given compatNonEmptyString: CompatSchema[String Refined NonEmpty] =
    val baseSchema = Schema[String].transformOrFail[String Refined NonEmpty](
      s => eu.timepit.refined.refineV[NonEmpty](s),
      refined => Right(refined.value)
    )
    
    val avroSchema = AvroSchema.create(AvroSchema.Type.STRING)
    avroSchema.addProp("refined", "NonEmpty")
    avroSchema.addProp("constraint", "length > 0")
    
    CompatSchema(
      baseSchema,
      avroSchema,
      value => if value.value.nonEmpty then Right(()) else Left(List("String cannot be empty")),
      (previous, mode) => checkNonEmptyCompatibility(previous, mode),
      Map("refined" -> "NonEmpty", "constraint" -> "length > 0")
    )
  
  private def checkNonEmptyCompatibility(previous: CompatSchema[_], mode: Mode): List[SchemaIssue] =
    val oldRefined = previous.avro.getProp("refined")
    if oldRefined == "NonEmpty" then List.empty
    else List(
      SchemaIssue(
        path = "root",
        message = s"Refinement changed from $oldRefined to NonEmpty; may reject previously valid empty strings",
        level = IssueLevel.Warning
      )
    )

  /** CompatSchema for Positive Int */
  given compatPositiveInt: CompatSchema[Int Refined Positive] =
    val baseSchema = Schema[Int].transformOrFail[Int Refined Positive](
      i => eu.timepit.refined.refineV[Positive](i),
      refined => Right(refined.value)
    )
    
    val avroSchema = AvroSchema.create(AvroSchema.Type.INT)
    avroSchema.addProp("refined", "Positive")
    avroSchema.addProp("constraint", "> 0")
    
    CompatSchema(
      baseSchema,
      avroSchema,
      value => if value.value > 0 then Right(()) else Left(List("Number must be positive")),
      (previous, mode) => checkPositiveCompatibility(previous, mode),
      Map("refined" -> "Positive", "constraint" -> "> 0")
    )
  
  private def checkPositiveCompatibility(previous: CompatSchema[_], mode: Mode): List[SchemaIssue] =
    val oldRefined = previous.avro.getProp("refined")
    if oldRefined == "Positive" then List.empty
    else List(
      SchemaIssue(
        path = "root",
        message = s"Refinement changed from $oldRefined to Positive; may reject previously valid non-positive values",
        level = IssueLevel.Warning
      )
    )

  /** CompatSchema for MatchesRegex String - simplified version */
  def matchesRegexCompat(pattern: String): CompatSchema[String Refined MatchesRegex[String]] =
    val baseSchema = Schema[String].transformOrFail[String Refined MatchesRegex[String]](
      s => Right(s.asInstanceOf[String Refined MatchesRegex[String]]), // Simplified for demo
      refined => Right(refined.value)
    )
    
    val avroSchema = AvroSchema.create(AvroSchema.Type.STRING)
    avroSchema.addProp("refined", "MatchesRegex")
    avroSchema.addProp("pattern", pattern)
    
    CompatSchema(
      baseSchema,
      avroSchema,
      value => 
        Try(Pattern.compile(pattern).matcher(value.value).matches()) match
          case scala.util.Success(true) => Right(())
          case scala.util.Success(false) => Left(List(s"String does not match pattern: $pattern"))
          case scala.util.Failure(e) => Left(List(s"Invalid regex pattern: ${e.getMessage}")),
      (previous, mode) => checkRegexCompatibility(pattern, previous, mode),
      Map("refined" -> "MatchesRegex", "pattern" -> pattern)
    )
  
  private def checkRegexCompatibility(newPattern: String, previous: CompatSchema[_], mode: Mode): List[SchemaIssue] =
    val oldPattern = previous.avro.getProp("pattern")
    if oldPattern == null then List(
      SchemaIssue(
        path = "root",
        message = "Old schema missing pattern information",
        level = IssueLevel.Warning
      )
    )
    else if isPatternSuperset(newPattern, oldPattern) then List.empty
    else List(
      SchemaIssue(
        path = "root",
        message = s"Pattern '$newPattern' is not a superset of '$oldPattern'; may reject previously valid values",
        level = IssueLevel.Error
      )
    )

  private def isPatternSuperset(newPattern: String, oldPattern: String): Boolean =
    try
      // Simplified heuristic check
      // In practice, you'd want more sophisticated regex subset analysis
      val oldRegex = Pattern.compile(oldPattern)
      val newRegex = Pattern.compile(newPattern)
      
      // Generate some test strings that match the old pattern and verify they match the new one
      val testStrings = generateTestStrings(oldPattern, 50)
      testStrings.nonEmpty && testStrings.forall(s => newRegex.matcher(s).matches())
    catch
      case _: Exception => false // If patterns are malformed, assume incompatible

  private def generateTestStrings(pattern: String, count: Int): List[String] =
    // Very simplified test string generation
    // In practice, you'd want a more sophisticated regex-to-string generator
    val candidateStrings = List(
      "test", "example", "abc123", "", "a", "1", "Test123!", "hello", "world",
      "123", "abc", "ABC", "a1b2c3", "test@example.com", "user_name", 
      "CamelCase", "snake_case", "kebab-case"
    )
    
    Try {
      val regex = Pattern.compile(pattern)
      candidateStrings.filter(s => regex.matcher(s).matches()).take(count)
    }.getOrElse(List.empty)