package io.github.gitbucket.mavenrepository.command

import java.io.File

class MkdirCommand(dir: File) extends AbstractCommand {
  override def execute(): Int = {
    dir.mkdirs()
    Success
  }
}
