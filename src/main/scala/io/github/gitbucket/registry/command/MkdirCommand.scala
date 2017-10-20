package io.github.gitbucket.registry.command

import java.io.File

class MkdirCommand(dir: File) extends AbstractCommand {
  override def execute(): Unit = {
    dir.mkdirs()
  }
}
