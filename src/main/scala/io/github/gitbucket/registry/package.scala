package io.github.gitbucket

package object registry {

  val Registries = Seq("releases", "snapshots")

}


case class Registry(name: String, overwrite: Boolean)