package io.github.gitbucket.mavenrepository.command

import java.io.{InputStream, OutputStream}

import org.apache.sshd.server.command.Command
import org.apache.sshd.server.{Environment, ExitCallback}

abstract class AbstractCommand extends Command {

  protected val Success = 0
  protected val Failure = -1

  protected var in: InputStream = null
  protected var out: OutputStream = null
  protected var err: OutputStream = null
  protected var callback: ExitCallback = null
  override def setErrorStream(err: OutputStream): Unit = this.err = err
  override def setOutputStream(out: OutputStream): Unit = this.out = out
  override def setInputStream(in: InputStream): Unit = this.in = in
  override def setExitCallback(callback: ExitCallback): Unit = this.callback = callback
  override def start(env: Environment): Unit = {
    val exitCode = execute()
    out.flush()

    in.close()
    out.close()
    err.close()

    callback.onExit(exitCode)
  }
  override def destroy(): Unit = {}
  protected def execute(): Int
}
