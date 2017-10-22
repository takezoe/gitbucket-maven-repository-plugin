package io.github.gitbucket

import gitbucket.core.util.Directory

package object registry {

  val Registries = Seq(Registry("releases", false), Registry("snapshots", true))

  val RegistryPath = s"${Directory.GitBucketHome}/registries"

}


case class Registry(name: String, overwrite: Boolean)