package io.github.gitbucket.mavenrepository.service

import java.io.File

import io.github.gitbucket.mavenrepository.RegistryPath
import io.github.gitbucket.mavenrepository.model.Registry
import io.github.gitbucket.mavenrepository.model.Profile._
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import org.apache.commons.io.FileUtils
import gitbucket.core.util.SyntaxSugars._

trait MavenRepositoryService {

  def getMavenRepository(name: String)(implicit s: Session): Option[Registry] = {
    Registries.filter(_.name === name.bind).firstOption
  }

  def getMavenRepositories()(implicit s: Session): Seq[Registry] = {
    Registries.sortBy(_.name).list
  }

  def createRegistry(name: String, description: Option[String], overwrite: Boolean, isPrivate: Boolean)
                    (implicit s: Session): Unit = {
    Registries.insert(Registry(name, description, overwrite, isPrivate))

    val dir = new File(s"${RegistryPath}/${name}")
    dir.mkdirs()
  }

  def updateRegistry(name: String, description: Option[String], overwrite: Boolean, isPrivate: Boolean)
                    (implicit s: Session): Unit = {
    Registries.filter(_.name === name.bind)
      .map(t => (t.description.?, t.overwrite, t.isPrivate))
      .update((description, overwrite, isPrivate))
  }

  def deleteRegistry(name: String)(implicit s: Session): Unit = {
    Registries.filter(_.name === name.bind).delete

    val dir = new File(s"${RegistryPath}/${name}")
    FileUtils.deleteDirectory(dir)
  }

}
