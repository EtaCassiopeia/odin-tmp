package compat.schema.core

/** Main exports for easy importing */
object exports:
  export compat.schema.core.CompatSchema
  export compat.schema.core.AutoDerivation.{derived, summon}
  export compat.schema.core.Mode
  export compat.schema.core.{SchemaIssue, IssueLevel}
