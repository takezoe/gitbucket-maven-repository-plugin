package io.github.gitbucket.registry.controller

import io.github.gitbucket.registry._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.AdminAuthenticator

class RegistryAdminController extends ControllerBase with AdminAuthenticator {

  get("/admin/repo")(adminOnly {
    gitbucket.registry.html.settings(Registries, None)
  })

}
