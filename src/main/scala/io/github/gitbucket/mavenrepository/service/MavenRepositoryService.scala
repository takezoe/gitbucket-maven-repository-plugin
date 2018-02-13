package io.github.gitbucket.mavenrepository.service

import io.github.gitbucket.mavenrepository.model.Registry
import io.github.gitbucket.mavenrepository.model.Profile._
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._

trait MavenRepositoryService {

  def getMavenRepositories()(implicit s: Session): Seq[Registry] = {
    Registries.sortBy(_.name).list
  }

}
