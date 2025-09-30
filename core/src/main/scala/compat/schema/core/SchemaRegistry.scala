package compat.schema.core

import scala.collection.concurrent.TrieMap

/**
 * Registry for collecting schemas during compilation.
 * This is used to gather all CompatSchema instances for embedding in JAR manifests.
 */
object SchemaRegistry {
  private val schemas: TrieMap[String, String] = TrieMap.empty
  
  /**
   * Register a schema for a given type.
   * Called during macro expansion to collect schemas.
   */
  def register(typeName: String, avroJson: String): Unit = {
    schemas.put(typeName, avroJson)
  }
  
  /**
   * Get all registered schemas.
   * Used by the SBT plugin to extract schemas for manifest embedding.
   */
  def getAllSchemas(): Map[String, String] = schemas.toMap
  
  /**
   * Clear all registered schemas.
   * Used to reset between compilations.
   */
  def clear(): Unit = schemas.clear()
  
  /**
   * Get a specific schema by type name.
   */
  def getSchema(typeName: String): Option[String] = schemas.get(typeName)
  
  /**
   * Check if any schemas are registered.
   */
  def hasSchemas: Boolean = schemas.nonEmpty
  
  /**
   * Get the count of registered schemas.
   */
  def count: Int = schemas.size
}