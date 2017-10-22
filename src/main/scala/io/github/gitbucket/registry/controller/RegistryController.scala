package io.github.gitbucket.registry.controller

import java.io.{File, FileInputStream}
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}

import io.github.gitbucket.registry._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.FileUtil
import gitbucket.core.util.SyntaxSugars.using
import net.sf.webdav.{LocalFileSystemStore, WebDavServletBean}
import org.apache.commons.io.IOUtils
import org.scalatra.Ok

class RegistryController extends ControllerBase {

  get("/repo/?"){
    Ok(<html>
      <head>
        <title>Available repositories</title>
      </head>
      <body>
        <h1>Library repositories</h1>
        <ul>
          {Registries.map { registory =>
            <li>
              <a href={context.baseUrl + "/repo/" + registory.name + "/"}>{registory.name}</a>
            </li>
          }}
        </ul>
      </body>
    </html>)
  }

  get("/repo/:name"){
    val name = params("name")
    redirect(s"/repo/${name}/")
  }

  get("/repo/:name/*"){
    val name = params("name")

    if(Registries.contains(name)){
      val path = multiParams("splat").head
      val fullPath = s"${RegistryPath}/${name}/${path}"
      val file = new File(fullPath)

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
          redirect(s"/repo/${name}/${path}/")

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
                      <a href={context.baseUrl + "/repo/" + name + "/" + path + file.getName + "/"}>{file.getName}/</a>
                    } else {
                      <a href={context.baseUrl + "/repo/" + name + "/" + path + file.getName}>{file.getName}</a>
                    }}
                  </li>
                }}
              </ul>
            </body>
          </html>)

        // Otherwise
        case _ => NotFound()
      }
    } else NotFound()
  }

  put("/repo/:name/*"){
    val name = params("name")
    val path = multiParams("splat").head

    // TODO permission check

    val webdav = new WebDavServletBean()
    webdav.init(new LocalFileSystemStore(new File(s"${RegistryPath}/${name}")), null, null, -1, true)

    webdav.service(new WebDavRequest(request, "/" + path), response)
  }
}

/**
 * Wraps HttpServletRequest to overwrite pathInfo.
 */
class WebDavRequest(request: HttpServletRequest, uri: String) extends HttpServletRequestWrapper(request) {
  override def getPathInfo(): String = uri
}
