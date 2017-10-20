package io.github.gitbucket.registry.command

import java.io.File

class LsCommand(dir: File) extends AbstractCommand {
  override protected def execute(): Unit = {
    val result = dir.listFiles.map(_.getName).mkString("\t")
    out.write(result.getBytes("UTF-8"))
  }
}

