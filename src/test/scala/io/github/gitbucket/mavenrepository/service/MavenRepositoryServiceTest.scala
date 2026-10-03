package io.github.gitbucket.mavenrepository.service

import io.github.gitbucket.mavenrepository.{RegistryPath, TestDatabase}
import io.github.gitbucket.mavenrepository.model.Profile._
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import io.github.gitbucket.mavenrepository.model.Registry
import org.scalatest.funsuite.AnyFunSuite

import java.io.File

class MavenRepositoryServiceTest extends AnyFunSuite with MavenRepositoryService {

  private def withTestDB[A](action: Session => A): A = TestDatabase.withTestDB(action)

  test("the migration creates the default releases and snapshots repositories") {
    withTestDB { implicit session =>
      assert(getMavenRepositories() == Seq(
        Registry("releases", Some("GitBucket Releases"), overwrite = false, isPrivate = false),
        Registry("snapshots", Some("GitBucket Snapshots"), overwrite = true, isPrivate = false)
      ))
    }
  }

  test("createRegistry persists a registry and creates its directory") {
    withTestDB { implicit session =>
      createRegistry("internal", Some("Internal"), overwrite = false, isPrivate = true)

      assert(getMavenRepository("internal").contains(Registry("internal", Some("Internal"), false, true)))
      assert(new File(s"$RegistryPath/internal").isDirectory)
    }
  }

  test("getMavenRepository returns None for an unknown registry") {
    withTestDB { implicit session =>
      assert(getMavenRepository("does-not-exist").isEmpty)
    }
  }

  test("getMavenRepositories returns all registries sorted by name") {
    withTestDB { implicit session =>
      createRegistry("zeta", None, overwrite = true, isPrivate = false)
      createRegistry("alpha", None, overwrite = false, isPrivate = false)

      assert(getMavenRepositories().map(_.name) == Seq("alpha", "releases", "snapshots", "zeta"))
    }
  }

  test("updateRegistry updates description, overwrite and isPrivate") {
    withTestDB { implicit session =>
      updateRegistry("releases", Some("Updated"), overwrite = true, isPrivate = true)

      val registry = getMavenRepository("releases").get
      assert(registry.description.contains("Updated"))
      assert(registry.overwrite)
      assert(registry.isPrivate)
    }
  }

  test("deleteRegistry removes the registry and its directory") {
    withTestDB { implicit session =>
      createRegistry("internal", None, overwrite = false, isPrivate = false)
      assert(new File(s"$RegistryPath/internal").isDirectory)

      deleteRegistry("internal")

      assert(getMavenRepository("internal").isEmpty)
      assert(!new File(s"$RegistryPath/internal").exists)
    }
  }
}
