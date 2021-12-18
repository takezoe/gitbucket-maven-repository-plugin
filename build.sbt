name := "gitbucket-maven-repository-plugin"
organization := "io.github.gitbucket"
version := "1.8.0-SNAPSHOT"
scalaVersion := "2.13.7"
gitbucketVersion := "4.37.1"
scalacOptions += "-deprecation"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.apache.sshd" % "sshd-scp" % "2.8.0"
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) =>
    (xs map { _.toLowerCase }) match {
      case ("manifest.mf" :: Nil) => MergeStrategy.discard
      case _                      => MergeStrategy.discard
    }
  case x => MergeStrategy.first
}