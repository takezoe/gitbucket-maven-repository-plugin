import java.io.{File, IOException, OutputStream}
import java.nio.file.{Files, OpenOption, Path}
import java.nio.file.attribute.PosixFilePermission

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
import org.apache.sshd.scp.common.helpers.DefaultScpFileOpener
import org.apache.sshd.scp.server.ScpCommand
import org.apache.sshd.common.session.Session

class Plugin extends gitbucket.core.plugin.Plugin with MavenRepositoryService {
  override val pluginId: String = "maven-repository"
  override val pluginName: String = "Maven Repository Plugin"
  override val description: String = "Host Maven repository on GitBucket."
  override val versions: List[Version] = List(
    new Version("1.0.0"),
    new Version("1.0.1"),
    new Version("1.1.0",
      new LiquibaseMigration("update/gitbucket-maven-repository_1.1.0.xml"),
      (moduleId: String, version: String, context: java.util.Map[String, AnyRef]) => {
        new File(s"${RegistryPath}/releases").mkdirs()
        new File(s"${RegistryPath}/snapshots").mkdirs()
      }
    ),
    new Version("1.2.0"),
    new Version("1.2.1"),
    new Version("1.3.0"),
    new Version("1.3.1"),
    new Version("1.3.2"),
    new Version("1.4.0"),
    new Version("1.5.0"),
    new Version("1.6.0"),
    new Version("1.7.0")
  )

  override val sshCommandProviders = Seq({
    case command: String if checkCommand(command) => {
      val index        = command.indexOf('/')
      val path         = command.substring(index + "/maven".length)
      val registryName = path.split("/")(1)
      val registry     = Database() withTransaction { implicit session => getMavenRepository(registryName).get }
      val fullPath     = s"${RegistryPath}/${path}"

      if(command.startsWith("scp")){
        new ScpCommand(
          null, // TODO Fix this parameter to take from GitBucket core: https://github.com/gitbucket/gitbucket/pull/2941
          s"scp -t -d ${fullPath}", 
          null,       // executorService
          1024 * 128, // sendSize
          1024 * 128, // receiveSize
          new DefaultScpFileOpener(){
            override def openWrite(session: Session, file: Path, size: Long, permissions: java.util.Set[PosixFilePermission], options: OpenOption*): OutputStream = {
              val fileName = file.getFileName.toString
              if(fileName == "maven-metadata.xml" || fileName.startsWith("maven-metadata.xml.")){
                // accept
              } else if(registry.overwrite == false && Files.exists(file)){
                throw new IOException("Rejected.")
              }
              super.openWrite(session, file, size, permissions, options: _*)
            }
          },
          null // eventListener
        )
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

  override val anonymousAccessiblePaths = Seq("/maven")

  override val systemSettingMenus: Seq[Context => Option[Link]] = Seq(
    _ => Some(Link("maven", "Maven repositories", "admin/maven", Some("package")))
  )

}
