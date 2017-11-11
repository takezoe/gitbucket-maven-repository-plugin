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
