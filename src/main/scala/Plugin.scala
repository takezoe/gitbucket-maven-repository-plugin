import java.io.{File, IOException, OutputStream}
import java.nio.file.{Files, OpenOption, Path}
import java.util

import gitbucket.core.controller.Context
import gitbucket.core.plugin.Link
import gitbucket.core.servlet.Database
import gitbucket.core.model.Profile.profile.blockingApi._
import io.github.gitbucket.mavenrepository._
import io.github.gitbucket.mavenrepository.command.{LsCommand, MkdirCommand}
import io.github.gitbucket.mavenrepository.controller.MavenRepositoryController
import io.github.gitbucket.mavenrepository.service.MavenRepositoryService
import io.github.gitbucket.solidbase.migration.{LiquibaseMigration, Migration}
import io.github.gitbucket.solidbase.model.Version
import org.apache.sshd.common.scp.helpers.DefaultScpFileOpener
import org.apache.sshd.common.session.Session
import org.apache.sshd.server.scp.ScpCommand

class Plugin extends gitbucket.core.plugin.Plugin with MavenRepositoryService {
  override val pluginId: String = "maven-repository"
  override val pluginName: String = "Maven Repository Plugin"
  override val description: String = "Host Maven repository on GitBucket."
  override val versions: List[Version] = List(
    new Version("1.0.0"),
    new Version("1.0.1"),
    new Version("1.1.0",
      new LiquibaseMigration("update/gitbucket-maven-repository_1.1.0.xml"),
      (moduleId: String, version: String, context: util.Map[String, AnyRef]) => {
        new File(s"${RegistryPath}/releases").mkdirs()
        new File(s"${RegistryPath}/snapshots").mkdirs()
      }
    ),
    new Version("1.2.0"),
    new Version("1.2.1"),
    new Version("1.3.0")
  )

  override val sshCommandProviders = Seq({
    case command: String if checkCommand(command) => {
      val index        = command.indexOf('/')
      val path         = command.substring(index + "/maven".length)
      val registryName = path.split("/")(1)
      val registry     = Database() withTransaction { implicit session => getMavenRepository(registryName).get }
      val fullPath     = s"${RegistryPath}/${path}"

      if(command.startsWith("scp")){
        new ScpCommand(s"scp -t -d ${fullPath}", null, 1024 * 128, 1024 * 128, new DefaultScpFileOpener(){
          override def openWrite(session: Session, file: Path, options: OpenOption*): OutputStream = {
            val fileName = file.getFileName.toString
            if(fileName == "maven-metadata.xml" || fileName.startsWith("maven-metadata.xml.")){
              // accept
            } else if(registry.overwrite == false && Files.exists(file)){
              throw new IOException("Rejected.")
            }
            super.openWrite(session, file, options: _*)
          }
        }, null)
      } else if(command.startsWith("mkdir")){
        new MkdirCommand(new File(fullPath))
      } else {
        new LsCommand(new File(fullPath))
      }
    }
  })

  /**
   * Check the existence of the library repository.
   */
  private def checkCommand(command: String): Boolean = {
    Database() withTransaction { implicit session =>
      getMavenRepositories().exists { registry =>
        command.matches(s"scp .* /maven/${registry.name}/.*") ||
        command.startsWith(s"ls /maven/${registry.name}") ||
        command.startsWith(s"mkdir /maven/${registry.name}")
      }
    }
  }

  private val controller = new MavenRepositoryController()

  override val controllers = Seq(
    "/maven/*"       -> controller,
    "/admin/maven/*" -> controller
  )

  override val systemSettingMenus: Seq[Context => Option[Link]] = Seq(
    _ => Some(Link("maven", "Maven repositories", "admin/maven", Some("package")))
  )

}
