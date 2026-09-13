package io.github.gitbucket.mavenrepository.controller

import gitbucket.core.controller.Context
import gitbucket.core.model.Account
import gitbucket.core.service.SystemSettingsService
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, equal}
import org.scalatra.test.scalatest.ScalatraFunSuite

import java.util.Date

// Covers only what resolves before any registry/DB lookup: the admin-only gate and the
// path-traversal guard. Behavior that needs a real registry lookup (private-repo auth,
// overwrite protection, WebDAV round trips) is covered by the live smoke test instead.
class MavenRepositoryControllerWithoutLoginTests extends ScalatraFunSuite {
  addFilter(new MavenRepositoryController() {
    override implicit val context: Context = MavenRepositoryControllerTest.buildContext(None)
  }, "/*")

  test("GET /admin/maven without login is unauthorized") {
    get("/admin/maven") {
      status should equal(401)
    }
  }

  test("PUT with a path-traversal segment is rejected before any registry lookup") {
    put("/maven/releases/../../../etc/passwd", "x".getBytes) {
      status should equal(400)
    }
  }

  test("GET with a path-traversal segment is rejected before any registry lookup") {
    get("/maven/releases/../../../etc/passwd") {
      status should equal(400)
    }
  }

  test("DELETE with a path-traversal segment is rejected before any registry lookup") {
    delete("/maven/releases/../../../etc/passwd") {
      status should equal(400)
    }
  }

  test("POST /admin/maven/:name/_delete without login is unauthorized") {
    post("/admin/maven/releases/_delete") {
      status should equal(401)
    }
  }

  test("POST /admin/maven/:name/_deletefiles without login is unauthorized") {
    post("/admin/maven/releases/_deletefiles") {
      status should equal(401)
    }
  }
}

class MavenRepositoryControllerWithNonAdminTests extends ScalatraFunSuite {
  addFilter(new MavenRepositoryController() {
    override implicit val context: Context =
      MavenRepositoryControllerTest.buildContext(Some(MavenRepositoryControllerTest.buildAccount(isAdmin = false)))
  }, "/*")

  test("GET /admin/maven as a non-admin is unauthorized") {
    get("/admin/maven") {
      status should equal(401)
    }
  }

  test("POST /admin/maven/:name/_delete as a non-admin is unauthorized") {
    post("/admin/maven/releases/_delete") {
      status should equal(401)
    }
  }

  test("POST /admin/maven/:name/_deletefiles as a non-admin is unauthorized") {
    post("/admin/maven/releases/_deletefiles") {
      status should equal(401)
    }
  }
}

object MavenRepositoryControllerTest {
  private val systemSettings = mock(classOf[SystemSettingsService.SystemSettings])

  def buildContext(loginAccount: Option[Account]): Context = {
    val context = mock(classOf[Context])
    when(context.baseUrl).thenReturn("http://localhost:8080")
    when(context.loginAccount).thenReturn(loginAccount)
    when(context.settings).thenReturn(systemSettings)
    context
  }

  def buildAccount(isAdmin: Boolean): Account = Account(
    userName = "test",
    fullName = "Test User",
    mailAddress = "test@example.com",
    password = "password",
    isAdmin = isAdmin,
    url = None,
    registeredDate = new Date(),
    updatedDate = new Date(),
    lastLoginDate = None,
    image = None,
    isGroupAccount = false,
    isRemoved = false,
    description = None
  )
}
