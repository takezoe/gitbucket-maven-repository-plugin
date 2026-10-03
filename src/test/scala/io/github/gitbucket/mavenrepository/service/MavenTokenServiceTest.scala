package io.github.gitbucket.mavenrepository.service

import gitbucket.core.service.AccountService
import io.github.gitbucket.mavenrepository.TestDatabase
import io.github.gitbucket.mavenrepository.model.Profile.profile.blockingApi._
import org.scalatest.funsuite.AnyFunSuite

import java.util.Date

class MavenTokenServiceTest extends AnyFunSuite with MavenTokenService with AccountService {

  private def withTestDB[A](action: Session => A): A = TestDatabase.withTestDB { implicit session =>
    createAccount("alice", "password", "Alice", "alice@example.com", isAdmin = false, None, None)
    createAccount("bob", "password", "Bob", "bob@example.com", isAdmin = false, None, None)
    action(session)
  }

  private def daysFromNow(days: Int) = new Date(System.currentTimeMillis + days * 24L * 3600 * 1000)

  test("createMavenToken returns a prefixed secret and stores only its hash") {
    withTestDB { implicit session =>
      val (token, secret) = createMavenToken("alice", "ci", canWrite = true, Some(daysFromNow(90)))

      assert(secret.startsWith("gbmvn_") && secret.length == 46)
      assert(isMavenToken(secret))
      assert(token.tokenHash != secret && !token.tokenHash.contains(secret))
      assert(getMavenToken(token.tokenId).contains(token))
    }
  }

  test("findMavenToken returns the account and token, and records the use") {
    withTestDB { implicit session =>
      val (token, secret) = createMavenToken("alice", "ci", canWrite = false, None)

      val found = findMavenToken(secret)
      assert(found.map(_._1.userName).contains("alice"))
      assert(found.map(_._2.tokenId).contains(token.tokenId))
      assert(getMavenToken(token.tokenId).flatMap(_.lastUsed).nonEmpty)
    }
  }

  test("findMavenToken ignores unknown and expired tokens, and tokens of removed accounts") {
    withTestDB { implicit session =>
      val (_, expired) = createMavenToken("alice", "old", canWrite = true, Some(daysFromNow(-1)))
      val (_, removed) = createMavenToken("bob", "ci", canWrite = true, None)
      updateAccount(getAccountByUserName("bob").get.copy(isRemoved = true))

      assert(findMavenToken("gbmvn_0000000000000000000000000000000000000000").isEmpty)
      assert(findMavenToken(expired).isEmpty)
      assert(findMavenToken(removed).isEmpty)
    }
  }

  test("getMavenTokens lists a user's tokens, newest first; deleteMavenToken removes one") {
    withTestDB { implicit session =>
      val (first, _) = createMavenToken("alice", "first", canWrite = false, None)
      val (second, secret) = createMavenToken("alice", "second", canWrite = false, None)
      createMavenToken("bob", "other", canWrite = false, None)

      assert(getMavenTokens("alice").map(_.note) == Seq("second", "first"))
      assert(getAllMavenTokens().map(_.note) == Seq("second", "first", "other"))

      deleteMavenToken(second.tokenId)
      assert(getMavenTokens("alice").map(_.tokenId) == Seq(first.tokenId))
      assert(findMavenToken(secret).isEmpty)
    }
  }
}
