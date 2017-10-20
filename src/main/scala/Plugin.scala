import java.io.File

import gitbucket.core.util.Directory
import io.github.gitbucket.registry._
import io.github.gitbucket.registry.controller.RegistryController
import io.github.gitbucket.solidbase.model.Version
import org.apache.sshd.server.scp.ScpCommand

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "registry"
  override val pluginName: String = "Library Registry Plugin"
  override val description: String = "Provides library management and registries on GitBucket."
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val sshCommandProviders = Seq({
    case command: String if checkCommand(command) => {
      println(command)
      val index = command.indexOf('/')
      val path = command.substring(index)
      val fullPath = s"${Directory.GitBucketHome}/registries/${path}"
      println(fullPath)
      val dir = new File(fullPath)
      if(!dir.exists){
        dir.mkdirs()
      }
      new ScpCommand(s"scp -t ${fullPath}", null, true, 1024 * 128, 1024 * 128, null, null)
    }
  })

  /**
   * Check the existence of the library repository.
   */
  private def checkCommand(command: String): Boolean = {
    Registries.exists { repositoryName =>
      command.startsWith(s"scp -t -d /${repositoryName}")
    }
  }

  override val controllers = Seq("/repo/*" -> new RegistryController())

}
