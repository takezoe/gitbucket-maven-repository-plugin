gitbucket-maven-repository-plugin
========
A GitBucket plugin that provides Maven repository hosting on GitBucket.

## Features

Following Maven repositories become available by installing this plugin to GitBucket.

- `http(s)://GITBUCKET_HOST/maven/releases`
- `http(s)://GITBUCKET_HOST/maven/snapshots`

You can deploy artifacts to these repositories via WebDAV with your GitBucket account.

You can also deploy via SSH (SCP) with public key authentication using keys registered in GitBucket. In this case, use following configurations to connect via SSH:

- Host: Hostname of GitBucket
- Port: SSH port configured in GitBucket system settings
- Path: `/maven/releases` or `/maven/snapshots`

## Installation

Run `sbt package` and copy generated `/target/scala-2.12/gitbucket-maven-repository-plugin_2.12-1.0.0.jar` to `~/.gitbucket/plugins/` (If the directory does not exist, create it by hand before copying the jar).

## Configuration

### sbt

Resolvers:

```scala
resolvers ++= Seq(
 "GitBucket Snapshots Repository" at "http://localhost:8080/maven/snapshots",
 "GitBucket Releases Repository"  at "http://localhost:8080/maven/releases"
)
```

Publish via WebDAV:

```scala
publishTo := {
  val base = "http://localhost:8080/maven/"
  if (version.value.endsWith("SNAPSHOT")) Some("snapshots" at base + "snapshots")
  else                                    Some("releases"  at base + "releases")
}

credentials += Credentials("GitBucket Maven Repository", "localhost", "username", "password")
```

Publish via SSH:

```scala
publishTo := {
  val repoInfo =
    if (version.value.endsWith("SNAPSHOT")) ("snapshots" -> "/maven/snapshots")
    else                                    ("releases"  -> "/maven/releases")
  Some(Resolver.ssh(repoInfo._1, "localhost", 29418, repoInfo._2) 
    as(System.getProperty("user.name"), (Path.userHome / ".ssh" / "id_rsa").asFile))
}
```

### Maven

TBC