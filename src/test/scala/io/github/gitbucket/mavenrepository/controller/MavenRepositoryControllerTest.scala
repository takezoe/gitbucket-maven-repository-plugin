package io.github.gitbucket.mavenrepository.controller

import gitbucket.core.controller.Context
import gitbucket.core.model.Account
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.service.SystemSettingsService.{BasicBehavior, SystemSettings}
import gitbucket.core.util.Keys
import io.github.gitbucket.mavenrepository.{RegistryPath, TestDatabase}
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import io.github.gitbucket.mavenrepository.service.MavenRepositoryService
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.mockito.Mockito._
import org.slf4j.LoggerFactory
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, equal}
import org.scalatra.test.scalatest.ScalatraFunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.{Base64, Date}
import javax.servlet.{Filter, FilterChain, ServletRequest, ServletResponse}
import scala.jdk.CollectionConverters._

// Covers only what resolves before any registry/DB lookup: the admin-only gate and the
// path-traversal guard. Behavior that needs a registry lookup is in MavenRepositoryControllerWithDatabaseTests.
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

// Runs against a database created by the plugin's migrations, so the default `releases` and `snapshots`
// repositories exist and are public. Anonymous access is disabled for the instance.
class MavenRepositoryControllerWithDatabaseTests extends ScalatraFunSuite with MavenRepositoryService {
  private implicit val session: Session = TestDatabase.create().createSession()
  private var loginAccount: Option[Account] = None

  // Stands in for GitBucket's TransactionFilter, which provides the controller's DB session.
  addFilter(new Filter {
    override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
      request.setAttribute(Keys.Request.DBSession, session)
      chain.doFilter(request, response)
    }
  }, "/*")

  addFilter(new MavenRepositoryController() {
    override implicit def context: Context =
      MavenRepositoryControllerTest.buildContext(loginAccount, allowAnonymousAccess = false)

    override def authenticate(settings: SystemSettings, userName: String, password: String)
                             (implicit s: Session): Option[Account] =
      if (userName == "test" && password == "secret") Some(MavenRepositoryControllerTest.buildAccount(isAdmin = false))
      else None
  }, "/*")

  override def afterAll(): Unit = {
    super.afterAll()
    session.close()
  }

  private def basicAuth(user: String, password: String): Map[String, String] =
    Map("Authorization" -> ("Basic " + Base64.getEncoder.encodeToString(s"$user:$password".getBytes(StandardCharsets.UTF_8))))

  private def asAdmin[A](action: => A): A = {
    loginAccount = Some(MavenRepositoryControllerTest.buildAccount(isAdmin = true))
    try action finally loginAccount = None
  }

  private def artifact(registry: String, path: String) = Paths.get(RegistryPath, registry, path)

  // Messages the controller logs (including DEBUG) while running action.
  private def logged(action: => Any): Seq[String] = {
    val logger = LoggerFactory.getLogger(classOf[MavenRepositoryController]).asInstanceOf[Logger]
    val appender = new ListAppender[ILoggingEvent]
    val level = logger.getLevel
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.DEBUG)
    try {
      action
      appender.list.asScala.map(_.getFormattedMessage).toSeq
    } finally {
      logger.detachAppender(appender)
      logger.setLevel(level)
    }
  }

  test("PUT to a public repository without credentials is unauthorized and writes nothing") {
    put("/maven/snapshots/anon/probe.txt", "x".getBytes) {
      status should equal(401)
      header("WWW-Authenticate") should equal("Basic realm=\"GitBucket Maven Repository\"")
    }
    Files.exists(artifact("snapshots", "anon/probe.txt")) should equal(false)
  }

  test("PUT to a public repository with wrong credentials is unauthorized") {
    put("/maven/snapshots/anon/probe.txt", "x".getBytes, basicAuth("test", "wrong")) {
      status should equal(401)
    }
    Files.exists(artifact("snapshots", "anon/probe.txt")) should equal(false)
  }

  test("PUT to a public repository with credentials stores the file") {
    put("/maven/snapshots/auth/probe.txt", "x".getBytes, basicAuth("test", "secret")) {
      status should equal(200)
    }
    Files.readString(artifact("snapshots", "auth/probe.txt")) should equal("x")
  }

  test("GET from a public repository works without credentials") {
    Files.createDirectories(artifact("releases", "pub"))
    Files.writeString(artifact("releases", "pub/a.txt"), "public")
    get("/maven/releases/pub/a.txt") {
      status should equal(200)
      body should equal("public")
    }
  }

  test("GET from a private repository needs credentials") {
    createRegistry("internal", None, overwrite = false, isPrivate = true)
    Files.writeString(artifact("internal", "a.txt"), "private")
    get("/maven/internal/a.txt") {
      status should equal(401)
    }
    get("/maven/internal/a.txt", headers = basicAuth("test", "secret")) {
      status should equal(200)
      body should equal("private")
    }
  }

  test("DELETE of a single file removes it and its then-empty directory") {
    Files.createDirectories(artifact("snapshots", "del/one"))
    Files.writeString(artifact("snapshots", "del/one/a.jar"), "a")
    delete("/maven/snapshots/del/one/a.jar", headers = basicAuth("test", "secret")) {
      status should equal(200)
    }
    Files.exists(artifact("snapshots", "del/one/a.jar")) should equal(false)
    Files.exists(artifact("snapshots", "del/one")) should equal(false)
  }

  test("DELETE of a directory removes it with its content") {
    Files.createDirectories(artifact("snapshots", "del/dir"))
    Files.writeString(artifact("snapshots", "del/dir/a.jar"), "a")
    delete("/maven/snapshots/del/dir", headers = basicAuth("test", "secret")) {
      status should equal(200)
    }
    Files.exists(artifact("snapshots", "del/dir")) should equal(false)
  }

  test("DELETE without credentials is unauthorized") {
    Files.createDirectories(artifact("snapshots", "del/anon"))
    Files.writeString(artifact("snapshots", "del/anon/a.jar"), "a")
    delete("/maven/snapshots/del/anon/a.jar") {
      status should equal(401)
    }
    Files.exists(artifact("snapshots", "del/anon/a.jar")) should equal(true)
  }

  test("GET from a private repository works with the GitBucket session, PUT and DELETE don't") {
    createRegistry("session", None, overwrite = true, isPrivate = true)
    Files.writeString(artifact("session", "a.txt"), "private")
    loginAccount = Some(MavenRepositoryControllerTest.buildAccount(isAdmin = false))
    try {
      get("/maven/session/a.txt") {
        status should equal(200)
        body should equal("private")
      }
      put("/maven/session/b.txt", "x".getBytes) {
        status should equal(401)
      }
      delete("/maven/session/a.txt") {
        status should equal(401)
      }
    } finally loginAccount = None
    Files.exists(artifact("session", "b.txt")) should equal(false)
    Files.exists(artifact("session", "a.txt")) should equal(true)
  }

  test("editing a repository logs the old and new settings") {
    createRegistry("log-edit", Some("x"), overwrite = false, isPrivate = true)
    val messages = logged {
      asAdmin {
        post("/admin/maven/log-edit/_edit", "description" -> "y", "overwrite" -> "true", "isPrivate" -> "false",
          "confirmPublic" -> "true") {
          status should equal(302)
        }
      }
    }
    messages should equal(Seq("Maven repository 'log-edit' changed by test " +
      "(private -> public, overwrite false -> true, description changed): public although anonymous access is disabled"))
  }

  test("deleting files in the file browser is logged") {
    Files.createDirectories(artifact("snapshots", "log/files"))
    Files.writeString(artifact("snapshots", "log/files/a.jar"), "a")
    Files.writeString(artifact("snapshots", "log/files/b.jar"), "b")
    val messages = logged {
      asAdmin {
        post("/admin/maven/snapshots/_deletefiles", "path" -> "log/files", "files" -> "a.jar", "files" -> "b.jar") {
          status should equal(302)
        }
      }
    }
    messages should equal(Seq("Maven repository 'snapshots': a.jar, b.jar in /log/files deleted by test"))
    Files.exists(artifact("snapshots", "log/files/a.jar")) should equal(false)
  }

  test("uploads and deletes by clients are logged with the user") {
    val messages = logged {
      put("/maven/snapshots/log/c.jar", "c".getBytes, basicAuth("test", "secret")) {
        status should equal(200)
      }
      delete("/maven/snapshots/log/c.jar", headers = basicAuth("test", "secret")) {
        status should equal(200)
      }
    }
    messages should equal(Seq(
      "Maven repository 'snapshots': /log/c.jar uploaded by test",
      "Maven repository 'snapshots': /log/c.jar deleted by test"
    ))
  }

  test("making a private repository public must be confirmed while anonymous access is disabled") {
    createRegistry("confirm-edit", None, overwrite = false, isPrivate = true)
    asAdmin {
      post("/admin/maven/confirm-edit/_edit/validate", "description" -> "x") {
        body should include("confirmPublic")
      }
      post("/admin/maven/confirm-edit/_edit", "description" -> "x", "isPrivate" -> "false", "confirmPublic" -> "false") {
        status should not equal(302)
      }
      getMavenRepository("confirm-edit").map(_.isPrivate) should equal(Some(true))

      post("/admin/maven/confirm-edit/_edit", "description" -> "x", "confirmPublic" -> "true") {
        status should equal(302)
      }
      getMavenRepository("confirm-edit").map(_.isPrivate) should equal(Some(false))
    }
  }

  test("creating a public repository must be confirmed while anonymous access is disabled") {
    asAdmin {
      post("/admin/maven/_new", "name" -> "confirm-new") {
        status should not equal(302)
      }
      getMavenRepository("confirm-new") should equal(None)

      post("/admin/maven/_new", "name" -> "confirm-new", "isPrivate" -> "true") {
        status should equal(302)
      }
      getMavenRepository("confirm-new").map(_.isPrivate) should equal(Some(true))
    }
  }

  test("editing a repository that is already public needs no confirmation") {
    asAdmin {
      post("/admin/maven/releases/_edit", "description" -> "Releases") {
        status should equal(302)
      }
      getMavenRepository("releases").flatMap(_.description) should equal(Some("Releases"))
    }
  }
}

object MavenRepositoryControllerTest {
  def buildContext(loginAccount: Option[Account], allowAnonymousAccess: Boolean = true): Context = {
    val basicBehavior = mock(classOf[BasicBehavior])
    when(basicBehavior.allowAnonymousAccess).thenReturn(allowAnonymousAccess)
    val systemSettings = mock(classOf[SystemSettingsService.SystemSettings])
    when(systemSettings.basicBehavior).thenReturn(basicBehavior)

    val context = mock(classOf[Context])
    when(context.baseUrl).thenReturn("http://localhost:8080")
    when(context.path).thenReturn("")
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
