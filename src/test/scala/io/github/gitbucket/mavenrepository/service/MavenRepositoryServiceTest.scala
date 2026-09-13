package io.github.gitbucket.mavenrepository.service

import io.github.gitbucket.mavenrepository.RegistryPath
import io.github.gitbucket.mavenrepository.model.Profile._
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import io.github.gitbucket.mavenrepository.model.Registry
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.file.Files
import java.util.UUID

class MavenRepositoryServiceTest extends AnyFunSuite with MavenRepositoryService {

  // Redirect RegistryPath away from the real ~/.gitbucket.
  sys.props("gitbucket.home") = Files.createTempDirectory("mvn-repo-plugin-test").toString

  // Mirrors update/gitbucket-maven-repository_1.1.0.xml exactly (DESCRIPTION is nullable
  // there; Registries.schema.create would get that wrong since the column is a plain
  // String, with optionality only applied at the row-mapping projection).
  private def withTestDB[A](action: Session => A): A = {
    val db = Database.forURL(s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
    db.withSession { implicit session =>
      session.conn.createStatement().execute("""
        create table REGISTRY (
          NAME varchar(100) not null primary key,
          DESCRIPTION text,
          OVERWRITE boolean not null,
          PRIVATE boolean not null
        )
      """)
      action(session)
    }
  }

  test("createRegistry persists a registry and creates its directory") {
    withTestDB { implicit session =>
      createRegistry("releases", Some("Releases"), overwrite = false, isPrivate = false)

      assert(getMavenRepository("releases").contains(Registry("releases", Some("Releases"), false, false)))
      assert(new File(s"$RegistryPath/releases").isDirectory)
    }
  }

  test("getMavenRepository returns None for an unknown registry") {
    withTestDB { implicit session =>
      assert(getMavenRepository("does-not-exist").isEmpty)
    }
  }

  test("getMavenRepositories returns all registries sorted by name") {
    withTestDB { implicit session =>
      createRegistry("snapshots", None, overwrite = true, isPrivate = false)
      createRegistry("releases", None, overwrite = false, isPrivate = false)

      assert(getMavenRepositories().map(_.name) == Seq("releases", "snapshots"))
    }
  }

  test("updateRegistry updates description, overwrite and isPrivate") {
    withTestDB { implicit session =>
      createRegistry("releases", Some("Releases"), overwrite = false, isPrivate = false)

      updateRegistry("releases", Some("Updated"), overwrite = true, isPrivate = true)

      val registry = getMavenRepository("releases").get
      assert(registry.description.contains("Updated"))
      assert(registry.overwrite)
      assert(registry.isPrivate)
    }
  }

  test("deleteRegistry removes the registry and its directory") {
    withTestDB { implicit session =>
      createRegistry("releases", None, overwrite = false, isPrivate = false)
      assert(new File(s"$RegistryPath/releases").isDirectory)

      deleteRegistry("releases")

      assert(getMavenRepository("releases").isEmpty)
      assert(!new File(s"$RegistryPath/releases").exists)
    }
  }
}
