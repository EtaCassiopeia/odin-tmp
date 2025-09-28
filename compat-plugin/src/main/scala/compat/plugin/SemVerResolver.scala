package compat.plugin

import coursier._
import coursier.cache.FileCache
import coursier.util.Task
import scala.util.{Try, Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.matching.Regex

object SemVerResolver {
  
  case class Version(major: Int, minor: Int, patch: Int, suffix: Option[String] = None) {
    override def toString: String = 
      s"$major.$minor.$patch" + suffix.fold("")(s => s"-$s")
  }
  
  object Version {
    private val VersionRegex: Regex = """(\d+)\.(\d+)\.(\d+)(?:-(.+))?""".r
    
    def parse(versionStr: String): Option[Version] = versionStr match {
      case VersionRegex(major, minor, patch, suffix) =>
        Try {
          Version(major.toInt, minor.toInt, patch.toInt, Option(suffix))
        }.toOption
      case _ => None
    }
    
    implicit val ordering: Ordering[Version] = Ordering.by { v: Version =>
      (v.major, v.minor, v.patch, v.suffix.getOrElse(""))
    }
  }
  
  def resolvePreviousVersions(
    organization: String,
    name: String,
    currentVersion: String,
    strategy: String,
    repositoryUrl: String
  ): Try[Seq[String]] = {
    
    Try {
      val cache = FileCache[Task]()
      val repositories = Seq(coursier.Repositories.central) ++ 
        (if (repositoryUrl != "https://repo1.maven.org/maven2") Seq(coursier.MavenRepository(repositoryUrl)) else Seq.empty)
      
      val module = coursier.Module(coursier.core.Organization(organization), coursier.ModuleName(name))
      
      // Simplified version resolution for demo
      // In a real implementation, would properly use coursier to fetch versions
      val allVersions = List("1.0.0", "1.1.0", "1.2.0", "2.0.0") // Dummy versions
      
      val parsedVersions = allVersions.flatMap(Version.parse)
        .filter(_.suffix.isEmpty) // Filter out snapshots and pre-releases for now
        .sorted
      
      Version.parse(currentVersion) match {
        case Some(current) =>
          strategy match {
            case "latestMinor" =>
              // Find the latest version with the same major and minor but lower patch
              parsedVersions.filter { v =>
                v.major == current.major && 
                v.minor == current.minor && 
                v.patch < current.patch
              }.lastOption.toSeq.map(_.toString)
              
            case "latestPatch" =>
              // Find all versions with the same major and minor
              parsedVersions.filter { v =>
                v.major == current.major && 
                v.minor == current.minor && 
                v != current
              }.lastOption.toSeq.map(_.toString)
              
            case "previousMajor" =>
              // Find the latest version with the previous major
              if (current.major > 0) {
                parsedVersions.filter(_.major == current.major - 1)
                  .lastOption.toSeq.map(_.toString)
              } else {
                Seq.empty
              }
              
            case "all" =>
              // All previous versions (be careful with this!)
              parsedVersions.filter(v => Version.ordering.compare(v, current) < 0).map(_.toString)
              
            case _ =>
              // Default to latestMinor
              parsedVersions.filter { v =>
                v.major == current.major && 
                v.minor == current.minor && 
                v.patch < current.patch
              }.lastOption.toSeq.map(_.toString)
          }
          
        case None =>
          // If we can't parse the current version, return empty
          Seq.empty
      }
    }.recover {
      case e: Exception =>
        // Return empty sequence on any error
        Seq.empty
    }
  }
}