name := "gitbucket-maven-repository-plugin"
organization := "io.github.gitbucket"
version := "1.10.0"
scalaVersion := "2.13.18"
gitbucketVersion := "4.48.0"
scalacOptions += "-deprecation"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.apache.sshd" % "sshd-scp" % "2.19.0",
  "org.scalatest" %% "scalatest-funsuite"       % "3.2.20" % "test",
  "org.scalatra"  %% "scalatra-scalatest-javax" % "3.2.0"  % "test",
  "org.mockito"   % "mockito-core"              % "5.23.0" % "test"
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) =>
    (xs map { _.toLowerCase }) match {
      case ("manifest.mf" :: Nil) => MergeStrategy.discard
      case _                      => MergeStrategy.discard
    }
  case x => MergeStrategy.first
}