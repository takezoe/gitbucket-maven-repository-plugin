package io.github.gitbucket.mavenrepository.controller

import java.io.{File, FileInputStream, FileOutputStream}

import io.github.gitbucket.mavenrepository._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Account
import gitbucket.core.service.AccountService
import gitbucket.core.util.{AuthUtil, FileUtil}
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util.Implicits._
import io.github.gitbucket.mavenrepository.model.Registry
import io.github.gitbucket.mavenrepository.service.MavenRepositoryService
import org.apache.commons.io.IOUtils
import org.scalatra.forms._
import org.scalatra.{ActionResult, NotAcceptable, Ok}

class MavenRepositoryController extends ControllerBase with AccountService with MavenRepositoryService {

  case class RepositoryCreateForm(name: String, description: Option[String], overwrite: Boolean, isPrivate: Boolean)
  case class RepositoryEditForm(description: Option[String], overwrite: Boolean, isPrivate: Boolean)

  val repositoryCreateForm = mapping(
    "name"        -> trim(label("Name", text(required, identifier, maxlength(100)))), // TODO Unique check
    "description" -> trim(label("Description", optional(text()))),
    "overwrite"   -> trim(boolean()),
    "isPrivate"   -> trim(boolean())
  )(RepositoryCreateForm.apply)

  val repositoryEditForm = mapping(
    "description" -> trim(label("Description", optional(text()))),
    "overwrite"   -> trim(boolean()),
    "isPrivate"   -> trim(boolean())
  )(RepositoryEditForm.apply)


  get("/admin/maven"){
    gitbucket.mavenrepository.html.settings(getMavenRepositories())
  }

  get("/admin/maven/_new"){
    gitbucket.mavenrepository.html.form(None)
  }

  post("/admin/maven/_new", repositoryCreateForm){ form =>
    createRegistry(form.name, form.description, form.overwrite, form.isPrivate)
    redirect("/admin/maven")
  }

  get("/admin/maven/:name/_edit"){
    gitbucket.mavenrepository.html.form(getMavenRepository(params("name")))
  }

  post("/admin/maven/:name/_edit", repositoryEditForm){ form =>
    updateRegistry(params("name"), form.description, form.overwrite, form.isPrivate)
    redirect("/admin/maven")
  }

  post("/admin/maven/:name/_delete"){
    deleteRegistry(params("name"))
    redirect("/admin/maven")
  }

  get("/maven/?"){
    Ok(<html>
      <head>
        <title>Available repositories</title>
      </head>
      <body>
        <h1>Library repositories</h1>
        <ul>
          {getMavenRepositories().map { registory =>
            <li>
              <a href={context.baseUrl + "/maven/" + registory.name + "/"}>{registory.name}</a>
            </li>
          }}
        </ul>
      </body>
    </html>)
  }

  get("/maven/:name"){
    val name = params("name")
    redirect(s"/maven/${name}/")
  }

  private def basicAuthentication(): Either[ActionResult, Account] = {
    request.header("Authorization").flatMap {
      case auth if auth.startsWith("Basic ") => {
        val Array(username, password) = AuthUtil.decodeAuthHeader(auth).split(":", 2)
        authenticate(context.settings, username, password)
      }
      case _ => None
    }.toRight {
      response.setHeader("WWW-Authenticate", "Basic realm=\"GitBucket Maven Repository\"")
      Unauthorized()
    }
  }

  get("/maven/:name/*"){
    val result = for {
      // Basic authentication
      _ <- basicAuthentication()
      // Find registry
      name = params("name")
      _ <- getMavenRepositories().find(_.name == name).toRight { NotFound() }
      path = multiParams("splat").head
      file = new File(s"${RegistryPath}/${name}/${path}")
    } yield {
      file match {
        // Download the file
        case f if f.exists && f.isFile =>
          contentType = FileUtil.getMimeType(path)
          response.setContentLength(file.length.toInt)
          using(new FileInputStream(file)){ in =>
            IOUtils.copy(in, response.getOutputStream)
          }

        // Redirect to the normalized url for the directory
        case f if f.exists && f.isDirectory && path.nonEmpty && !path.endsWith("/") =>
          redirect(s"/maven/${name}/${path}/")

        // Render the directory index
        case f if f.exists && f.isDirectory =>
          val files = file.listFiles.toSeq.sortWith { (file1, file2) =>
            (file1.isDirectory, file2.isDirectory) match {
              case (true , false) => true
              case (false, true ) => false
              case _ => file1.getName.compareTo(file2.getName) < 0
            }
          }

          Ok(<html>
            <head>
              <title>{name} - /{path}</title>
            </head>
            <body>
              <h1>{name} - /{path}</h1>
              <ul>
                <li><a href="../">../</a></li>
                {files.map { file =>
                <li>
                  {if(file.isDirectory) {
                  <a href={context.baseUrl + "/maven/" + name + "/" + path + file.getName + "/"}>{file.getName}/</a>
                } else {
                  <a href={context.baseUrl + "/maven/" + name + "/" + path + file.getName}>{file.getName}</a>
                }}
                </li>
              }}
              </ul>
            </body>
          </html>)

        // Otherwise
        case _ => NotFound()
      }
    }

    result match {
      case Right(result) => result
      case Left(result)  => result
    }
  }

  put("/maven/:name/*"){
    val result = for {
      // Basic authentication
      _ <- basicAuthentication()
      // Find registry
      name = params("name")
      registry <- getMavenRepositories().find(_.name == name).toRight { NotFound() }
      // Overwrite check
      path = multiParams("splat").head
      file = new File(s"${RegistryPath}/${name}/${path}")
      _    <- if(file.getName == "maven-metadata.xml" || file.getName.startsWith("maven-metadata.xml.")){
                Right(())
              } else if(registry.overwrite == false && file.exists){
                Left(NotAcceptable())
              } else {
                Right(())
              }
    } yield {
      val parent = file.getParentFile
      if(!parent.exists){
        parent.mkdirs()
      }
      using(new FileOutputStream(file)){ out =>
        IOUtils.copy(request.getInputStream, out)
      }
      Ok()
    }

    result match {
      case Right(result) => result
      case Left(result)  => result
    }
  }
}
