package compat.plugin

import java.io.File
import java.net.URLClassLoader
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import sbt.util.Logger

object SchemaDiscovery {
  
  /**
   * Discovers CompatSchema instances from compiled classes
   */
  def discoverSchemas(classDir: File, classpathFiles: Seq[File], log: Logger): Map[String, String] = {
    log.info(s"Scanning class directory: ${classDir.getAbsolutePath}")
    log.info(s"Classpath includes ${classpathFiles.length} files")
    
    try {
      // Create a classloader with the compiled classes and dependencies
      val urls = (classDir +: classpathFiles).map(_.toURI.toURL).toArray
      val classLoader = new URLClassLoader(urls, this.getClass.getClassLoader)
      
      // Find all classes that have companion objects with CompatSchema instances
      val compatSchemaClasses = findCompatSchemaClasses(classDir, classLoader, log)
      
      if (compatSchemaClasses.isEmpty) {
        log.warn("No CompatSchema instances found!")
        return Map.empty
      }
      
      log.info(s"Found ${compatSchemaClasses.size} classes with CompatSchema instances:")
      compatSchemaClasses.foreach(className => log.info(s"  - $className"))
      
      // Extract schemas from each CompatSchema instance
      val schemas = compatSchemaClasses.flatMap { className =>
        extractSchemaFromClass(className, classLoader, log)
      }.toMap
      
      log.info(s"Successfully extracted ${schemas.size} schemas")
      schemas
      
    } catch {
      case e: Exception =>
        log.warn(s"Failed to discover schemas: ${e.getMessage}")
        log.debug(s"Schema discovery error: ${e.getMessage}")
        // Return empty map rather than failing the build
        Map.empty
    }
  }
  
  /**
   * Recursively find .class files and check for CompatSchema companion objects
   */
  private def findCompatSchemaClasses(
    classDir: File, 
    classLoader: ClassLoader, 
    log: Logger
  ): List[String] = {
    
    def findClassFiles(dir: File, packagePrefix: String = ""): List[String] = {
      if (!dir.exists() || !dir.isDirectory()) {
        return List.empty
      }
      
      dir.listFiles().toList.flatMap { file =>
        if (file.isDirectory()) {
          val newPrefix = if (packagePrefix.isEmpty) file.getName else s"$packagePrefix.${file.getName}"
          findClassFiles(file, newPrefix)
        } else if (file.getName.endsWith(".class") && !file.getName.contains("$")) {
          // Skip inner classes and anonymous classes for now
          val className = if (packagePrefix.isEmpty) {
            file.getName.dropRight(6) // Remove .class extension
          } else {
            s"$packagePrefix.${file.getName.dropRight(6)}"
          }
          List(className)
        } else {
          List.empty
        }
      }
    }
    
    val allClasses = findClassFiles(classDir)
    log.debug(s"Found ${allClasses.size} total class files")
    
    // Filter classes that have CompatSchema instances in their companion objects
    allClasses.filter { className =>
      hasCompatSchemaInstance(className, classLoader, log)
    }
  }
  
  /**
   * Check if a class has a CompatSchema instance in its companion object
   */
  private def hasCompatSchemaInstance(
    className: String, 
    classLoader: ClassLoader, 
    log: Logger
  ): Boolean = {
    Try {
      val clazz = classLoader.loadClass(className)
      
      // Look for companion object with CompatSchema instance
      val companionClassName = className + "$"
      val companionClass = Try(classLoader.loadClass(companionClassName)).toOption
      
      companionClass.exists { companion =>
        Try {
          // Get the MODULE$ field (Scala companion object instance)
          val moduleField = companion.getDeclaredField("MODULE$")
          val companionInstance = moduleField.get(null)
          
          // Look for methods that return CompatSchema instances
          val methods = companion.getDeclaredMethods
          val compatSchemaMethod = methods.find { method =>
            method.getName.contains("compat") && 
            method.getReturnType.getName.contains("CompatSchema")
          }
          
          compatSchemaMethod.isDefined
        }.getOrElse(false)
      }
    } match {
      case Success(hasCompatSchema) => 
        if (hasCompatSchema) {
          log.debug(s"$className has CompatSchema instance")
        }
        hasCompatSchema
      case Failure(e) =>
        log.debug(s"Could not load class $className: ${e.getMessage}")
        false
    }
  }
  
  /**
   * Extract Avro schema from a CompatSchema instance
   */
  private def extractSchemaFromClass(
    className: String,
    classLoader: ClassLoader,
    log: Logger
  ): Option[(String, String)] = {
    // Set the context classloader to ensure proper resolution of given instances
    val originalClassLoader = Thread.currentThread().getContextClassLoader
    
    try {
      Thread.currentThread().setContextClassLoader(classLoader)
      
      val clazz = classLoader.loadClass(className)
      
      // Look for companion object with CompatSchema instance
      val companionClassName = className + "$"
      val companionClass = Try(classLoader.loadClass(companionClassName)).toOption
      
      companionClass.flatMap { companion =>
        Try {
          // Get the MODULE$ field (Scala companion object instance)
          val moduleField = companion.getDeclaredField("MODULE$")
          moduleField.setAccessible(true)
          val companionInstance = moduleField.get(null)
          
          log.debug(s"Found companion object for $className")
          
          // Look for methods that return CompatSchema instances
          val methods = companion.getDeclaredMethods
          log.debug(s"Found ${methods.length} methods in companion object")
          
          // Find the CompatSchema method - look for methods containing "compat"
          val compatSchemaMethod = methods.find { method =>
            val returnTypeName = method.getReturnType.getName
            method.getParameterCount == 0 && // No parameters
            method.getName.contains("compat") && 
            (returnTypeName.contains("CompatSchema") || returnTypeName.contains("$CompatSchema"))
          }
          
          compatSchemaMethod match {
            case Some(method) =>
              log.debug(s"Found CompatSchema method: ${method.getName} -> ${method.getReturnType.getName}")
              method.setAccessible(true)
              
              // Try to invoke the method within the correct classloader context
              Try {
                val compatSchemaInstance = method.invoke(companionInstance)
                
                if (compatSchemaInstance == null) {
                  log.warn(s"CompatSchema instance returned null for $className")
                  None
                } else {
                  log.debug(s"Got CompatSchema instance: ${compatSchemaInstance.getClass.getName}")
                  
                  // Call the avro method to get the schema
                  Try {
                    val avroMethod = compatSchemaInstance.getClass.getMethod("avro")
                    val avroSchema = avroMethod.invoke(compatSchemaInstance)
                    log.debug(s"Got Avro schema: ${avroSchema.getClass.getName}")
                    
                    // Convert to JSON string - Avro Schema toString() should work
                    val avroJson = avroSchema.toString
                    
                    log.info(s"Successfully extracted schema for $className (${avroJson.length} chars)")
                    Some((className, avroJson))
                  } match {
                    case Success(result) => result
                    case Failure(e) =>
                      // Unwrap InvocationTargetException to get the real cause
                      val rootCause = e match {
                        case ite: java.lang.reflect.InvocationTargetException if ite.getCause != null =>
                          ite.getCause
                        case _ => e
                      }
                      log.warn(s"Failed to get Avro schema from CompatSchema for $className: ${rootCause.getClass.getName}: ${rootCause.getMessage}")
                      log.debug(s"Avro extraction error details: ${rootCause.toString}")
                      None
                  }
                }
              } match {
                case Success(result) => result
                case Failure(e) =>
                  // Unwrap InvocationTargetException to get the real cause
                  val rootCause = e match {
                    case ite: java.lang.reflect.InvocationTargetException if ite.getCause != null =>
                      ite.getCause
                    case _ => e
                  }
                  log.warn(s"Failed to invoke CompatSchema method for $className: ${rootCause.getClass.getName}: ${rootCause.getMessage}")
                  log.debug(s"Method invocation error details: ${rootCause.toString}")
                  
                  // Print full stack trace for debugging
                  rootCause.printStackTrace()
                  None
              }
            case None =>
              log.warn(s"No CompatSchema method found for $className")
              log.debug(s"Available methods: ${methods.map(m => s"${m.getName}(${m.getParameterTypes.mkString(",")}) -> ${m.getReturnType.getName}").mkString(", ")}")
              None
          }
        } match {
          case Success(result) => result
          case Failure(e) =>
            log.warn(s"Failed to access companion object for $className: ${e.getMessage}")
            log.debug(s"Companion object error: ${e.getMessage}")
            None
        }
      }
    } catch {
      case e: Exception =>
        log.warn(s"Failed to extract schema from $className: ${e.getMessage}")
        log.debug(s"Schema extraction error for $className: ${e.getMessage}")
        None
    } finally {
      // Restore the original context classloader
      Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }
}