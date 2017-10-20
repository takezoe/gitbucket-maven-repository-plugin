package io.github.gitbucket

package object registry {

  val Registries = Seq(Registry("releases", false), Registry("snapshots", true))

}


case class Registry(name: String, overwrite: Boolean)