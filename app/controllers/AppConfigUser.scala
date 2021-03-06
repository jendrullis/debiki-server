/**
 * Copyright (C) 2012 Kaj Magnus Lindberg (born 1979)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import com.debiki.v0._
import debiki._
import debiki.DebikiHttp._
import play.api._
import play.api.mvc.{Action => _}
import PageActions._
import Prelude._
import Utils.ValidationImplicits._
import EmailNotfPrefs.EmailNotfPrefs


/** Configures a user, can e.g. change receive-email preferences.
  */
object AppConfigUser extends mvc.Controller {


  val ConfigCookie = "dwCoConf"
  val ConfigCookieEmailNotfs = "EmNt"
  val ConfigCookieEmailSpecd = "EmSp"

  val FormPramEmailNotfs = "dw-fi-eml-prf-rcv"
  val FormPramEmailAddr = "dw-fi-eml-prf-adr"


  def showForm(pathIn: PagePath, userId: String)
        = PageGetAction(pathIn) { pageReq: PageGetRequest =>
    unimplemented
  }


  def handleForm(pathIn: PagePath, userId: String)
        = PagePostAction(500)(pathIn) { pageReq: PagePostRequest =>
    val user = pageReq.user_!
    val identity = pageReq.identity_!

    // For now:
    if (userId != "me")
      throwBadParamValue("DwE09EF32", "user")

    val emailNotfPrefs = pageReq.getEmptyAsNone(FormPramEmailNotfs) match {
      case Some("yes") => EmailNotfPrefs.Receive
      case Some("no") => EmailNotfPrefs.DontReceive
      case Some(x) => throwBadParamValue("DwE09EF32", FormPramEmailNotfs)
      case _ =>
        // This happened only once when I clicked the No button in Firefox, therefore:
        EmailNotfPrefs.DontReceive
        // I don't know why no post data was received. I'd rather have Some("no")
        // to be posted, and I would rather do:
        //   throwParamMissing("DwE83IhB6", FormPramEmailNotfs)

      // (There's no value that maps to ForbiddenForever. That functionality
      // is only available via email login, that is, for the actual
      // owner of the email address.)
    }

    val emailOpt = pageReq.getEmptyAsNone(FormPramEmailAddr)

    val newEmailAddr =
      if (emailOpt.isEmpty) None
      else {
        // For now, abort, if specified email differs from existing.
        // (You're currently not allowed to change your email via
        // this interface.)
        if (user.email.nonEmpty && user.email != emailOpt.get)
          throwBadReq("DwE8RkL3", "Email differs from user.email")
        if (identity.email.nonEmpty && identity.email != emailOpt.get)
          throwBadReq("DwE09IZ8", "Email differs from identity.email")
        if (user.email.isEmpty && identity.email.isEmpty)
          emailOpt
        else
          None
      }

    if (pageReq.user_!.isAuthenticated)
      _configAuUserUpdCookies(pageReq, emailNotfPrefs, newEmailAddr)
    else
      _configUnauUserUpdCookies(pageReq, emailNotfPrefs, newEmailAddr)
  }


  private def _configAuUserUpdCookies(pageReq: PagePostRequest,
        emailNotfPrefs: EmailNotfPrefs, newEmailAddr: Option[String])
        : mvc.PlainResult = {

    val (user, loginId) = (pageReq.user_!, pageReq.loginId_!)
    require(user.isAuthenticated)

    // Update email preferences and email address.
    newEmailAddr match {
      case None =>
        if (user.email.isEmpty && emailNotfPrefs == EmailNotfPrefs.Receive)
          throwBadReqDialog(
            "DwE8kOJ5", "No Email Address", "",
            "Please specify an email address.")

        if (emailNotfPrefs != user.emailNotfPrefs) {
          pageReq.dao.configRole(loginId, pageReq.ctime,
             roleId = user.id, emailNotfPrefs = Some(emailNotfPrefs))
        }
      case Some(addr) =>
        // Update DW1_USERS: add new email? and cofig notf prefs.
        // Add email prefs to user config cookie.
        throwForbidden(
          "DwE03921", "Changing DW1_USERS.EMAIL is currently not possible")
    }

    val userNewPrefs = user.copy(emailNotfPrefs = emailNotfPrefs)
    Ok.withCookies(userConfigCookie(pageReq.identity_!, userNewPrefs))
  }


  private def _configUnauUserUpdCookies(pageReq: PagePostRequest,
        emailNotfPrefs: EmailNotfPrefs, newEmailAddr: Option[String])
        : mvc.PlainResult = {
    require(!pageReq.user_!.isAuthenticated)

    // Login again, if new email specified, because the email is part of the
    // unauthenticated identity (so we need a new login and identity).
    val (user, loginId, newSessCookies) = newEmailAddr match {
      case None =>
        (pageReq.user_!, pageReq.loginId_!, Nil)
      case Some(newAddr) =>
        val (loginGrant, newSessCookies) =
          AppLoginGuest.loginGuestAgainWithNewEmail(pageReq, newAddr)
        (loginGrant.user, loginGrant.login.id, newSessCookies)
    }

    // Update email notification preferences.
    // (If no email addr specified: do nothing. Then, the next time that
    // that email is specified, the user will be asked again --
    // but not until the user logins again; the answer is rememberd
    // in a cookie. (You cannot config notf prefs for the empty email addr,
    // because that'd affect everyone that hasn't yet configd some addr.))
    if (user.email.isEmpty) {
      if (emailNotfPrefs == EmailNotfPrefs.Receive)
        throwBadReqDialog(
          "DwE82hQ2", "No Email Address", "",
          "Please specify an email address.")
    }
    else if (emailNotfPrefs != user.emailNotfPrefs) {
      pageReq.dao.configIdtySimple(loginId, pageReq.ctime,
        user.email, emailNotfPrefs)
    }

    val userNewPrefs = user.copy(emailNotfPrefs = emailNotfPrefs)
    val configCookie = userConfigCookie(pageReq.identity_!, userNewPrefs)
    Ok.withCookies(configCookie::newSessCookies.toList: _*)
  }


  def userConfigCookie(loginGrant: LoginGrant): mvc.Cookie =
    userConfigCookie(loginGrant.identity, loginGrant.user)


  def userConfigCookie(identity: Identity, user: User): mvc.Cookie = {
    val email = user.email.nonEmpty ? user.email | identity.email
    val emailPrefs =
      if (user.emailNotfPrefs == EmailNotfPrefs.Unspecified) ""
      else ConfigCookieEmailNotfs + emailPrefsToChar(user.emailNotfPrefs)
    val emailSpecd = email.nonEmpty ? ConfigCookieEmailSpecd | ""
    val value = emailPrefs +"."+ emailSpecd
    mvc.Cookie(name = ConfigCookie, value = value, httpOnly = false)
  }


  def emailPrefsToChar(prefs: EmailNotfPrefs): String = prefs match {
    case EmailNotfPrefs.Receive => "R"
    case EmailNotfPrefs.DontReceive => "N"
    case EmailNotfPrefs.ForbiddenForever => "F"
    case EmailNotfPrefs.Unspecified => "U"
  }


  /*
  def emailPrefsFromChar(char: String): EmailNotfPrefs = char match {
    case "r" => EmailNotfPrefs.Receive
    case "n" => EmailNotfPrefs.DontReceive
    case "f" => EmailNotfPrefs.ForbiddenForever
    case "u" => EmailNotfPrefs.Unspecified
    case "" => EmailNotfPrefs.Unspecified
    case x => throwBadReq("DwE97RBQ5", "Bad email prefs char: "+ x)
  } */

}
