package io.github.gitbucket.mavenrepository.controller

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}

import io.github.gitbucket.mavenrepository._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Account
import gitbucket.core.service.AccountService
import gitbucket.core.util.{AdminAuthenticator, AuthUtil, FileUtil}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.mavenrepository.model.{MavenToken, Registry}
import io.github.gitbucket.mavenrepository.service.{MavenRepositoryService, MavenTokenService}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.slf4j.LoggerFactory
import org.scalatra.forms._
import org.scalatra.i18n.Messages
import org.scalatra.{ActionResult, Forbidden, NotAcceptable, Ok}
import scala.util.Using
import org.scalatra.BadRequest

class MavenRepositoryController extends ControllerBase with AccountService with MavenRepositoryService
  with MavenTokenService with AdminAuthenticator {

  private val logger = LoggerFactory.getLogger(classOf[MavenRepositoryController])

  case class RepositoryCreateForm(name: String, description: Option[String], overwrite: Boolean, isPrivate: Boolean,
                                  confirmPublic: Boolean)
  case class RepositoryEditForm(description: Option[String], overwrite: Boolean, isPrivate: Boolean,
                                confirmPublic: Boolean)

  val repositoryCreateForm = mapping(
    "name"          -> trim(label("Name", text(required, identifier, maxlength(100), unique))),
    "description"   -> trim(label("Description", optional(text()))),
    "overwrite"     -> trim(boolean()),
    "isPrivate"     -> trim(boolean()),
    "confirmPublic" -> trim(boolean())
  )(RepositoryCreateForm.apply).verifying { form =>
    confirmPublicRequired(wasPrivate = true, form.isPrivate, form.confirmPublic)
  }

  val repositoryEditForm = mapping(
    "description"   -> trim(label("Description", optional(text()))),
    "overwrite"     -> trim(boolean()),
    "isPrivate"     -> trim(boolean()),
    "confirmPublic" -> trim(boolean())
  )(RepositoryEditForm.apply).verifying { form =>
    val wasPrivate = getMavenRepository(params("name")).forall(_.isPrivate)
    confirmPublicRequired(wasPrivate, form.isPrivate, form.confirmPublic)
  }

  // With anonymous access disabled, making a repository public is an exception that must be confirmed.
  private def confirmPublicRequired(wasPrivate: Boolean, isPrivate: Boolean, confirmPublic: Boolean): Seq[(String, String)] =
    if (!context.settings.basicBehavior.allowAnonymousAccess && wasPrivate && !isPrivate && !confirmPublic) {
      Seq("confirmPublic" -> "Confirm that this repository should be readable without authentication.")
    } else Nil

  private def userName: String = context.loginAccount.map(_.userName).getOrElse("?")

  private def access(registry: Registry): String = if (registry.isPrivate) "private" else "public"

  private def describe(registry: Registry): String = s"${access(registry)}, overwrite=${registry.overwrite}"

  private def describeChanges(before: Registry, after: Registry): String =
    Seq(
      Option.when(before.isPrivate != after.isPrivate)(s"${access(before)} -> ${access(after)}"),
      Option.when(before.overwrite != after.overwrite)(s"overwrite ${before.overwrite} -> ${after.overwrite}"),
      Option.when(before.description != after.description)("description changed")
    ).flatten.mkString(", ")

  private def logChange(message: String, registry: Registry, details: String, madePublic: Boolean): Unit = {
    val text = s"Maven repository '${registry.name}' ${message} by ${userName} (${details})"
    if (madePublic && !context.settings.basicBehavior.allowAnonymousAccess) {
      logger.warn(s"${text}: public although anonymous access is disabled")
    } else {
      logger.info(text)
    }
  }


  get("/admin/maven")(adminOnly {
    gitbucket.mavenrepository.html.settings(getMavenRepositories(), getAllMavenTokens())
  })

  post("/admin/maven/_tokens/:id/_delete")(adminOnly {
    params("id").toIntOption.flatMap(getMavenToken).foreach { token =>
      deleteMavenToken(token.tokenId)
      logger.info(s"Maven token '${token.note}' of ${token.userName} deleted by ${userName}")
    }
    redirect("/admin/maven")
  })

  get("/admin/maven/_new")(adminOnly {
    gitbucket.mavenrepository.html.form(None)
  })

  post("/admin/maven/_new", repositoryCreateForm)(adminOnlyWithForm { (form: RepositoryCreateForm) =>
    createRegistry(form.name, form.description, form.overwrite, form.isPrivate)
    val registry = Registry(form.name, form.description, form.overwrite, form.isPrivate)
    logChange("created", registry, describe(registry), madePublic = !form.isPrivate)
    redirect("/admin/maven")
  })

  get("/admin/maven/:name/_edit")(adminOnly {
    gitbucket.mavenrepository.html.form(getMavenRepository(params("name")))
  })

  post("/admin/maven/:name/_edit", repositoryEditForm)(adminOnlyWithForm { (form: RepositoryEditForm) =>
    getMavenRepository(params("name")).map { before =>
      updateRegistry(before.name, form.description, form.overwrite, form.isPrivate)
      val after = before.copy(description = form.description, overwrite = form.overwrite, isPrivate = form.isPrivate)
      if (after != before) {
        logChange("changed", after, describeChanges(before, after), madePublic = before.isPrivate && !after.isPrivate)
      }
      redirect("/admin/maven")
    } getOrElse NotFound()
  })

  post("/admin/maven/:name/_delete")(adminOnly {
    getMavenRepository(params("name")).foreach { registry =>
      deleteRegistry(registry.name)
      logChange("deleted", registry, describe(registry), madePublic = false)
    }
    redirect("/admin/maven")
  })

  // An authenticated client of the repositories, and the Maven token it used, if any.
  private case class Client(account: Account, token: Option[MavenToken]) {
    def name: String = account.userName + token.fold("")(t => s" (token '${t.note}')")
  }

  // Browsing and downloading also accept the GitBucket session, so signed-in users get no Basic auth prompt.
  // Uploads and deletes need credentials.
  private def sessionOrCredentials(): Either[ActionResult, Client] =
    context.loginAccount.map(account => Right(Client(account, None))).getOrElse(credentials())

  // Basic auth with the password or a Maven token, or a Maven token as bearer token.
  private def credentials(): Either[ActionResult, Client] = {
    def byToken(secret: String) = findMavenToken(secret).map { case (account, token) => Client(account, Some(token)) }
    request.header("Authorization").flatMap {
      case auth if auth.startsWith("Basic ") => {
        val Array(username, secret) = AuthUtil.decodeAuthHeader(auth).split(":", 2)
        // Tokens are recognized by their prefix, so they never reach the password check (and LDAP).
        if (isMavenToken(secret)) byToken(secret).filter(_.account.userName == username)
        else authenticate(context.settings, username, secret).map(Client(_, None))
      }
      case auth if auth.startsWith("Bearer ") => Some(auth.substring(7).trim).filter(isMavenToken).flatMap(byToken)
      case _ => None
    }.toRight {
      response.setHeader("WWW-Authenticate", "Basic realm=\"GitBucket Maven Repository\"")
      org.scalatra.Unauthorized()
    }
  }

  // Uploads and deletes: read-only tokens are refused.
  private def writeCredentials(): Either[ActionResult, Client] =
    credentials().filterOrElse(_.token.forall(_.canWrite), Forbidden())

  post("/admin/maven/:name/_deletefiles")(adminOnly {
    val name  = params("name")
    val path  = validatePath(params("path"))
    val files = multiParams("files")

    files.foreach { file =>
      val fullPath = if (path.nonEmpty) {
        s"${RegistryPath}/${name}/${path}/${file}"
      } else {
        s"${RegistryPath}/${name}/${file}"
      }
      val f = new File(fullPath)
      FileUtils.deleteQuietly(f)
    }
    if (files.nonEmpty) {
      logger.info(s"Maven repository '${name}': ${files.mkString(", ")} in /${path} deleted by ${userName}")
    }
    if (path.nonEmpty) {
      redirect(s"/maven/${name}/${path}/")
    } else {
      redirect(s"/maven/${name}/")
    }
  })

  get("/maven/:name"){
    download(params("name"), "")
  }

  get("/maven/:name/*"){
    val path = validatePath(multiParams("splat").head)
    download(params("name"), path)
  }

  private def download(name: String, path: String) = {
    val result = for {
      // Find registry
      registry <- getMavenRepository(name).toRight { NotFound() }
      // Basic authentication
      _ <- if(registry.isPrivate){ sessionOrCredentials().map(x => Some(x)) } else Right(None)
      //path = multiParams("splat").head
      file = new File(s"${RegistryPath}/${name}/${path}")
    } yield {
      file match {
        // Download the file
        case f if f.exists && f.isFile =>
          contentType = FileUtil.getMimeType(path)
          response.setContentLength(file.length.toInt)
          Using.resource(new FileInputStream(file)){ in =>
            IOUtils.copy(in, response.getOutputStream)
          }

        // Render the directory index
        case f if f.exists && f.isDirectory =>
          val files = file.listFiles.toSeq.sortWith { (file1, file2) =>
            (file1.isDirectory, file2.isDirectory) match {
              case (true , false) => true
              case (false, true ) => false
              case _ => file1.getName.compareTo(file2.getName) < 0
            }
          }

          gitbucket.mavenrepository.html.files(name, path, files)

        // Otherwise
        case _ => NotFound()
      }
    }

    result.fold(identity, identity)
  }

  put("/maven/:name/*"){
    val name = params("name")
    val path = validatePath(multiParams("splat").head)

    val result = for {
      // Find registry
      registry <- getMavenRepository(name).toRight { NotFound() }
      // Uploads always need credentials, also for public repositories
      client <- writeCredentials()
      // Overwrite check
      file = new File(s"${RegistryPath}/${name}/${path}")
      _    <- if(file.getName == "maven-metadata.xml" || file.getName.startsWith("maven-metadata.xml.")){
                Right(())
              } else if(!registry.overwrite && file.exists){
                Left(NotAcceptable())
              } else {
                Right(())
              }
    } yield {
      val parent = file.getParentFile
      if(!parent.exists){
        parent.mkdirs()
      }
      Using.resource(new FileOutputStream(file)){ out =>
        IOUtils.copy(request.getInputStream, out)
      }
      logger.debug(s"Maven repository '${name}': /${path} uploaded by ${client.name}")
      Ok()
    }

    result.fold(identity, identity)
  }

  // authentication required
  // delete artifacts, only if the registry is overwritable
  delete("/maven/:name/*") {
    val name = params("name")
    val path = validatePath(multiParams("splat").head)

    val result = for {
      registry <- getMavenRepository(name).toRight(NotFound())
      client   <- writeCredentials()
      path     =  multiParams("splat").head
      file     =  Paths.get(RegistryPath, name, path)
      repoBase =  Paths.get(RegistryPath, registry.name)
      _        <- if (!Files.exists(file) || !registry.overwrite) Left(NotAcceptable()) else Right(())
    } yield {
      if (Files.isSameFile(repoBase, file)) {
        // clean up repository
        FileUtils.cleanDirectory(file.toFile)
      } else {
        // remove file and remove the directory if it's empty
        FileUtils.forceDelete(file.toFile)
        val parent = file.getParent
        if (!Files.isSameFile(repoBase, parent)) {
          FileUtil.deleteDirectoryIfEmpty(parent.toFile)
        }
      }
      logger.info(s"Maven repository '${name}': /${path} deleted by ${client.name}")
      Ok()
    }

    result.fold(identity, identity)
  }

  private def unique: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      getMavenRepository(value).map { _ => "Repository already exist." }
    }
  }

  private def validatePath(path: String): String = {
    if (path != null && path.contains("..")) {
      halt(BadRequest())
    }
    path
  }
}
