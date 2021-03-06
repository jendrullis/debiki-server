/**
 * Copyright (C) 2012-2013 Kaj Magnus Lindberg (born 1979)
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

package debiki

import com.debiki.v0._
//import com.twitter.ostrich.stats.Stats
import java.{util => ju, io => jio}
import scala.collection.JavaConversions._
import _root_.scala.xml.{NodeSeq, Node, Elem, Text, XML, Attribute}
import FlagReason.FlagReason
import Prelude._
import DebikiHttp._
import HtmlUtils._


object HtmlConfig {

  /**
   * A function from debate-id and post-id to a react URL.
   */
  def reactUrl(debateId: String, postId: ActionId) = "?act="+ postId
}


abstract class HtmlConfig {
  def termsOfUseUrl: String

  // If a form action is the empty string, the browser POSTS to the current
  // page, says the URI spec: http://www.apps.ietf.org/rfc/rfc3986.html#sec-5.4
  // COULD rename replyAction -> replyUrl (or reactUrl -> reactAction).
  def replyAction = "?reply"
  def rateAction = "?rate"
  def flagAction = "?flag"
  def loginActionSimple = ""
  def loginActionOpenId = ""
  def loginOkAction = ""
  def loginFailedAction = ""
  def logoutAction = ""


  /** Constructs a URL to more info on a certain user,
   *  adds "http://" if needed.
   */
  def userUrl(nilo: NiLo) = {
    "" // for now, since OpenID users cannot specify url, fix ...
    /*
    // Lift-Web or Java? escapes cookie values, so unescape `://'.
    // Scala 1.9? val ws = user.website.replaceAllLiterally("%3A%2F%2F", "://")
    val ws = user.website.replaceAll("%3A%2F%2F", "://")
    if (ws isEmpty) {
      ""
    } else if (ws.startsWith("http://") ||
                ws.startsWith("https://")) {
      ws
    } else {
      "http://"+ ws
    } */
  }

  /** Whether or not to show edit suggestions. */
  def showEdits_? = false  // doesn't work at all right now
  def hostAndPort = "localhost"

}


object HtmlPageSerializer {


  /**
    * @param nofollowArticle If true, links in the page body will be rel=nofollow.
    *   The only time it's set to false, is when we serve data to search engines, but
    *   usually we're replying to ajax requests. So defaults to true (and this feels safe).
    */
  def markupTextOf(post: Post, hostAndPort: String, nofollowArticle: Boolean = true): String =
    _markupTextOf(post, hostAndPort, nofollowArticle)._1.toString


  // COULD move to HtmlPostSerializer.
  def _markupTextOf(post: Post, hostAndPort: String, nofollowArticle: Boolean = true,
        showUnapproved: Boolean = false): (NodeSeq, Int) = {

    val isArticle = post.id == PageParts.BodyId

    // Use nofollow links in people's comments, so Google won't punish
    // the website if someone posts spam.
    def isArticeOrArticleQuestion = isArticle // || post.meta.isArticleQuestion
    val makeNofollowLinks =
      !isArticeOrArticleQuestion || (nofollowArticle && post.id == PageParts.BodyId)


    val text: String =
      if (showUnapproved) post.currentText
      else post.approvedText getOrElse {
        return (Nil, 0)
      }

    post.markup match {
      case "dmd0" =>
        // Debiki flavored markdown.
        val html = markdownToSafeHtml(
          text, hostAndPort, allowClassIdDataAttrs = isArticle, makeNofollowLinks)
        (html, -1)
      case "para" =>
        textToHtml(text)
      /*
    case "link" =>
      textToHtml(text) and linkify-url:s, w/ rel=nofollow)
    case "img" =>
      // Like "link", but also accept <img> tags and <pre>.
      // But nothing that makes text stand out, e.g. skip <h1>, <section>.
      */
      case "html" =>
        val cleanHtml = sanitizeHtml(
          text, allowClassIdDataAttrs = isArticle, makeNofollowLinks)
        (cleanHtml, -1)
      case "code" =>
        (<pre class='prettyprint'>{text}</pre>,
           text.count(_ == '\n'))
      /*
    case c if c startsWith "code" =>
      // Wrap the text in:
      //     <pre class="prettyprint"><code class="language-javascript">
      //  That's an HTML5 convention actually:
      // <http://dev.w3.org/html5/spec-author-view/
      //     the-code-element.html#the-code-element>
      // Works with: http://code.google.com/p/google-code-prettify/
      var lang = c.dropWhile(_ != '-')
      if (lang nonEmpty) lang = " lang"+lang  ; UN TESTED // if nonEmpty
      (<pre class={"prettyprint"+ lang}>{text}</pre>,
        post.text.count(_ == '\n'))
      // COULD include google-code-prettify js and css, and
      // onload="prettyPrint()".
      */
      case _ =>
        // Default to Debiki flavored markdown, for now.
        // (Later: update database, change null/'' to "dmd0" for the page body,
        // change to "para" for everything else.
        // Then warnDbgDie-default to "para" here not "dmd0".)
        (markdownToSafeHtml(text, hostAndPort,
          allowClassIdDataAttrs = isArticle, makeNofollowLinks), -1)
    }
  }


  /** Converts text to xml, returns (html, approx-line-count).
   *
   * Splits into <p>:s and <br>:s at newlines, does nothing else.
   */
  def textToHtml(text: String, charsPerLine: Int = 80)
      : Tuple2[NodeSeq, Int] = {
    var lineCount = 0
    val xml =
      // Two newlines end a paragraph.
      for (para <- text.split("\n\n").toList)
      yield {
        // One newline results in a row break.
        val lines = para.split("\n").zipWithIndex.flatMap(lineAndIndex => {
          lineCount += 1 + lineAndIndex._1.length / charsPerLine
          if (lineAndIndex._2 == 0) Text(lineAndIndex._1)
          else <br/> ++ Text(lineAndIndex._1)
        })
        <p>{lines}</p>
      }
    (xml, lineCount)
  }


  /**
   * Converts markdown to xml.
   */
  def markdownToSafeHtml(source: String, hostAndPort: String,
        allowClassIdDataAttrs: Boolean, makeLinksNofollow: Boolean = true): NodeSeq
        = /*Stats.time("markdownToSafeHtml")*/ {
    val htmlTextUnsafe =
       (new compiledjs.PagedownJsImpl()).makeHtml(source, hostAndPort)
    sanitizeHtml(htmlTextUnsafe, allowClassIdDataAttrs, makeLinksNofollow)
  }


  def sanitizeHtml(htmlTextUnsafe: String, allowClassIdDataAttrs: Boolean,
        makeLinksNofollow: Boolean = true): NodeSeq = {

    var htmlTextSafe: String =
      (new compiledjs.HtmlSanitizerJsImpl()).googleCajaSanitizeHtml(
        htmlTextUnsafe,
        // Cannot specify param names, regrettably, since we're calling
        // Java / compiled Javascript code.
        allowClassIdDataAttrs, // allowClassAndIdAttr
        allowClassIdDataAttrs) // allowDataAttr

    // As of 2011-08-18 the Google Caja js html sanitizer strips
    // target='_blank', (Seems to be a bug:
    // `Issue 1296: target="_blank" is allowed, but cleared by html_sanitize()'
    // http://code.google.com/p/google-caja/issues/detail?id=1296  )
    // Add target _blank here - not needed any more, I ask "do you really
    // want to close the page?" if people have started writing.
    //   htmlTextSafe = htmlTextSafe.replace("<a ", "<a target='_blank' ")

    if (makeLinksNofollow)
      htmlTextSafe = htmlTextSafe.replace("<a ", "<a rel='nofollow' ")

    // Use a HTML5 parser; html_sanitize outputs HTML5, which Scala's XML
    // parser don't understand (e.g. an <img src=…> tag with no </img>).
    // Lift-Web uses a certain nu.validator HTML5 parser; use it.
    // (html_sanitize, from Google Caja, also uses the very same parser.
    // So html_sanitize probably generates html that Lift-Web's version
    // of the nu.validator parser understands.)
    // Wrap the html text in a dummy tag to avoid a SAXParseException.
    liftweb.Html5.parse("<div>"+ htmlTextSafe +"</div>").get.child
  }


  /** Replaces spaces with the Unicode representation of non-breaking space,
   *  which is interpreted as {@code &nbsp;} by Web browsers.
   */
  def spaceToNbsp(text: String): String = text.replace(' ', '\u00a0')


  /**
   * Shows a link to the user represented by NiLo.
   */
  def linkTo(user: User, config: HtmlConfig): NodeSeq = linkTo(user)  // for now


  // COULD move to object HtmlPostSerializer
  def linkTo(user: User): NodeSeq = {
    var url = ""  // since not implemented anyway: config.userUrl(nilo)

    // COULD investigate: `url' is sometimes the email address!!
    // When signed in @gmail.com, it seems.
    // For now: (this is actually a good test anyway, in case someone
    // accidentally enters his email in the website field?)
    if (url.contains('@') || url.containsSlice("%40")) {
      System.err.println(
        "URL contains email? It contains `@' or `%40': "+ url)
      url = ""
    }
    val nameElem: NodeSeq = user.isAuthenticated match {
      case false =>
        // Indicate that the user was not logged in, that we're not sure
        // about his/her identity, by appending "??". If however s/he
        // provided an email address then only append one "?", because
        // other people probaably don't know what is it, so it's harder
        // for them people to impersonate her.
        // (Well, at least if I some day in some way indicate that two
        // persons with the same name actually have different emails.)
        xml.Text(user.displayName) ++
            <span class='dw-lg-t-spl'>{
              if (user.email isEmpty) "??" else "?"}</span>
      case _ => xml.Text(user.displayName)
    }
    val userLink = if (url nonEmpty) {
      <a class='dw-p-by' href={url} data-dw-u-id={user.id}
         rel='nofollow' target='_blank'>{nameElem}</a>
    } else {
      <span class='dw-p-by' data-dw-u-id={user.id}>{nameElem}</span>
    }
    userLink
  }


  /** XML for the user name and login/out links.
   */
  def loginInfo(userName: Option[String]): NodeSeq = {
    <span id='dw-u-info'>
      <span class='dw-u-name'>{userName.getOrElse("")}</span>
    </span>
    <span class='dw-u-lgi-lgo'>
      <a id='dw-a-login'>Log in</a>
      <a id='dw-a-logout'>Log out</a>
    </span>
  }


  /**
   * A script tag that hides comments and shows them on click.
   */
  val tagsThatHideShowInteractions = (
    <script type="text/javascript">
    jQuery('html').addClass('dw-hide-interactions');
    debiki.scriptLoad.done(function() {{
      debiki.v0.showInteractionsOnClick();
    }});
    </script>
  )

  private def _attrEq(name: String, value: String)(node: Node) =
    node.attribute(name).filter(_.text == value).isDefined

  // COULD move to HtmlPostSerializer
  def findChildrenOfNode(withClass: String, in: NodeSeq): Option[NodeSeq] =
    (in \\ "_").filter(_attrEq("class", withClass)).
        headOption.map(_.child)

  case class SerializedSingleThread(
    prevSiblingId: Option[ActionId],
    htmlNodes: Node)


  def wrapInPageTag(page: Page)(body: NodeSeq): NodeSeq =
    wrapInPageTag(page.meta, page.path)(body)


  def wrapInPageTag(pageMeta: PageMeta, pagePath: PagePath)(body: NodeSeq): NodeSeq = {
    <div id={"page-"+ pageMeta.pageId} class='debiki dw-debate dw-page'
        data-page_exists={pageMeta.pageExists.toString}
        data-page_path={pagePath.path}
        data-page_role={pageMeta.pageRole.toString}
        data-page_status={pageMeta.status.toString}
        data-parent_page_id={pageMeta.parentPageId.map(_.toString) getOrElse ""}>
      { body }
    </div>
  }

}



case class HtmlPageSerializer(
  page : PageParts,
  pageTrust: PageTrust,
  pageRoot: PageRoot,
  config: HtmlConfig,
  nofollowArticle: Boolean = true,
  showUnapproved: Boolean = false) {

  import HtmlPageSerializer._

  private lazy val pageStats = new PageStats(page, pageTrust)

  //private def lastChange: Option[String] =
  //  page.lastOrLaterChangeDate.map(toIso8601(_))


  private def postRenderer =
    HtmlPostRenderer(page, pageStats, config.hostAndPort, nofollowArticle,
      showUnapproved = showUnapproved)


  def renderSingleThread(postId: ActionId): Option[SerializedSingleThread] = {
    page.getPost(postId) map { post =>
      val html = renderThreads(depth = post.depth, posts = post::Nil,
        parentHorizontal = false, uncollapseFirst = true)
      // The post might have been deleted.
      if (html.isEmpty)
        return None
      assert(html.length == 1)
      val siblingsSorted = _sortPostsDescFitness(post.siblingsAndMe)
      var prevSibling: Option[Post] = None
      siblingsSorted.takeWhile(_.id != postId).foreach { sibling =>
        prevSibling = Some(sibling)
      }
      SerializedSingleThread(prevSibling.map(_.id), html.head)
    }
  }


  /**
   * The results from layoutPosts doesn't depend on who the user is
   * and can thus be cached. If a user has rated comment or has written
   * comments that are pending approval, then info on that stuff is
   * appended at the end of the server's reply (in a special <div>), and
   * client side Javascript update the page with user specific stuff.
   */
  def renderBodyAndComments(showComments: Boolean): NodeSeq = {
        // = Stats.time("renderBodyAndComments") {

    val cssArtclThread =
      if (pageRoot.subId == PageParts.BodyId) " dw-ar-t" else ""

    val rootPostsReplies = {
      val children = pageRoot.findChildrenIn(page)
      if (showUnapproved) children
      else children.filter(_.someVersionApproved)
    }

    val rootPost: Post = pageRoot.findOrCreatePostIn(page) getOrElse
       throwNotFound("DwE0PJ404", "Post not found: "+ pageRoot.subId)

    val cssDummy =
      if (rootPost.user_!.id == DummyPage.DummyAuthorUser.id) " dw-dummy" else ""

    val bodyAndComments =
      <div id={"dw-t-"+ rootPost.id}
           class={"dw-t"+ cssArtclThread + cssDummy +" dw-depth-0 dw-hor"}>
      {
        val renderedRoot = postRenderer.renderPost(rootPost.id)
        renderedRoot.headAndBodyHtml ++
        ifThen(showComments, {
          <div class='dw-t-vspace'/>
          <ol class='dw-res'>
            { renderedRoot.actionsHtml.copy(label = "li") }
            { renderThreads(1, rootPostsReplies, parentHorizontal = true) }
          </ol>
        })
      }
      </div>

    bodyAndComments
  }


  private def _sortPostsDescFitness(posts: List[Post]): List[Post] = {
    // Sort by: 1) Fixed position, 2) deleted? (see below)
    // 3) fitness, descending, 4) time, ascending.
    // Concerning deleted posts: Place them last, since they're rather
    // uninteresting. Sort by num replies (to the deleted comment).
    // COULD sort by *subthread* ratings instead (but not by rating of
    // deleted comment, since it's not visible).
    // Could skip sorting inline posts, since sorted by position later
    // anyway, in javascript. But if javascript disabled?
    posts.sortBy(p => p.creationDati.getTime) // the oldest first
      .sortBy(p => -pageStats.ratingStatsFor(p.id)
          .fitnessDefaultTags.lowerLimit)
      .sortBy(p => if (p.isDeletedSomehow) -p.replyCount else Int.MinValue)
      // .sortBy(p => p.meta.fixedPos.getOrElse(Int.MaxValue))
  }


  private def renderThreads(
    depth: Int,
    posts: List[Post],
    parentHorizontal: Boolean,
    uncollapseFirst: Boolean = false): NodeSeq = {

    // COULD let this function return Nil if posts.isEmpty, and otherwise
    // wrap any posts in <ol>:s, with .dw-ts or .dw-i-ts CSS classes
    // — this would reduce dupl wrapping code.

    // COULD change this to a bredth first search for the 100 most interesting
    // comments, and rename this function to `findSomeInterestingComments'.

    var threads: NodeSeq = Nil
    for {
      post <- _sortPostsDescFitness(posts)
      if !post.isTreeDeleted
      if !(post.isPostDeleted && post.replies.isEmpty)
    } {
      val thread = renderThread(post, depth, parentHorizontal, uncollapseFirst)
      threads ++= thread
    }

    threads
  }


  private def renderThread(post: Post, depth: Int, parentHorizontal: Boolean,
      uncollapseFirst: Boolean) = {

    val cssThreadId = "dw-t-"+ post.id
    val cssDepth = "dw-depth-"+ depth
    val isInlineThread = post.where.isDefined
    val isInlineNonRootChild = isInlineThread && depth >= 2
    val cssInlineThread = if (isInlineThread) " dw-i-t" else ""
    val replies =
      if (showUnapproved) post.replies
      else post.replies.filter(_.someVersionApproved)
    val isTitle = post.id == PageParts.TitleId
    val isRootOrArtclQstn =
          post.id == pageRoot.subId // || post.meta.isArticleQuestion

    // Layout replies horizontally, if this is an inline reply to
    // the root post, i.e. depth is 1 -- because then there's unused space
    // to the right. However, the horizontal layout results in a higher
    // thread if there's only one reply. So only do this if there's more
    // than one reply.
    val horizontal = (post.where.isDefined && depth == 1 && replies.length > 1) ||
                    isRootOrArtclQstn
    val cssThreadDeleted = if (post.isTreeDeleted) " dw-t-dl" else ""
    val cssArticleQuestion = if (isRootOrArtclQstn) " dw-p-art-qst" else ""
    val postFitness = pageStats.ratingStatsFor(post.id).fitnessDefaultTags
      // For now: If with a probability of 90%, most people find this post
      // boring/faulty/off-topic, and if, on average,
      // more than two out of three people think so too, then fold it.
      // Also fold inline threads (except for root post inline replies)
      // -- they confuse people (my father), in their current shape.

    val renderedComment: RenderedPost =
      postRenderer.renderPost(post.id, uncollapse = uncollapseFirst)

    val (myActionsIfHorizontalLayout, myActionsIfVerticalLayout) =
      if (isTitle) {
        (Nil, Nil)
      } else if (horizontal) {
        // Change from <div> to <li>.
        (renderedComment.actionsHtml.copy(label = "li"), Nil)
      } else {
        (Nil, renderedComment.actionsHtml)
      }

    val (cssFolded, foldLinkText) = {
      val shallFoldPost =
        !uncollapseFirst && (
        (postFitness.upperLimit < 0.5f && postFitness.observedMean < 0.333f) ||
          isInlineNonRootChild || post.isTreeCollapsed)

      if (shallFoldPost)
        (" dw-zd", "Click to show this thread" +
           renderedComment.topRatingsText.map(", rated "+ _).getOrElse(""))
      else
        ("", "")
    }

    val foldLink =
      if (isTitle || isRootOrArtclQstn || post.isTreeDeleted) Nil
      else <a class='dw-z'>{foldLinkText}</a>

    val repliesHtml = {
      def shallHideReplies = post.isTreeDeleted ||
        (!uncollapseFirst && post.isTreeCollapsed)
      if (replies.isEmpty && myActionsIfHorizontalLayout.isEmpty) Nil
      else if (shallHideReplies) Nil
      // else if the-computer-thinks-the-comment-is-off-topic-and-that-
      // -replies-therefore-should-be-hidden,
      // then: renderCollapsedReplies(replies)
      else <ol class='dw-res'>
        { myActionsIfHorizontalLayout }
        { renderThreads(depth + 1, replies, parentHorizontal = horizontal) }
      </ol>
    }

    var thread = {
      val cssHoriz = if (horizontal) " dw-hor" else ""
      <li id={cssThreadId} class={"dw-t "+ cssDepth + cssInlineThread +
             cssFolded + cssHoriz + cssThreadDeleted + cssArticleQuestion}>{
        foldLink ++
        renderedComment.headAndBodyHtml ++
        myActionsIfVerticalLayout ++
        repliesHtml
      }</li>
    }

    if (parentHorizontal) {
      // Make this thread resizable, eastwards, by wrapping it in a <div>.
      // The <li> has display: table-cell and cannot be resized, so we'll
      // resize the <div> instead.
      thread = thread.copy(label = "div")
      thread = <li>{ thread }</li>
    }
    else if (isTitle) {
      // The title isn't placed in any list.
      thread = thread.copy(label = "div")
    }

    // For inline comments, add info on where to place them.
    // COULD rename attr to data-where, that's search/replace:able enough.
    if (post.where isDefined) thread = thread % Attribute(
      None, "data-dw-i-t-where", Text(post.where.get), scala.xml.Null)

    thread
  }


  private def renderCollapsedReplies(posts: List[Post]): NodeSeq = {
    <ol class="dw-res dw-zd">{/* COULD rename dw-res to dw-ts, "threads"/"tree" */}
      <li><a class="dw-z">Click to show {posts.length} threads</a></li>
    </ol>
  }

}



object UserHtml {

  def renderInbox(notfs: Seq[NotfOfPageAction]): NodeSeq = {
    if (notfs.isEmpty)
      return  <div class='dw-ibx'><div class='dw-ibx-ttl'/></div>;

    <div class='dw-ibx'>
      <div class='dw-ibx-ttl'>Your inbox:</div>
      <ol> {
        for (notf <- notfs.take(20)) yield {
          val pageAddr = "/-"+ notf.pageId
          val postAddr = pageAddr +"#post-"+ notf.recipientActionId

          notf.eventType match {
            case NotfOfPageAction.Type.PersonalReply =>
              // COULD look up address in PATHS table when loading
              // InboxItem from database -- to get rid of 1 unnecessary redirect.
              <li><a href={postAddr}>1 reply on {notf.pageTitle}</a>,
                by <em>{notf.eventUserDispName}</em>
              </li>

            case _ =>
              // I won't forget to fix this later, when I add more notf types.
              <li><a href={postAddr}>1 something on {notf.pageTitle}</a>,
                by <em>{notf.eventUserDispName}</em>
              </li>
          }
        }
      }
      </ol>
    </div>
  }
}


