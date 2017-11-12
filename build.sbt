import java.io.File

name := "gitbucket-maven-repository-plugin"

organization := "io.github.gitbucket"

version := "1.0.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "io.github.gitbucket"  %% "gitbucket"         % "4.19.0-SNAPSHOT" % "provided",
  "javax.servlet"         % "javax.servlet-api" % "3.1.0"           % "provided"
)

scalacOptions += "-deprecation"

resolvers += Resolver.bintrayRepo("bkromhout", "maven")
resolvers += Resolver.mavenLocal

val installKey = TaskKey[Unit]("install")
installKey := {
  val file = (Keys.`package` in Compile).value

  val GitBucketHome = (System.getProperty("gitbucket.home") match {
    // -Dgitbucket.home=<path>
    case path if(path != null) => new File(path)
    case _ => scala.util.Properties.envOrNone("GITBUCKET_HOME") match {
      // environment variable GITBUCKET_HOME
      case Some(env) => new File(env)
      // default is HOME/.gitbucket
      case None => {
        val oldHome = new File(System.getProperty("user.home"), "gitbucket")
        if(oldHome.exists && oldHome.isDirectory && new File(oldHome, "version").exists){
          //FileUtils.moveDirectory(oldHome, newHome)
          oldHome
        } else {
          new File(System.getProperty("user.home"), ".gitbucket")
        }
      }
    }
  })

  val PluginDir = new File(GitBucketHome, "plugins")
  if(!PluginDir.exists){
    PluginDir.mkdirs()
  }

  val log = streams.value.log
  log.info(s"Copying ${file.getAbsolutePath} to ${PluginDir.getAbsolutePath}")

  IO.copyFile(file, new File(PluginDir, file.getName))
}