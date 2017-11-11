package io.github.gitbucket

import gitbucket.core.util.Directory

package object mavenrepository {

  val Registries = Seq(
    Registry("releases", false),
    Registry("snapshots", true)
  )

  val RegistryPath = s"${Directory.GitBucketHome}/maven"

  case class Registry(name: String, overwrite: Boolean)

}


