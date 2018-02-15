package io.github.gitbucket.mavenrepository.model

trait RegistryComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val Registries = TableQuery[Registries]

  class Registries(tag: Tag) extends Table[Registry](tag, "REGISTRY"){
    val name        = column[String]("NAME")
    val description = column[String]("DESCRIPTION")
    val overwrite   = column[Boolean]("OVERWRITE")
    val isPrivate   = column[Boolean]("PRIVATE")
    def * = (name, description.?, overwrite, isPrivate) <> (Registry.tupled, Registry.unapply)
  }

}

case class Registry(
  name: String,
  description: Option[String],
  overwrite: Boolean,
  isPrivate: Boolean
)