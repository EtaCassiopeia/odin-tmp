package compat.schema.core

import scala.annotation.StaticAnnotation

/** Marks a type for schema compatibility checking */
class CompatCheck extends StaticAnnotation

/** Override global compatibility mode for this specific type */
class CompatMode(mode: String) extends StaticAnnotation

/** Custom refined type compatibility rule */
class RefinedCompat(rule: String) extends StaticAnnotation

/** Compatibility mode enumeration */
enum Mode:
  case Backward, Forward, Full

  def asString: String = this match
    case Backward => "backward"
    case Forward => "forward"
    case Full => "full"

object Mode:
  def fromString(s: String): Mode = s.toLowerCase match
    case "backward" => Backward
    case "forward" => Forward
    case "full" | _ => Full