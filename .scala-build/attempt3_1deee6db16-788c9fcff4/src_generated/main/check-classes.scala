

final class check$minusclasses$_ {
def args = check$minusclasses_sc.args$
def scriptPath = """check-classes.sc"""
/*<script>*/


//> using scala "3.3.3"

import java.io.File
import java.net.URLClassLoader
import scala.util.{Try, Success, Failure}

// Simple test to check if compiled classes exist and can be loaded
@main def checkClasses() = {
  
  val classDir = new File("demo/target/scala-3.3.3/classes")
  
  if (!classDir.exists()) {
    println("Class directory does not exist. Please compile demo project first.")
    sys.exit(1)
  }
  
  println(s"Scanning class directory: ${classDir.getAbsolutePath}")
  
  // Recursively find all .class files
  def findClassFiles(dir: File, packagePrefix: String = ""): List[String] = {
    if (!dir.exists() || !dir.isDirectory()) {
      return List.empty
    }
    
    dir.listFiles().toList.flatMap { file =>
      if (file.isDirectory()) {
        val newPrefix = if (packagePrefix.isEmpty) file.getName else s"$packagePrefix.${file.getName}"
        findClassFiles(file, newPrefix)
      } else if (file.getName.endsWith(".class") && !file.getName.contains("$")) {
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
  
  val classNames = findClassFiles(classDir)
  println(s"Found ${classNames.length} class files:")
  
  classNames.sorted.foreach { className =>
    println(s"  - $className")
  }
  
  // Try to load a few key classes
  val testClasses = Seq(
    "com.example.models.User",
    "com.example.models.Order"
  )
  
  val urls = Array(classDir.toURI.toURL)
  val classLoader = new URLClassLoader(urls, this.getClass.getClassLoader)
  
  testClasses.foreach { className =>
    Try(classLoader.loadClass(className)) match {
      case Success(clazz) => 
        val annotations = clazz.getAnnotations
        println(s"\n✓ Successfully loaded $className")
        println(s"  Annotations: ${annotations.map(_.annotationType().getSimpleName).mkString(", ")}")
      case Failure(e) => 
        println(s"\n✗ Failed to load $className: ${e.getMessage}")
    }
  }
}
/*</script>*/ /*<generated>*//*</generated>*/
}

object check$minusclasses_sc {
  private var args$opt0 = Option.empty[Array[String]]
  def args$set(args: Array[String]): Unit = {
    args$opt0 = Some(args)
  }
  def args$opt: Option[Array[String]] = args$opt0
  def args$: Array[String] = args$opt.getOrElse {
    sys.error("No arguments passed to this script")
  }

  lazy val script = new check$minusclasses$_

  def main(args: Array[String]): Unit = {
    args$set(args)
    val _ = script.hashCode() // hashCode to clear scalac warning about pure expression in statement position
  }
}

export check$minusclasses_sc.script as `check-classes`

