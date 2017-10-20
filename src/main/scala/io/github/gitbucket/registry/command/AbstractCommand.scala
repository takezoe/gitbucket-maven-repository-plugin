package io.github.gitbucket.registry.command

import java.io.{InputStream, OutputStream}

import org.apache.sshd.server.{Command, Environment, ExitCallback}

abstract class AbstractCommand extends Command {
  protected var in: InputStream = null
  protected var out: OutputStream = null
  protected var err: OutputStream = null
  protected var callback: ExitCallback = null
  override def setErrorStream(err: OutputStream): Unit = this.err = err
  override def setOutputStream(out: OutputStream): Unit = this.out = out
  override def setInputStream(in: InputStream): Unit = this.in = in
  override def setExitCallback(callback: ExitCallback): Unit = this.callback = callback
  override def start(env: Environment): Unit = {
    execute()
    out.flush()

    in.close()
    out.close()
    err.close()

    callback.onExit(0)
  }
  override def destroy(): Unit = {}
  protected def execute(): Unit
}
