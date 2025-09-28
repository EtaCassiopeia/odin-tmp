# Scala Schema Compatibility Check System

A build-time schema compatibility checking system for Scala projects that prevents breaking changes in domain models used for JSON serialization/deserialization (using zio-schema and zio-json) for databases and APIs.

## Features

- ✅ **Explicit Opt-in**: Only check models explicitly annotated with `@CompatCheck`
- ✅ **Avro Schema Evolution**: Leverages Apache Avro's robust compatibility checking
- ✅ **JAR Manifest Embedding**: Schemas are embedded in JAR manifests for easy extraction
- ✅ **SemVer Version Fetching**: Automatically fetches previous artifact versions for comparison
- ✅ **Refined Type Support**: Value-level compatibility checks for refined types
- ✅ **Complex Model Support**: Handles traits, case classes, inheritance, and enumerations
- ✅ **Pinpoint Issues**: Exact field-level issue reporting
- ✅ **SBT Integration**: Seamlessly integrates with SBT for CI pipelines
- ✅ **Multiple Compatibility Modes**: Backward, Forward, and Full compatibility checking

## Architecture

The system consists of two main modules:

1. **Core Library** (`schema-compat-core`): Scala 3 library providing type classes, macros, and schema generation
2. **SBT Plugin** (`sbt-schema-compat`): Scala 2.12 SBT plugin for build integration and compatibility checking

## Quick Start

### 1. Add Dependencies

In your `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.mohsen" % "sbt-schema-compat" % "0.1.0")
```

In your `build.sbt`:

```scala
libraryDependencies += "com.github.mohsen" %% "schema-compat-core" % "0.1.0"

// Enable the plugin
enablePlugins(SchemaCompatPlugin)

// Configure compatibility settings
schemaCompatMode := "full" // backward, forward, or full
schemaCompatSemverStrategy := "latestMinor" // latestMinor, latestPatch, previousMajor
schemaCompatFailOnBreak := true
```

### 2. Annotate Your Models

```scala
import compat.schema.core._
import compat.schema.core.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.NonEmpty
import eu.timepit.refined.numeric.Positive

@CompatCheck
@CompatMode("full")
case class User(
  id: UUID,
  name: String Refined NonEmpty,
  email: String,
  age: Option[Int Refined Positive] = None,
  status: UserStatus = UserStatus.Active
)

object User:
  given compatUser: CompatSchema[User] = AutoDerivation.derived

enum UserStatus:
  case Active, Inactive, Suspended

object UserStatus:
  given compatUserStatus: CompatSchema[UserStatus] = AutoDerivation.derived
```

### 3. Build and Check

```bash
sbt compile
# or explicitly run the compatibility check
sbt schemaCompatCheck
```

## Compatibility Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `backward` | New code can read old data | API consumers, database readers |
| `forward` | Old code can read new data | Database writers, message producers |
| `full` | Both backward and forward | Complete compatibility |

## SemVer Strategies

| Strategy | Description |
|----------|-------------|
| `latestMinor` | Compare against latest patch in same minor version |
| `latestPatch` | Compare against all patches in same minor version |
| `previousMajor` | Compare against latest version of previous major |

## Configuration Options

All configuration is done via SBT settings:

```scala
// Compatibility mode: backward, forward, full
schemaCompatMode := "full"

// SemVer strategy for finding previous versions
schemaCompatSemverStrategy := "latestMinor"

// Additional repository for artifact resolution
schemaCompatRepository := "https://your-private-repo.com/maven2"

// Fail build on compatibility breaks
schemaCompatFailOnBreak := true

// Report level: summary, detailed
schemaCompatReportLevel := "detailed"

// Check transitive types recursively
schemaCompatTransitive := true

// Optional JSON report file
schemaCompatReportFile := Some(file("target/schema-compat-report.json"))
```

## Refined Type Support

The system provides built-in support for common refined types:

- `String Refined NonEmpty`: Checks string length constraints
- `Int Refined Positive`: Checks numeric constraints
- Pattern matching support (basic regex compatibility)

### Custom Refined Types

You can provide custom CompatSchema instances for your refined types:

```scala
given customRefinedInstance: CompatSchema[String Refined MyCustomConstraint] = 
  refined.matchesRegexCompat("^[A-Z][a-z]+$")
```

## Sample Output

```
[info] Starting schema compatibility check...
[info] Found 3 schema(s) in current JAR: User, Order, PaymentInfo
[info] Comparing against demo-1.0.0.jar

=== Schema Compatibility Report ===
Total issues: 2 (1 errors, 1 warnings, 0 info)

📋 Schema: User
  ❌ Errors:
    • /fields/email: READER_FIELD_MISSING_DEFAULT_VALUE - field email type changed from STRING to UNION

  ⚠️  Warnings:
    • refinement: Refined type constraint changed from NonEmpty to MatchesRegex

💡 Suggestions:
  • Consider adding default values to new required fields
```

## Demo Project

The included demo project showcases various scenarios:

```bash
cd demo
sbt compile  # This will run schema compatibility checks
```

## Supported Types

- ✅ Case classes
- ✅ Sealed traits/enums
- ✅ Optional fields
- ✅ Collections (List, Vector, Set)
- ✅ Refined types (NonEmpty, Positive, MatchesRegex)
- ✅ Nested structures
- ✅ Union types

## Limitations

- Regex pattern compatibility uses heuristic checking (not full subset analysis)
- Runtime reflection for schema discovery has performance implications
- Requires Scala 3.3+ for the core library
- SBT plugin requires Scala 2.12 (SBT limitation)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the Apache License 2.0.

---

For more detailed documentation and examples, see the `demo/` directory and the source code documentation.