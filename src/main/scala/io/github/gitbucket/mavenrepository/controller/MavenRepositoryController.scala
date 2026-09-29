package io.github.gitbucket.mavenrepository.controller

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}

import io.github.gitbucket.mavenrepository._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Account
import gitbucket.core.service.AccountService
import gitbucket.core.util.{AdminAuthenticator, AuthUtil, FileUtil}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.mavenrepository.model.Registry
import io.github.gitbucket.mavenrepository.service.MavenRepositoryService
import org.apache.commons.io.{FileUtils, IOUtils}
import org.slf4j.LoggerFactory
import org.scalatra.forms._
import org.scalatra.i18n.Messages
import org.scalatra.{ActionResult, NotAcceptable, Ok}
import scala.util.Using
import org.scalatra.BadRequest

class MavenRepositoryController extends ControllerBase with AccountService with MavenRepositoryService
  with AdminAuthenticator {

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

  private def logChange(message: String, registry: Registry, madePublic: Boolean): Unit = {
    val text = s"Maven repository '${registry.name}' ${message} by ${context.loginAccount.map(_.userName).getOrElse("?")} " +
      s"(${if (registry.isPrivate) "private" else "public"}, overwrite=${registry.overwrite})"
    if (madePublic && !context.settings.basicBehavior.allowAnonymousAccess) {
      logger.warn(s"${text}: public although anonymous access is disabled")
    } else {
      logger.info(text)
    }
  }


  get("/admin/maven")(adminOnly {
    gitbucket.mavenrepository.html.settings(getMavenRepositories())
  })

  get("/admin/maven/_new")(adminOnly {
    gitbucket.mavenrepository.html.form(None)
  })

  post("/admin/maven/_new", repositoryCreateForm)(adminOnlyWithForm { (form: RepositoryCreateForm) =>
    createRegistry(form.name, form.description, form.overwrite, form.isPrivate)
    logChange("created", Registry(form.name, form.description, form.overwrite, form.isPrivate), madePublic = !form.isPrivate)
    redirect("/admin/maven")
  })

  get("/admin/maven/:name/_edit")(adminOnly {
    gitbucket.mavenrepository.html.form(getMavenRepository(params("name")))
  })

  post("/admin/maven/:name/_edit", repositoryEditForm)(adminOnlyWithForm { (form: RepositoryEditForm) =>
    getMavenRepository(params("name")).map { before =>
      updateRegistry(before.name, form.description, form.overwrite, form.isPrivate)
      val after = before.copy(description = form.description, overwrite = form.overwrite, isPrivate = form.isPrivate)
      if (after != before) logChange("changed", after, madePublic = before.isPrivate && !after.isPrivate)
      redirect("/admin/maven")
    } getOrElse NotFound()
  })

  post("/admin/maven/:name/_delete")(adminOnly {
    getMavenRepository(params("name")).foreach { registry =>
      deleteRegistry(registry.name)
      logChange("deleted", registry, madePublic = false)
    }
    redirect("/admin/maven")
  })

  // Browsing and downloading also accept the GitBucket session, so signed-in users get no Basic auth prompt.
  // Uploads and deletes stay on Basic auth only.
  private def sessionOrBasicAuthentication(): Either[ActionResult, Account] =
    context.loginAccount.map(Right(_)).getOrElse(basicAuthentication())

  private def basicAuthentication(): Either[ActionResult, Account] = {
    request.header("Authorization").flatMap {
      case auth if auth.startsWith("Basic ") => {
        val Array(username, password) = AuthUtil.decodeAuthHeader(auth).split(":", 2)
        authenticate(context.settings, username, password)
      }
      case _ => None
    }.toRight {
      response.setHeader("WWW-Authenticate", "Basic realm=\"GitBucket Maven Repository\"")
      org.scalatra.Unauthorized()
    }
  }

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
      _ <- if(registry.isPrivate){ sessionOrBasicAuthentication().map(x => Some(x)) } else Right(None)
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
      // Basic authentication: uploads always need it, also for public repositories
      _ <- basicAuthentication()
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
      _        <- basicAuthentication()
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
