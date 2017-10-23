gitbucket-registry-plugin
========
A GitBucket plugin that provides Maven repository hosting on GitBucket.

### Features

Following Maven repositories become available by installing this plugin to GitBucket.

- `http(s)://GITBUCKET_HOST:GITBUCKET_PORT/repo/releases`
- `http(s)://GITBUCKET_HOST:GITBUCKET_PORT/repo/snapshots`

You can deploy artifacts to these repositories via WebDAV with your GitBucket account.

You can also deploy via SSH (SCP) with public key authentication using keys registered in GitBucket. In this case, use following configurations to connect via SSH:

- Host: Hostname of GitBucket
- Port: SSH port configured in GitBucket system settings
- Path: `/repo/releases` or `/repo/snapshots`

### Installation

Run `sbt assembly` and copy generated `/target/scala-2.12/gitbucket-registry-plugin-assembly-1.0.0.jar` to `~/.gitbucket/plugins/` (If the directory does not exist, create it by hand before copying the jar).
