package io.github.gitbucket.mavenrepository.command

import java.io.File

class LsCommand(dir: File) extends AbstractCommand {
  override protected def execute(): Int = {
    if(dir.exists && dir.isDirectory){
      val result = dir.listFiles.map(_.getName).mkString("\t")
      out.write(result.getBytes("UTF-8"))
      Success
    } else {
      Failure
    }
  }
}

