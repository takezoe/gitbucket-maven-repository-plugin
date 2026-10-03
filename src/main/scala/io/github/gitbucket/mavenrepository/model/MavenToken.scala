package io.github.gitbucket.mavenrepository.model

import java.util.Date

trait MavenTokenComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val MavenTokens = TableQuery[MavenTokens]

  class MavenTokens(tag: Tag) extends Table[MavenToken](tag, "MVN_TOKEN"){
    val tokenId        = column[Int]("TOKEN_ID", O.AutoInc)
    val userName       = column[String]("USER_NAME")
    val tokenHash      = column[String]("TOKEN_HASH")
    val note           = column[String]("NOTE")
    val canWrite       = column[Boolean]("CAN_WRITE")
    val expires        = column[Date]("EXPIRES")
    val lastUsed       = column[Date]("LAST_USED")
    val registeredDate = column[Date]("REGISTERED_DATE")
    def * = (tokenId, userName, tokenHash, note, canWrite, expires.?, lastUsed.?, registeredDate) <>
      (MavenToken.tupled, MavenToken.unapply)
  }

}

case class MavenToken(
  tokenId: Int = 0,
  userName: String,
  tokenHash: String,
  note: String,
  canWrite: Boolean,
  expires: Option[Date],
  lastUsed: Option[Date],
  registeredDate: Date
) {
  def isExpired: Boolean = expires.exists(_.before(new Date()))
}
