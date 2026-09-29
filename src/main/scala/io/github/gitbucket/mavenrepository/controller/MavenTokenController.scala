package io.github.gitbucket.mavenrepository.controller

import java.util.Date

import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.UsersAuthenticator
import gitbucket.core.util.Implicits._
import io.github.gitbucket.mavenrepository.service.MavenTokenService
import org.scalatra.forms._
import org.slf4j.LoggerFactory

/**
 * The "Maven tokens" page in the account settings, where users manage their own tokens.
 */
class MavenTokenController extends ControllerBase with MavenTokenService with UsersAuthenticator {

  private val logger = LoggerFactory.getLogger(classOf[MavenRepositoryController])

  case class TokenForm(note: String, canWrite: Boolean, expiresInDays: Int)

  val tokenForm = mapping(
    "note"          -> trim(label("Note", text(required, maxlength(100)))),
    "canWrite"      -> trim(boolean()),
    "expiresInDays" -> trim(label("Expiration", number(required)))  // 0 = never
  )(TokenForm.apply)

  private def userName: String = context.loginAccount.get.userName

  get("/maven/_tokens")(usersOnly {
    gitbucket.mavenrepository.html.tokens(getMavenTokens(userName), flash.get("mavenToken").map(_.toString))
  })

  post("/maven/_tokens", tokenForm)(usersOnlyWithForm { (form: TokenForm) =>
    val expires = Option(form.expiresInDays).filter(_ > 0).map(days => new Date(System.currentTimeMillis + days * 24L * 3600 * 1000))
    val (token, secret) = createMavenToken(userName, form.note, form.canWrite, expires)
    logger.info(s"Maven token '${token.note}' (${if (token.canWrite) "read and write" else "read"}) created by ${userName}")
    flash.update("mavenToken", secret)
    redirect("/maven/_tokens")
  })

  post("/maven/_tokens/:id/_delete")(usersOnly {
    params("id").toIntOption.flatMap(getMavenToken).filter(_.userName == userName).foreach { token =>
      deleteMavenToken(token.tokenId)
      logger.info(s"Maven token '${token.note}' of ${token.userName} deleted by ${userName}")
    }
    redirect("/maven/_tokens")
  })
}
