import java.io.{File, IOException, OutputStream}
import java.nio.file.{Files, OpenOption, Path}

import io.github.gitbucket.mavenrepository._
import io.github.gitbucket.mavenrepository.command.{LsCommand, MkdirCommand}
import io.github.gitbucket.mavenrepository.controller.MavenRepositoryController
import io.github.gitbucket.solidbase.model.Version
import org.apache.sshd.common.scp.helpers.DefaultScpFileOpener
import org.apache.sshd.common.session.Session
import org.apache.sshd.server.scp.ScpCommand

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "maven-repository"
  override val pluginName: String = "Maven Repository Plugin"
  override val description: String = "Host Maven repository on GitBucket."
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val sshCommandProviders = Seq({
    case command: String if checkCommand(command) => {
      val index = command.indexOf('/')
      val path = command.substring(index + "/maven".length)

      val registryName = path.split("/")(1)
      val registry = Registries.find(_.name == registryName).get

      val registryPath = s"${RegistryPath}/${registry.name}"
      val registryDir = new File(registryPath)
      if(!registryDir.exists){
        registryDir.mkdirs()
      }

      val fullPath = s"${RegistryPath}/${path}"

      if(command.startsWith("scp")){
        new ScpCommand(s"scp -t -d ${fullPath}", null, true, 1024 * 128, 1024 * 128, new DefaultScpFileOpener(){
          override def openWrite(session: Session, file: Path, options: OpenOption*): OutputStream = {
            if(registry.overwrite == false && Files.exists(file)){
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
    Registries.exists { registry =>
      command.matches(s"scp .* /maven/${registry.name}/.*") ||
      command.startsWith(s"ls /maven/${registry.name}") ||
      command.startsWith(s"mkdir /maven/${registry.name}")
    }
  }

  override val controllers = Seq(
    "/maven/*" -> new MavenRepositoryController()
  )

}
