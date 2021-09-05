package io.github.gitbucket.mavenrepository.controller

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}

import io.github.gitbucket.mavenrepository._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Account
import gitbucket.core.service.AccountService
import gitbucket.core.util.{AdminAuthenticator, AuthUtil, FileUtil}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.mavenrepository.service.MavenRepositoryService
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalatra.forms._
import org.scalatra.i18n.Messages
import org.scalatra.{ActionResult, NotAcceptable, Ok}
import scala.util.Using

class MavenRepositoryController extends ControllerBase with AccountService with MavenRepositoryService
  with AdminAuthenticator {

  case class RepositoryCreateForm(name: String, description: Option[String], overwrite: Boolean, isPrivate: Boolean)
  case class RepositoryEditForm(description: Option[String], overwrite: Boolean, isPrivate: Boolean)

  val repositoryCreateForm = mapping(
    "name"        -> trim(label("Name", text(required, identifier, maxlength(100), unique))),
    "description" -> trim(label("Description", optional(text()))),
    "overwrite"   -> trim(boolean()),
    "isPrivate"   -> trim(boolean())
  )(RepositoryCreateForm.apply)

  val repositoryEditForm = mapping(
    "description" -> trim(label("Description", optional(text()))),
    "overwrite"   -> trim(boolean()),
    "isPrivate"   -> trim(boolean())
  )(RepositoryEditForm.apply)


  get("/admin/maven")(adminOnly {
    gitbucket.mavenrepository.html.settings(getMavenRepositories())
  })

  get("/admin/maven/_new")(adminOnly {
    gitbucket.mavenrepository.html.form(None)
  })

  post("/admin/maven/_new", repositoryCreateForm)(adminOnly { form =>
    createRegistry(form.name, form.description, form.overwrite, form.isPrivate)
    redirect("/admin/maven")
  })

  get("/admin/maven/:name/_edit")(adminOnly {
    gitbucket.mavenrepository.html.form(getMavenRepository(params("name")))
  })

  post("/admin/maven/:name/_edit", repositoryEditForm)(adminOnly { form =>
    updateRegistry(params("name"), form.description, form.overwrite, form.isPrivate)
    redirect("/admin/maven")
  })

  post("/admin/maven/:name/_delete")(adminOnly {
    deleteRegistry(params("name"))
    redirect("/admin/maven")
  })

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
    val name = params("name")
    val path = params("path")
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
    download(params("name"), multiParams("splat").head)
  }

  private def download(name: String, path: String) = {
    val result = for {
      // Find registry
      registry <- getMavenRepository(name).toRight { NotFound() }
      // Basic authentication
      _ <- if(registry.isPrivate){ basicAuthentication().map(x => Some(x)) } else Right(None)
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

    val result = for {
      // Find registry
      registry <- getMavenRepository(name).toRight { NotFound() }
      // Basic authentication
      _ <- if(registry.isPrivate){ basicAuthentication().map(x => Some(x)) } else Right(None)
      // Overwrite check
      path = multiParams("splat").head
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
    val result = for {
      registry <- getMavenRepository(name).toRight(NotFound())
      _<- basicAuthentication()
      path = multiParams("splat").head
      file = Paths.get(RegistryPath, name, path)
      repoBase = Paths.get(RegistryPath, registry.name)
      _ <- if (!Files.exists(file) || !registry.overwrite) {
        Left(NotAcceptable())
      } else {
        Right(())
      }
    } yield {
      if (Files.isSameFile(repoBase, file)) {
        // clean up repository
        FileUtils.cleanDirectory(file.toFile)
      } else {
        // remove file and remove the directory if it's empty
        FileUtils.deleteDirectory(file.toFile)
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
}
