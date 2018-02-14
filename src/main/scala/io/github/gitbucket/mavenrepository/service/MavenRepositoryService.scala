package io.github.gitbucket.mavenrepository.service

import java.io.File

import io.github.gitbucket.mavenrepository.RegistryPath
import io.github.gitbucket.mavenrepository.model.Registry
import io.github.gitbucket.mavenrepository.model.Profile._
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import org.apache.commons.io.FileUtils

trait MavenRepositoryService {

  def getMavenRepositories()(implicit s: Session): Seq[Registry] = {
    Registries.sortBy(_.name).list
  }

  def addRegistry(registry: Registry)(implicit s: Session): Unit = {
    Registries.insert(registry)

    val dir = new File(s"${RegistryPath}/${registry.name}")
    dir.mkdirs()
  }

  def updateRegistry(registry: Registry)(implicit s: Session): Unit = {
    Registries.update(registry)
  }

  def deleteRegistry(name: String)(implicit s: Session): Unit = {
    Registries.filter(_.name === name.bind).delete

    val dir = new File(s"${RegistryPath}/${name}")
    FileUtils.deleteDirectory(dir)
  }

}
