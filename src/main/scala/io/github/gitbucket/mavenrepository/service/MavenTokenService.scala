package io.github.gitbucket.mavenrepository.service

import java.util.Date

import gitbucket.core.model.Account
import gitbucket.core.service.AccessTokenService
import gitbucket.core.util.StringUtil
import io.github.gitbucket.mavenrepository.model.MavenToken
import io.github.gitbucket.mavenrepository.model.Profile._
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import io.github.gitbucket.mavenrepository.model.Profile.dateColumnType

/**
 * Access tokens that only work for the Maven repositories (no Git, API or web sign-in).
 * Only the SHA-1 hash of a token is stored; the token itself is shown once, when it is created.
 */
trait MavenTokenService {

  def isMavenToken(secret: String): Boolean = secret.startsWith(MavenTokenService.Prefix)

  /** @return the new token and its secret */
  def createMavenToken(userName: String, note: String, canWrite: Boolean, expires: Option[Date])
                      (implicit s: Session): (MavenToken, String) = {
    val secret = MavenTokenService.Prefix + AccessTokenService.makeAccessTokenString
    val token = MavenToken(userName = userName, tokenHash = StringUtil.sha1(secret), note = note,
      canWrite = canWrite, expires = expires, lastUsed = None, registeredDate = new Date())
    val tokenId = (MavenTokens returning MavenTokens.map(_.tokenId)) insert token
    (token.copy(tokenId = tokenId), secret)
  }

  def getMavenToken(tokenId: Int)(implicit s: Session): Option[MavenToken] =
    MavenTokens.filter(_.tokenId === tokenId.bind).firstOption

  def getMavenTokens(userName: String)(implicit s: Session): Seq[MavenToken] =
    MavenTokens.filter(_.userName === userName.bind).sortBy(_.tokenId.desc).list

  def getAllMavenTokens()(implicit s: Session): Seq[MavenToken] =
    MavenTokens.sortBy(t => (t.userName, t.tokenId.desc)).list

  def deleteMavenToken(tokenId: Int)(implicit s: Session): Unit =
    MavenTokens.filter(_.tokenId === tokenId.bind).delete

  /**
   * Finds the account of a valid token: known, not expired, and its account not removed.
   * Records the use, at most once a minute.
   */
  def findMavenToken(secret: String)(implicit s: Session): Option[(Account, MavenToken)] = {
    val now = new Date()
    Accounts.join(MavenTokens).on(_.userName === _.userName)
      .filter { case (account, token) =>
        token.tokenHash === StringUtil.sha1(secret).bind && account.removed === false.bind
      }
      .firstOption
      .filterNot { case (_, token) => token.isExpired }
      .map { case (account, token) =>
        if (token.lastUsed.forall(now.getTime - _.getTime > 60 * 1000)) {
          MavenTokens.filter(_.tokenId === token.tokenId.bind).map(_.lastUsed).update(now)
        }
        (account, token)
      }
  }
}

object MavenTokenService {
  val Prefix = "gbmvn_"
}
