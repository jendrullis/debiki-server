/**
 * Copyright (c) 2012 Kaj Magnus Lindberg (born 1979)
 */

package controllers

import com.debiki.v0._
import debiki._
import debiki.DebikiHttp._
import play.api._
import play.api.{mvc => pm}
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import PageActions._
import Prelude._
import Utils._
import Utils.ValidationImplicits._
import controllers.PageRequest.PathClashException


object AppCreatePage extends mvc.Controller {


    /*
    val changeShowId =
      // If the user may not hide the page id:
      if (!pageReq.permsOnPage.hidePageIdInUrl) None
      // If the user has specified a slug but no id, default to no id:
      else if (pathIn.pageSlug.nonEmpty) Some(false)
      // If the url path is a folder/index page:
      else Some(true)
    */

  def getViewNewPageUrl(pathIn: PagePath) =
        PageGetAction(pathIn, pageMustExist = false) { folderReq =>

    if (!pathIn.isFolderOrIndexPage)
      throwBadReq("DwE903XH3", "Call on folders only")

    val pageId = nextRandomString()
    val passhash = Passhasher.makePasshashQueryParam(pageId)

    val pageSlug = folderReq.queryString.getOrThrowBadReq("page-slug")
    val showId = folderReq.queryString.getBoolOrTrue("show-id")

    val newPath = folderReq.pagePath.copy(
      pageId = Some(pageId), showId = showId, pageSlug = pageSlug)

    val viewNewPageUrl = newPath.path +"?view-new-page="+ pageId +"&"+ passhash

    OkSafeJson(JsObject(Seq("viewNewPageUrl" -> JsString(viewNewPageUrl))))
  }


  def viewNewPage(pathIn: PagePath, pageId: String) =
        PageGetAction(pathIn, pageMustExist = false) { pageReqOrig =>

    // Ensure page id generated by server.
    Passhasher.throwIfBadPasshash(pageReqOrig, pageId)

    // Create a PageRequest for the new page (and be sure to use `pageId`
    // so people cannot /specify/any/-pageId).
    val pageReq = {
      val newPagePath = newPagePathFromUrl(pageReqOrig, pageId)
      val request =
        try { PageRequest(pageReqOrig, newPagePath) }
        catch {
          case ex: PathClashException =>
            throwForbidden(
              "DwE17Sf3", s"Cannot create new page at ${newPagePath.path}," +
              s" with id `$pageId`: There is already another page at that" +
              s" location, with id `${ex.databasePageId}`")
        }

      if (request.pageExists) {
        // The page we're supposed to create has already been created,
        // so we don't need to create any empty dummy page to show before
        // the page has actually been created. (The URL to this new
        // page is probably being reloaded, after the page was saved.)
        request
      }
      else {
        // Create empty dummy page.
        val newPageMeta = newPageMetaFromUrl(pageReqOrig, pageId)
        pageReqOrig.copyWithPreloadedPage(
          PageStuff(newPageMeta, newPagePath, Debate(newPageMeta.pageId)),
          pageExists = false)
      }
    }
    assert(pageReq.pageExists == pageReq.pageMeta.pageExists)

    val pageInfoYaml =
      if (pageReq.user.isEmpty) ""
      else Application.buildPageInfoYaml(pageReq)

    // If not logged in, then include an empty Yaml tag, so the browser
    // notices that it got that elem, and won't call GET ?page-info.
    val infoNode = <pre class='dw-data-yaml'>{pageInfoYaml}</pre>
    val pageHtml = pageReq.dao.renderTemplate(pageReq, appendToBody = infoNode)

    Ok(pageHtml) as HTML
  }


  def newPagePathFromUrl(pageReq: PageRequest[_], pageId: String): PagePath = {
    val pageSlug = pageReq.pagePath.pageSlug
    val showId = pageReq.pagePath.showId
    pageReq.pagePath.copy(pageId = Some(pageId),
      pageSlug = pageSlug, showId = showId)
  }


  def newPageMetaFromUrl(pageReq: PageRequest[_], pageId: String): PageMeta = {
    import pageReq.queryString

    val pageRole = queryString.getEmptyAsNone("page-role").map(
      stringToPageRole _) getOrElse PageRole.Any

    val parentPageId: Option[String] =
      queryString.getEmptyAsNone("parent-page-id")

    // In case of a Javascript bug.
    if (parentPageId == Some("undefined"))
      throwBadReq("DwE93HF2", "Parent page id is `undefined`")

    PageMeta.forNewPage(pageId, pageReq.ctime, pageRole,
      parentPageId = parentPageId)
  }


  def dummyTitle(request: PageRequest[_]) = Post(
    id = Page.TitleId, parent = Page.TitleId, ctime = request.ctime,
    loginId = request.loginId_!, newIp = None, text = DummyTitleText,
    markup = Markup.DefaultForPageTitle.id,
    approval = Some(Approval.Preliminary),
    tyype = PostType.Text)


  def dummyBody(request: PageRequest[_]) = dummyTitle(request).copy(
    id = Page.BodyId, parent = Page.BodyId, text = DummyPageText,
    markup = Markup.DefaultForPageBody.id)


  /**
   * Hmm, regrettably this breaks should I rename any case object.
   * Perhaps use a match ... case list instead?
   */
  private val _PageRoleLookup = Vector(
    PageRole.Any, PageRole.Homepage, PageRole.BlogMainPage,
    PageRole.BlogArticle, PageRole.ForumMainPage, PageRole.ForumThread,
    PageRole.WikiMainPage, PageRole.WikiPage)
    .map(x => (x, x.toString))


  // COULD replace with PageRole.fromString(): Option[PageRole]
  def stringToPageRole(pageRoleString: String): PageRole =
    _PageRoleLookup.find(_._2 == pageRoleString).map(_._1).getOrElse(
      throwBadReq("DwE930rR3", "Bad page role string: "+ pageRoleString))


  private def _pageRoleToString(pageRole: PageRole): String = pageRole.toString


  val DummyTitleText =
    "New Page (click to edit)"

  val DummyPageText: String =
    """|**To edit this page:**
       |
       |  - Click this text, anywhere.
       |  - Then select *Improve* in the menu that appears.
       |
       |## Example Subtitle
       |
       |### Example Sub Subtitle
       |
       |[Example link, to nowhere](http://example.com/does/not/exist)
       |
       |""".stripMargin

}
