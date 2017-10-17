import java.io.File

import gitbucket.core.util.Directory
import io.github.gitbucket.solidbase.model.Version
import org.apache.sshd.server.scp.ScpCommand

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "librepo"
  override val pluginName: String = "Library Repository Plugin"
  override val description: String = "Provides library management and repositories on GitBucket."
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val sshCommandProviders = Seq({
    case command: String if command.startsWith("scp -t -d /repo") => {
      println(command)
      val index = command.indexOf('/')
      val path = command.substring(index)
      val fullPath = s"${Directory.GitBucketHome}/librepo/${path}"
      println(fullPath)
      val dir = new File(fullPath)
      if(!dir.exists){
        dir.mkdirs()
      }
      new ScpCommand(s"scp -t ${fullPath}", null, true, 1024 * 128, 1024 * 128, null, null)
    }
  })

}
