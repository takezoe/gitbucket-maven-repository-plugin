package io.github.gitbucket.mavenrepository

import gitbucket.core.plugin.Plugin
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection

import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID
import scala.util.Using

/**
 * In-memory H2 databases with the plugin's tables created by its own Solidbase migrations, as GitBucket does
 * when it installs the plugin (so they also contain the default `releases` and `snapshots` repositories).
 */
object TestDatabase {

  // Redirect RegistryPath away from the real ~/.gitbucket. Runs before anything reads it.
  sys.props("gitbucket.home") = Files.createTempDirectory("mvn-repo-plugin-test").toString

  // Plugin.scala is in the default package, which Scala code in a package can't import.
  private lazy val plugin =
    Class.forName("Plugin").getDeclaredConstructor().newInstance().asInstanceOf[Plugin]

  def create(): Database = {
    val url = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    Using.resource(DriverManager.getConnection(url, "sa", "sa")) { conn =>
      val db = new H2Database()
      db.setConnection(new JdbcConnection(conn))
      new Solidbase().migrate(conn, Thread.currentThread.getContextClassLoader, db,
        new Module(plugin.pluginId, plugin.versions: _*))
      // Liquibase turns auto-commit off; without this the inserted default repositories are rolled back on close.
      if (!conn.getAutoCommit) conn.commit()
    }
    Database.forURL(url, "sa", "sa")
  }

  def withTestDB[A](action: Session => A): A = create().withSession(action)
}
