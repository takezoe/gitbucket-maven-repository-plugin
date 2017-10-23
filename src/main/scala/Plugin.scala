import java.io.{File, IOException, OutputStream}
import java.nio.file.{Files, OpenOption, Path}

import gitbucket.core.controller.Context
import gitbucket.core.plugin.Link
import io.github.gitbucket.registry._
import io.github.gitbucket.registry.command.{LsCommand, MkdirCommand}
import io.github.gitbucket.registry.controller.{RegistryAdminController, RegistryController}
import io.github.gitbucket.solidbase.model.Version
import org.apache.sshd.common.scp.helpers.DefaultScpFileOpener
import org.apache.sshd.common.session.Session
import org.apache.sshd.server.scp.ScpCommand

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "registry"
  override val pluginName: String = "Library Registry Plugin"
  override val description: String = "Provides library management and registries on GitBucket."
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val sshCommandProviders = Seq({
    case command: String if checkCommand(command) => {
      val index = command.indexOf('/')
      val path = command.substring(index + 1)

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
    println(command)
    Registries.exists { registry =>
      command.startsWith(s"scp -t -d /repo/${registry.name}") ||
      command.startsWith(s"ls /repo/${registry.name}") ||
      command.startsWith(s"mkdir /repo/${registry.name}")
    }
  }

  override val controllers = Seq(
    "/repo/*"       -> new RegistryController(),
    "/admin/repo/*" -> new RegistryAdminController()
  )

  override val systemSettingMenus = Seq(
    (ctx: Context) => Some(Link("registries", "Registries", "admin/repo", Some("package")))
  )

}
