

object test$minusdiscovery extends App {
val scriptPath = """test-discovery.sc"""
/*<script>*/


//> using scala "2.12.18"
//> using lib "com.github.mohsen::schema-compat-core:0.1.0-SNAPSHOT"
//> using lib "org.apache.avro:avro:1.11.3"

import java.io.File
import java.net.URLClassLoader
import scala.util.{Try, Success, Failure}

// Simple test to see if we can load and discover schemas
object TestSchemaDiscovery extends App {
  
  val classDir = new File("demo/target/scala-3.3.3/classes")
  val classpathFiles = Seq(
    new File(System.getProperty("user.home") + "/.ivy2/local/com.github.mohsen/schema-compat-core_3/0.1.0-SNAPSHOT/jars/schema-compat-core_3.jar")
  )
  
  if (!classDir.exists()) {
    println("Class directory does not exist. Please compile demo project first.")
    sys.exit(1)
  }
  
  println(s"Scanning class directory: ${classDir.getAbsolutePath}")
  println(s"Classpath includes ${classpathFiles.length} files")
  
  // Create a classloader with the compiled classes and dependencies
  val urls = (classDir +: classpathFiles).filter(_.exists()).map(_.toURI.toURL).toArray
  val classLoader = new URLClassLoader(urls, this.getClass.getClassLoader)
  
  // Look for specific classes we know exist
  val testClasses = Seq(
    "com.example.models.User",
    "com.example.models.Order",
    "com.example.models.UserStatus",
    "com.example.models.OrderStatus"
  )
  
  testClasses.foreach { className =>
    Try {
      val clazz = classLoader.loadClass(className)
      val annotations = clazz.getAnnotations
      
      println(s"\nClass: $className")
      println(s"  Annotations: ${annotations.map(_.annotationType().getSimpleName).mkString(", ")}")
      
      // Check for @CompatCheck annotation
      val hasCompatCheck = annotations.exists { annotation =>
        annotation.annotationType().getSimpleName == "CompatCheck"
      }
      
      if (hasCompatCheck) {
        println(s"  ✓ Has @CompatCheck annotation")
        
        // Look for companion object
        val companionClassName = className + "$"
        Try {
          val companionClass = classLoader.loadClass(companionClassName)
          val moduleField = companionClass.getDeclaredField("MODULE$")
          val companionInstance = moduleField.get(null)
          
          println(s"  ✓ Found companion object")
          
          // Look for CompatSchema methods
          val methods = companionClass.getDeclaredMethods
          val compatMethods = methods.filter(_.getName.contains("compat"))
          
          println(s"  Methods containing 'compat': ${compatMethods.map(_.getName).mkString(", ")}")
          
        } match {
          case Success(_) => ()
          case Failure(e) => println(s"  ✗ Failed to load companion: ${e.getMessage}")
        }
        
      } else {
        println(s"  ✗ No @CompatCheck annotation")
      }
      
    } match {
      case Success(_) => ()
      case Failure(e) => println(s"Failed to load class $className: ${e.getMessage}")
    }
  }
}
/*</script>*/ /*<generated>*/
/*</generated>*/
}
