package compat.plugin

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.util.{Try, Success, Failure}

object ArtifactFetcher {
  
  def fetchArtifact(
    organization: String,
    name: String,
    version: String,
    repositoryUrl: String,
    targetDir: File
  ): Try[File] = {
    
    Try {
      val targetFile = new File(targetDir, s"$name-$version.jar")
      
      if (!targetFile.exists()) {
        targetFile.getParentFile.mkdirs()
        
        // First try to find in local Ivy cache
        val ivyHome = System.getProperty("user.home") + "/.ivy2/local"
        val ivyPath = s"$ivyHome/$organization/$name/$version/jars/$name.jar"
        val ivyFile = new File(ivyPath)
        
        if (ivyFile.exists() && ivyFile.length() > 0) {
          // Copy from Ivy cache
          Files.copy(ivyFile.toPath, targetFile.toPath, StandardCopyOption.REPLACE_EXISTING)
          println(s"Copied from Ivy cache: ${ivyFile.getAbsolutePath} -> ${targetFile.getAbsolutePath} (${ivyFile.length()} bytes)")
        } else {
          // Try Maven local repository
          val m2Home = System.getProperty("user.home") + "/.m2/repository"
          val m2Path = s"$m2Home/${organization.replace('.', '/')}/$name/$version/$name-$version.jar"
          val m2File = new File(m2Path)
          
          if (m2File.exists() && m2File.length() > 0) {
            Files.copy(m2File.toPath, targetFile.toPath, StandardCopyOption.REPLACE_EXISTING)
            println(s"Copied from Maven local: ${m2File.getAbsolutePath} -> ${targetFile.getAbsolutePath} (${m2File.length()} bytes)")
          } else {
            throw new RuntimeException(s"Could not find artifact $organization:$name:$version in local caches. Tried: $ivyPath, $m2Path")
          }
        }
      }
      
      targetFile
    }
  }
}
