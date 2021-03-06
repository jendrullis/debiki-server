/**
 * Copyright (C) 2011-2013 Kaj Magnus Lindberg (born 1979)
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

package com.debiki.v0.tck

import scala.collection.{mutable => mut}
import com.debiki.v0
import com.debiki.v0._
import com.debiki.v0.Prelude._
import org.specs2.mutable._
import java.{util => ju}
import DebikiSpecs._
import DbDaoTckTest._
import v0.DbDao._
import v0.{PostActionPayload => PAP}
import PageParts.UnassignedId
import PageParts.UnassignedId2
import PageParts.UnassignedId3
import PageParts.UnassignedId4


/*

======================================
 Technology Compatibility Kit (TCK)
======================================


1. Dependent project configuration requirement:

By naming specs ChildSpec and including in debiki-dao-pgsqlq config file this snippet:

testOptions := Seq(Tests.Filter(s =>
  s.endsWith("Spec") && !s.endsWith("ChildSpec")))

we're telling Specs2 not to instantiate and run the ChildSpec:s many times.
(So I've added that snippet to build.sbt in debiki-dao-pgsql, for now.
I don't know how to place it here in debiki-tck-dao have it affect *dependent*
projects.


2. Test design, including tests of upgrades:

For each released database structure version:
  1. execute the test suite (for that old version)
  2. upgrade to the most recent version
  3. execute the test suite (for the most recent version)
      revert the upgrade (e.g. revert to a restore point) so the schema
      remains unchanged, although an upgrade was tested.

The test suite for a certain version requires two schemas:
  - One completely empty
  - One with fairly much contents (for performance tests)

Could test:
   Many server instances (i.e. many DAOs) access the database at the
   same time, only one DAO should do the upgrade.

*/


trait TestContext {
  def dbDaoFactory: DbDaoFactory
  def quotaManager: QuotaCharger
  // def close() // = daoFactory.systemDbDao.close()
}



trait TestContextBuilder {
  def buildTestContext(what: DbDaoTckTest.What, schemaVersion: String)
        : TestContext
}



object DbDaoTckTest {
  sealed class What
  case object EmptySchema extends What
  case object EmptyTables extends What
  case object TablesWithData extends What
}



abstract class DbDaoTckTest(builder: TestContextBuilder)
    extends Specification {

  sequential

  inline({
      // Need to empty the schema automatically before enabling this test?
      //new DaoSpecEmptySchema(builder),
      new DbDaoV002ChildSpec(builder)})
}



abstract class DbDaoChildSpec(
  builder: TestContextBuilder,
  val defSchemaVersion: String)
  extends Specification {

  sequential

  // Inited in setup() below and closed in SpecContext below, after each test.
  var ctx: TestContext = _

  def newTenantDbDao(quotaConsumers: QuotaConsumers) =
    ctx.dbDaoFactory.newTenantDbDao(quotaConsumers)

  def systemDbDao = ctx.dbDaoFactory.systemDbDao

  def now = new ju.Date

  // -------------
  // I've replaced this with `step`s in DaoSpecV002 below (but they don't work though).
  // -------------
  // "SUS" means Systems under specification, which is a
  // "The system" should { ...examples... } block.
  // See: <http://code.google.com/p/specs/wiki/DeclareSpecifications
  //        #Systems_under_specification>
  //def setup(what: What, version: String = defSchemaVersion) = new Context {
  //  beforeSus({
  //    ctx = builder(what, version)
  //  })
  //}

  // close the dao and any db connections after each tests.
  // see: <http://code.google.com/p/specs/wiki/declarespecifications
  //                #specification_context>
  // (without `close()', the specs test framework says:
  // "[error] could not run test com.debiki.v0.oracledaotcktest:
  // org.specs.specification.pathexception: treepath(list(0, 0, 1))
  // not found for <the test name>")
  //new SpecContext {
  //  afterSus({
  //    if (ctx ne null) ctx.close()
  //  })
  //}
  // -------------
  // -------------

  //object SLog extends org.specs.log.ConsoleLog  // logs nothing! why?

}


/*
class DaoSpecEmptySchema(b: TestContextBuilder) extends DbDaoChildSpec(b, "0") {
  val schemaIsEmpty = setup(EmptySchema)

  "A v0.DAO in a completely empty repo" when schemaIsEmpty should {
    "consider the version being 0" in {
      systemDbDao.checkRepoVersion() must_== Some("0")
    }
    "be able to upgrade to 0.0.2" in {
      // dao.upgrade()  currently done automatically, but ought not to.
      systemDbDao.checkRepoVersion() must_== Some("0.0.2")
    }
  }
} */



object Templates {
  val login = v0.Login(id = "?", prevLoginId = None, ip = "1.1.1.1",
    date = new ju.Date, identityId = "?i")
  val identitySimple = v0.IdentitySimple(id = "?i", userId = "?",
    name = "Målligan", email = "no@email.no", location = "", website = "")
  val identityOpenId = v0.IdentityOpenId(id = "?i", userId = "?",
    oidEndpoint = "provider.com/endpoint", oidVersion = "2",
    oidRealm = "example.com", oidClaimedId = "claimed-id.com",
    oidOpLocalId = "provider.com/local/id",
    firstName = "Laban", email = "oid@email.hmm", country = "Sweden")
  val post = PostActionDto.forNewPost(id = UnassignedId, creationDati = new ju.Date,
    loginId = "?", userId = "?", newIp = None,  parentPostId = PageParts.BodyId,
    text = "", markup = "para", approval = None)
  val rating = v0.Rating(id = UnassignedId, postId = PageParts.BodyId, loginId = "?",
    userId = "?", newIp = None, ctime = new ju.Date, tags = Nil)
}

class DbDaoV002ChildSpec(testContextBuilder: TestContextBuilder)
    extends DbDaoChildSpec(testContextBuilder, "0.0.2") {

  sequential  // so e.g. loginId inited before used in ctors

  import com.debiki.v0._
  val T = Templates

  step {
    ctx = testContextBuilder.buildTestContext(EmptyTables, defSchemaVersion)
  }

  "A v0.DAO in an empty 0.0.2 repo" should {
    "find version 0.0.2" in {
      systemDbDao.checkRepoVersion() must_== Some("0.0.2")
    }
  }

  step {
    ctx = testContextBuilder.buildTestContext(EmptyTables, defSchemaVersion)
  }

  // COULD split into: 3 tests:
  // Login tests: IdentitySimle, IdentityOpenId.
  // Page tests: create page, reply, update.
  // Path tests: lookup GUID, handle missing/superfluous slash.

  "A v0.DAO in an empty 0.0.2 repo" can {

    //sequential  // so e.g. loginId inited before used in ctors
    // Should be placed at start of Spec only?

    lazy val ex1_postText = "postText0-3kcvxts34wr"
    var testPage: PageNoPath = null
    var loginGrant: LoginGrant = null

    // Why is this needed? There's a `step` above that does this and it should
    // be executed befor the tests below!
    ctx = testContextBuilder.buildTestContext(EmptyTables, defSchemaVersion)


    // -------- Create tenant

    var defaultTenantId = ""

    "find no tenant for non-existing host test.ex.com" in {
      val lookup = systemDbDao.lookupTenant("http", "test.ex.com")
      lookup must_== FoundNothing
    }

    "find no tenant for non-existing tenant id" in {
      systemDbDao.loadTenants("non_existing_id"::Nil) must_== Nil
    }

    "create a test site (that we'll use hereafter)" in {
      val tenant = systemDbDao.createFirstSite(new FirstSiteData {
        val name = "Test"
        val address = "test.ex.com"
        val https = TenantHost.HttpsNone
        val pagesToCreate = Nil
      })
      tenant.name must_== "Test"
      tenant.id must_!= ""
      defaultTenantId = tenant.id
    }

    lazy val dao = newTenantDbDao(v0.QuotaConsumers(tenantId = defaultTenantId))

    "lookup test tenant host test.ex.com" in {
      val lookup = systemDbDao.lookupTenant("http", "test.ex.com")
      lookup must_== FoundChost(defaultTenantId)
      val lookup2 = dao.lookupOtherTenant("http", "test.ex.com")
      lookup2 must_== FoundChost(defaultTenantId)
    }

    "lookup tenant by id, and find all hosts" in {
      val tenants = systemDbDao.loadTenants(defaultTenantId::Nil)
      tenants must beLike {
        case List(tenant) =>
          tenant.id must_== defaultTenantId
          tenant.name must_== "Test"
          tenant.hosts must_== List(TenantHost(
             "test.ex.com", TenantHost.RoleCanonical, TenantHost.HttpsNone))
        case x => failure(s"Found wrong tenants: $x")
      }
    }

    lazy val defaultPagePath = v0.PagePath(defaultTenantId, "/folder/",
                                    None, false, "page-title")


    // -------- Simple logins

    "throw error for an invalid login id" in {
      val debateBadLogin = PageParts(guid = "?", actionDtos =
          PostActionDto.copyCreatePost(T.post, id = PageParts.BodyId,
            loginId = "99999", userId = "99999")::Nil) // bad ids
      //SLog.info("Expecting ORA-02291: integrity constraint log message ------")
      dao.createPage(Page.newPage(
        PageRole.Generic, defaultPagePath, debateBadLogin, author = SystemUser.User)
                    ) must throwAn[Exception]
      //SLog.info("------------------------------------------------------------")
    }

    "list no users when there are none" in {
      dao.listUsers(UserQuery()) must_== Nil
    }

    "save an IdentitySimple login" in {
      val loginReq = LoginRequest(T.login, T.identitySimple)
      loginGrant = dao.saveLogin(loginReq)
      loginGrant.login.id must_!= "?"
      loginGrant.user must matchUser(
          displayName = "Målligan", email = "no@email.no")
      loginGrant.user.id must startWith("-") // dummy user ids start with -
    }

    "list simple user" in {
      dao.listUsers(UserQuery()) must_== List((loginGrant.user, List("Guest")))
    }

    lazy val loginId = loginGrant.login.id
    lazy val globalUserId = loginGrant.user.id

    "reuse the IdentitySimple and User" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
                            T.identitySimple)  // same identity
      var grant = dao.saveLogin(loginReq)
      grant.login.id must_!= loginGrant.login.id  // new login id
      grant.identity must_== loginGrant.identity  // same identity
      grant.user must matchUser(loginGrant.user)  // same user
    }

    "create a new dummy User for an IdentitySimple with different website" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
          T.identitySimple.copy(website = "weirdplace"))
      var grant = dao.saveLogin(loginReq)
      grant.login.id must_!= loginGrant.login.id  // new login id
      // New identity because website changed.  COULD: create matchIdentity()
      val si = grant.identity.asInstanceOf[IdentitySimple]
      si.id must_!= loginGrant.identity.id
      si.name must_== "Målligan"
      si.website must_== "weirdplace"
      // New user too. A new dummy user is created for each IdentitySimple.
      grant.user.id must_!= loginGrant.user.id
      grant.user must matchUser(loginGrant.user, id = grant.user.id,
                                website = "weirdplace")
    }

    //"have exactly one user" in {  // no, 2??
    //}

    "create a new User for an IdentitySimple with different email" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
        T.identitySimple.copy(email = "other@email.yes"))
      var grant = dao.saveLogin(loginReq)
      grant.login.id must_!= loginGrant.login.id  // new login id
      // New identity because email changed.
      val si = grant.identity.asInstanceOf[IdentitySimple]
      si.id must_!= loginGrant.identity.id
      si.name must_== "Målligan"
      si.email must_== "other@email.yes"
      // New user because email has changed.
      // (For an IdentitySimple, email+name identifies the user.)
      grant.user.id must_!= loginGrant.user.id
      grant.user must matchUser(loginGrant.user, id = grant.user.id,
                                email = "other@email.yes")
     }

    "create a new User for an IdentitySimple with different name" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
        T.identitySimple.copy(name = "Spöket Laban"))
      var grant = dao.saveLogin(loginReq)
      grant.login.id must_!= loginGrant.login.id  // new login id
      // New identity because name changed.
      val si = grant.identity.asInstanceOf[IdentitySimple]
      si.id must_!= loginGrant.identity.id
      si.name must_== "Spöket Laban"
      si.email must_== "no@email.no"
      // New user because email has changed.
      // (For an IdentitySimple, email+name identifies the user.)
      grant.user.id must_!= loginGrant.user.id
      //grant.user must matchUser(loginGrant.user, id = grant.user.id,
      // why this ok?
      grant.user must matchUser(loginGrant.user, id = grant.user.id,
        //email = "other@email.yes")
        displayName = "Spöket Laban")
    }

    // "create a new User for an IdentitySimple, for another tenant" in {
    // }


    // -------- List no pages

    "list no pages, if there are none" in {
      val pagePathsDetails = dao.listPagePaths(
        PathRanges(trees = Seq("/")),  // all pages
        include = v0.PageStatus.All,
        sortBy = v0.PageSortOrder.ByPath,
        limit = Int.MaxValue,
        offset = 0
      )
      pagePathsDetails.length must_== 0
    }


    "list no posts, when there are none" in {
      dao.loadPostsRecentlyActive(limit = 10, offset = 0)._1 must beEmpty
    }


    // -------- Page creation

    lazy val ex1_rootPost = PostActionDto.copyCreatePost(T.post,
      id = PageParts.BodyId, loginId = loginId, userId = globalUserId, text = ex1_postText)

    "create a debate with a root post" in {
      val debateNoId = PageParts(guid = "?", actionDtos = ex1_rootPost::Nil)
      val page = dao.createPage(Page.newPage(
        PageRole.Generic, defaultPagePath, debateNoId, publishDirectly = true,
        author = loginGrant.user))
      val actions = page.parts
      testPage = PageNoPath(page.parts, page.meta)
      actions.postCount must_== 1
      actions.guid.length must be_>(1)  // not = '?'
      actions must havePostLike(ex1_rootPost)
      page.meta.cachedAuthorDispName must_== loginGrant.user.displayName
      page.meta.cachedAuthorUserId must_== loginGrant.user.id
      page.meta.cachedNumPosters must_== 0
      page.meta.cachedNumActions must_== 1
      page.meta.cachedNumPostsDeleted must_== 0
      page.meta.cachedNumRepliesVisible must_== 0
      page.meta.cachedNumPostsToReview must_== 1
      page.meta.cachedNumChildPages must_== 0
      page.meta.cachedLastVisiblePostDati must_== None
    }

    "find the debate and the post again" in {
      dao.loadPage(testPage.id) must beLike {
        case Some(d: PageParts) => {
          d must havePostLike(ex1_rootPost)
        }
      }
    }

    "find the debate and the login and user again" in {
      dao.loadPage(testPage.id) must beLike {
        case Some(d: PageParts) => {
          d.people.nilo(ex1_rootPost.loginId) must beLike {
            case Some(n: NiLo) =>  // COULD make separate NiLo test?
              n.login.id must_== ex1_rootPost.loginId
              n.login.identityId must_== n.identity_!.id
              n.identity_!.userId must_== n.user_!.id
              n.user_! must matchUser(displayName = "Målligan",
                                      email = "no@email.no")
          }
        }
      }
    }


    // -------- List one page

    "list the recently created page" in {
      val pagePathsDetails = dao.listPagePaths(
        PathRanges(trees = Seq("/")),
        include = v0.PageStatus.All,
        sortBy = v0.PageSortOrder.ByPath,
        limit = Int.MaxValue,
        offset = 0
      )
      pagePathsDetails must beLike {
        case List((pagePath, pageDetails)) =>
          pagePath must_== defaultPagePath.copy(pageId = pagePath.pageId)
          // When I've implemented Draft/Published status, Draft will be
          // the default:
          pageDetails.status must_== PageStatus.Published
          // There page currently has no title.
          // It's published by default though.
          pageDetails.cachedTitle must_== None
          pageDetails.pubDati must_!= None
          // Shouldn't the page body post affect the
          // significant-modification-time?
          // pageDetails.sgfntModDati must_== None  -- or Some(date)?
      }
    }

    "list nothing for an empty list" in {
      val pathsAndPages = dao.loadPageBodiesTitles(Nil)
      pathsAndPages must beEmpty
    }

    "list no body and title, for a non-existing page" in {
      dao.loadPageBodiesTitles("nonexistingpage"::Nil) must beEmpty
    }

    "list body and title, for a page that exists" in {
      val pathAndDetails = dao.listPagePaths(
        PathRanges(trees = Seq("/")),
        include = v0.PageStatus.All,
        sortBy = v0.PageSortOrder.ByPath,
        limit = Int.MaxValue,
        offset = 0)
      pathAndDetails.length must be_>=(1)
      val path: PagePath = pathAndDetails.head._1

      val pathsAndPages = dao.loadPageBodiesTitles(path.pageId.get::Nil)
      pathsAndPages.size must_== 1
      pathsAndPages.get(path.pageId.get) must beLike { case Some(page) =>
        val bodyText = page.body.map(_.currentText)
        bodyText must beSome
        bodyText.get.length must be_>(0)
        page.body_!.user must beSome

        /* Currently there is no title for the test page.
        page.title must beSome
        page.titleText.get.length must be_>(0)
        page.title_!.user must beSome
        */
      }
    }


    "list the posts, it's recently active " in {
      dao.loadPostsRecentlyActive(limit = 10, offset = 0)._1 must beLike {
        case List(post: Post) =>
          post.id must_== ex1_rootPost.id
      }
    }


    // -------- Page meta info

    "create, load and save meta info" >> {

      var blogMainPageId = "?"
      var blogArticleId = "?"

      "create a Blog" in {
        val pageNoId = Page(
          PageMeta.forNewPage(PageRole.Blog, loginGrant.user, PageParts("?"), now),
          defaultPagePath.copy(
            showId = true, pageSlug = "role-test-blog-main"),
          PageParts(guid = "?"))

        pageNoId.meta.pageExists must_== false
        val page = dao.createPage(pageNoId)

        page.meta.pageExists must_== true
        page.meta.pageRole must_== PageRole.Blog
        page.meta.parentPageId must_== None
        page.meta.pubDati must_== None

        val actions = page.parts
        blogMainPageId = actions.pageId
        actions.postCount must_== 0
        actions.pageId.length must be_>(1)  // not = '?'
      }

      "look up meta info for the Blog, find no child pages" in {
        dao.loadPageMeta(blogMainPageId) must beLike {
          case Some(pageMeta: PageMeta) => {
            pageMeta.pageExists must_== true
            pageMeta.pageRole must_== PageRole.Blog
            pageMeta.parentPageId must_== None
            pageMeta.pageId must_== blogMainPageId
            pageMeta.pubDati must_== None
            pageMeta.cachedNumChildPages must_== 0
          }
        }
      }

      "create a child BlogPost" in {
        val pageNoId = Page(
          PageMeta.forNewPage(PageRole.BlogPost, loginGrant.user, PageParts("?"), now,
            parentPageId = Some(blogMainPageId)),
          defaultPagePath.copy(
            showId = true, pageSlug = "role-test-blog-article"),
          PageParts(guid = "?"))
        val page = dao.createPage(pageNoId)
        val actions = page.parts
        blogArticleId = actions.pageId
        actions.postCount must_== 0
        actions.pageId.length must be_>(1)  // not = '?'
      }

      "look up meta info for the Blog again, find one child page" in {
        dao.loadPageMeta(blogMainPageId) must beLike {
          case Some(pageMeta: PageMeta) => {
            pageMeta.pageId must_== blogMainPageId
            pageMeta.cachedNumChildPages must_== 1
          }
        }
      }

      "look up meta info for the BlogPost" in {
        dao.loadPageMeta(blogArticleId) must beLike {
          case Some(pageMeta: PageMeta) => {
            pageMeta.pageRole must_== PageRole.BlogPost
            pageMeta.parentPageId must_== Some(blogMainPageId)
            pageMeta.pageId must_== blogArticleId
            pageMeta.pubDati must_== None
          }
        }
      }

      "find no child pages of a non-existing page" in {
        val childs = dao.listChildPages("doesNotExist",
          PageSortOrder.ByPublTime, limit = 10)
        childs.length must_== 0
      }

      "find no child pages of a page with no children" in {
        val childs = dao.listChildPages(blogArticleId,
          PageSortOrder.ByPublTime, limit = 10)
        childs.length must_== 0
      }

      def testBlogArticleMeta(meta: PageMeta) = {
        meta.pageId must_== blogArticleId
        meta.pageRole must_== PageRole.BlogPost
        meta.parentPageId must_== Some(blogMainPageId)
      }

      def testFoundChild(childs: Seq[(PagePath, PageMeta)]) {
        childs.length must_== 1
        childs must beLike {
          case List((pagePath, pageMeta)) =>
            pagePath.pageId must_== Some(blogArticleId)
            testBlogArticleMeta(pageMeta)
        }
      }

      "find child pages of the Blog" in {
        val childs = dao.listChildPages(blogMainPageId,
          PageSortOrder.ByPublTime, limit = 10)
        testFoundChild(childs)
      }

      "find child pages also when page role specified" in {
        val childs = dao.listChildPages(blogMainPageId,
          PageSortOrder.ByPublTime, limit = 10,
          filterPageRole = Some(PageRole.BlogPost))
        testFoundChild(childs)
      }

      "find no child pages of the wrong page role" in {
        val childs = dao.listChildPages(blogMainPageId,
          PageSortOrder.ByPublTime, limit = 10,
          filterPageRole = Some(PageRole.ForumTopic))
        childs.length must_== 0
      }

      "can update meta info, and parent page child count"  in {
        val blogArticleMeta = dao.loadPageMeta(blogArticleId) match {
          case Some(pageMeta: PageMeta) =>
            testBlogArticleMeta(pageMeta) // extra test
            pageMeta
          case x => failure(s"Bad meta: $x")
        }
        // Edit meta (but not page role, cannot be changed)
        val nextDay = new ju.Date(
          blogArticleMeta.modDati.getTime + 1000 * 3600 * 24)
        val newMeta = blogArticleMeta.copy(
          parentPageId = None,
          modDati = nextDay,
          pubDati = Some(nextDay),
          // Use stupid incorrect values here, just for testing.
          cachedTitle = Some("NewCachedPageTitle"),
          cachedAuthorDispName = "cachedAuthorDispName",
          cachedAuthorUserId = "cachedAuthorUserId",
          cachedNumPosters = 11,
          cachedNumActions = 12,
          cachedNumPostsToReview = 13,
          cachedNumPostsDeleted = 14,
          cachedNumRepliesVisible = 15,
          cachedLastVisiblePostDati = Some(new ju.Date(12345)),
          cachedNumChildPages = 17)

        dao.updatePageMeta(newMeta, old = blogArticleMeta)

        // Reload and test
        dao.loadPageMeta(blogArticleId) must beLike {
          case Some(meta2: PageMeta) =>
            meta2 must_== newMeta
        }

        // The former parent page's meta should also have changed:
        dao.loadPageMeta(blogMainPageId) must beLike {
          case Some(pageMeta: PageMeta) => {
            pageMeta.pageId must_== blogMainPageId
            pageMeta.cachedNumChildPages must_== 0
          }
        }

        // Change parent back to the old one.
        val newMetaOldParent = newMeta.copy(parentPageId = blogArticleMeta.parentPageId)
        dao.updatePageMeta(newMetaOldParent, old = newMeta)

        // Now the former parent page's child count should be 1 again.
        dao.loadPageMeta(blogMainPageId) must beLike {
          case Some(pageMeta: PageMeta) => {
            pageMeta.pageId must_== blogMainPageId
            pageMeta.cachedNumChildPages must_== 1
          }
        }
      }

      "cannot change page role" in {
        val blogArticleMeta = dao.loadPageMeta(blogArticleId) match {
          case Some(pageMeta: PageMeta) => pageMeta
          case x => failure(s"Bad meta: $x")
        }
        val newMeta = blogArticleMeta.copy(pageRole = PageRole.Forum)
        dao.updatePageMeta(
          newMeta, old = blogArticleMeta) must throwA[PageNotFoundByIdAndRoleException]
      }

    }


    // -------- Forums

    "create forums and topics" >> {

      var forum: Page = null
      var topic: Page = null
      var forumGroup: Page = null

      def forumStuff(
            pageRole: PageRole,
            parentPageId: Option[String] = None,
            pageSlug: String = "forum-or-topic",
            showId: Boolean = true): Page =
        Page(
          PageMeta.forNewPage(pageRole, loginGrant.user, PageParts("?"), now,
            parentPageId = parentPageId),
          defaultPagePath.copy(folder = "/forum/", showId = showId, pageSlug = pageSlug),
          PageParts(guid = "?"))

      "create a forum" in {
        val forumNoId = forumStuff(PageRole.Forum, pageSlug = "", showId = false)
        forum = dao.createPage(forumNoId)
        ok
      }

      "not create a forum in the forum" in {
        val subforumNoId = forumStuff(PageRole.Forum, Some(forum.id))
        dao.createPage(subforumNoId) must throwAn[Exception]
      }

      "not create a forum group in the forum" in {
        val groupNoId = forumStuff(PageRole.ForumGroup, Some(forum.id))
        dao.createPage(groupNoId) must throwAn[Exception]
      }

      "create a topic in the forum" in {
        val topicNoId = forumStuff(PageRole.ForumTopic, Some(forum.id))
        topic = dao.createPage(topicNoId)
        ok
      }

      "not create a topic in a topic" in {
        val topicInTopic = forumStuff(PageRole.ForumTopic, Some(topic.id))
        dao.createPage(topicInTopic) must throwAn[Exception]
      }

      "not create a forum in a topic" in {
        val forumInTopic = forumStuff(PageRole.Forum, Some(topic.id))
        dao.createPage(forumInTopic) must throwAn[Exception]
      }

      "not create a forum group in a topic" in {
        val groupInTopic = forumStuff(PageRole.ForumGroup, Some(topic.id))
        dao.createPage(groupInTopic) must throwAn[Exception]
      }

      "create a forum group P, place the original forum inside" in {
        failure
      }.pendingUntilFixed()

      "not create a topic in a forum group" in {
        failure
      }.pendingUntilFixed()

      "create a forum group C, place in forum group P" in {
        failure
      }.pendingUntilFixed()

      "create a forum, place in forum group C" in {
        failure
      }.pendingUntilFixed()

      "find the forum, topic and forum group" in {
        dao.loadPageMeta(forum.id) must beLike {
          case Some(pageMeta) =>
            pageMeta.pageRole must_== PageRole.Forum
        }

        dao.loadPageMeta(topic.id) must beLike {
          case Some(pageMeta) =>
            pageMeta.pageRole must_== PageRole.ForumTopic
        }

        /* dao.loadPageMeta(forumGroup.id) must beLike {
          case Some(pageMeta) =>
            pageMeta.pageRole must_== PageRole.ForumGroup
        } */
      }
    }


    // -------- Paths

    // COULD: Find the Identity again, and the User.

    lazy val exPagePath = defaultPagePath.copy(pageId = Some(testPage.id))
    "recognize its correct PagePath" in {
      dao.checkPagePath(exPagePath) must beLike {
        case Some(correctPath: PagePath) =>
          correctPath must matchPagePath(exPagePath)
        case p => failure(s"Bad path: $p")
      }
    }

    "correct an incorrect PagePath name" in {
      dao.checkPagePath(exPagePath.copy(pageSlug = "incorrect")) must beLike {
        case Some(correctPath: PagePath) =>
          correctPath must matchPagePath(exPagePath)
        case p => failure(s"Bad path: $p")
      }
    }

    "correct an incorrect PagePath folder" in {
      dao.checkPagePath(exPagePath.copy(folder = "/incorrect/")) must beLike {
        case Some(correctPath: PagePath) =>
          correctPath must matchPagePath(exPagePath)
        case p => failure(s"Bad path: $p")
      }
    }

    //"remove a superfluous slash in a no-guid path" in {
    //}

    //"add a missing slash to a folder index" in {
    //}

    // -------- Page actions

    lazy val ex2_emptyPost = PostActionDto.copyCreatePost(T.post,
      parentPostId = PageParts.BodyId, text = "", loginId = loginId, userId = globalUserId)
    var ex2_id = PageParts.NoId
    "save an empty root post child post" in {
      testPage += loginGrant.user
      dao.savePageActions(testPage, List(ex2_emptyPost)) must beLike {
        case (_, List(p: PostActionDto[PAP.CreatePost])) =>
          ex2_id = p.id
          p must matchPost(ex2_emptyPost, id = ex2_id)
      }
    }

    "find the empty post again" in {
      dao.loadPage(testPage.id) must beLike {
        case Some(d: PageParts) => {
          d must havePostLike(ex2_emptyPost, id = ex2_id)
        }
      }
    }

    var ex3_ratingId = PageParts.NoId
    lazy val ex3_rating = T.rating.copy(loginId = loginId, userId = globalUserId,
      postId = PageParts.BodyId,  tags = "Interesting"::"Funny"::Nil)  // 2 tags
    "save a post rating, with 2 tags" in {
      dao.savePageActions(testPage, List(ex3_rating)) must beLike {
        case (_, List(r: Rating)) =>
          ex3_ratingId = r.id
          r must matchRating(ex3_rating, id = ex3_ratingId,
            loginId = loginId, userId = globalUserId)
      }
    }

    "find the rating again" in {
      dao.loadPage(testPage.id) must beLike {
        case Some(d: PageParts) => {
          d must haveRatingLike(ex3_rating, id = ex3_ratingId)
        }
      }
    }

    var ex4_rating1Id = PageParts.NoId
    lazy val ex4_rating1 =
      T.rating.copy(id = UnassignedId2, postId = PageParts.BodyId, loginId = loginId,
        userId = globalUserId, tags = "Funny"::Nil)
    var ex4_rating2Id = PageParts.NoId
    lazy val ex4_rating2 =
      T.rating.copy(id = UnassignedId3, postId = PageParts.BodyId, loginId = loginId,
        userId = globalUserId, tags = "Boring"::"Stupid"::Nil)
    var ex4_rating3Id = PageParts.NoId
    lazy val ex4_rating3 =
      T.rating.copy(id = UnassignedId4, postId = PageParts.BodyId, loginId = loginId,
        userId = globalUserId, tags = "Boring"::"Stupid"::"Funny"::Nil)

    "save 3 ratings, with 1, 2 and 3 tags" in {
      dao.savePageActions(testPage,
                List(ex4_rating1, ex4_rating2, ex4_rating3)
      ) must beLike {
        case (_, List(r1: Rating, r2: Rating, r3: Rating)) =>
          ex4_rating1Id = r1.id
          r1 must matchRating(ex4_rating1, id = ex4_rating1Id)
          ex4_rating2Id = r2.id
          r2 must matchRating(ex4_rating2, id = ex4_rating2Id)
          ex4_rating3Id = r3.id
          r3 must matchRating(ex4_rating3, id = ex4_rating3Id)
      }
    }

    "find the 3 ratings again" in {
      dao.loadPage(testPage.id) must beLike {
        case Some(d: PageParts) => {
          d must haveRatingLike(ex4_rating1, id = ex4_rating1Id)
          d must haveRatingLike(ex4_rating2, id = ex4_rating2Id)
          d must haveRatingLike(ex4_rating3, id = ex4_rating3Id)
        }
      }
    }


    // -------- Save approvals and rejections

    "Save and load an approval" in {
      testSaveLoadReview(isApproved = true)
    }

    "Save and load a rejection" in {
      testSaveLoadReview(isApproved = false)
    }

    def testSaveLoadReview(isApproved: Boolean) {
      var reviewSaved: PostActionDto[PAP.ReviewPost] = null
      val approval = if (isApproved) Some(Approval.Manual) else None
      val reviewNoId = PostActionDto.toReviewPost(
         UnassignedId, postId = ex1_rootPost.id, loginId = loginId,
         userId = globalUserId, newIp = None, ctime = now, approval = approval)
      dao.savePageActions(testPage, List(reviewNoId)) must beLike {
        case (_, List(review: PostActionDto[PAP.ReviewPost])) =>
          reviewSaved = review
          review must_== reviewNoId.copy(id = review.id)
      }

      dao.loadPage(testPage.id) must beLike {
        case Some(page: PageParts) => {
          val postReviewed = page.getPost_!(reviewSaved.postId)
          postReviewed.lastReviewDati must_== Some(reviewSaved.ctime)
          postReviewed.lastReviewWasApproval must_== Some(isApproved)
        }
      }
    }


    // -------- Edit posts


    var exEdit_postId: ActionId = PageParts.NoId
    var exEdit_editId: ActionId = PageParts.NoId

    "create a post to edit" >> {
      // Make post creation action
      lazy val postNoId = PostActionDto.copyCreatePost(T.post,
        parentPostId = PageParts.BodyId, text = "Initial text",
        loginId = loginId, userId = globalUserId, markup = "dmd0")

      var post: PostActionDto[PAP.CreatePost] = null

      "save post" in {
        post = dao.savePageActions(testPage, List(postNoId))._2.head
        post.payload.text must_== "Initial text"
        post.payload.markup must_== "dmd0"
        exEdit_postId = post.id
        testPage += post
        ok
      }

      val newText = "Edited text 054F2x"

      "edit the post" in {
        // Make edit actions
        val patchText = makePatch(from = post.payload.text, to = newText)
        val editNoId = PostActionDto.toEditPost(
          id = UnassignedId, postId = post.id, ctime = now, loginId = loginId,
          userId = globalUserId,
          newIp = None, text = patchText, newMarkup = None,
          approval = None, autoApplied = false)
        val publNoId = EditApp(
          id = UnassignedId2, editId = UnassignedId, postId = post.id, ctime = now,
          loginId = loginId, userId = globalUserId, newIp = None, result = newText,
          approval = None)

        // Save
        val List(edit: PostActionDto[PAP.EditPost], publ: EditApp) =
          dao.savePageActions(testPage, List(editNoId, publNoId))._2

        exEdit_editId = edit.id

        // Verify text changed
        dao.loadPage(testPage.id) must beLike {
          case Some(d: PageParts) => {
            val editedPost = d.getPost_!(post.id)
            editedPost.currentText must_== newText
            editedPost.markup must_== "dmd0"
          }
        }
      }

      "change the markup type" in {
        // Make edit actions
        val editNoId = PostActionDto.toEditPost(
          id = UnassignedId, postId = post.id, ctime = now, loginId = loginId,
          userId = globalUserId, newIp = None, text = "", newMarkup = Some("html"),
          approval = None, autoApplied = false)
        val publNoId = EditApp(
          id = UnassignedId2, editId = UnassignedId, postId = post.id, ctime = now,
          loginId = loginId, userId = globalUserId, newIp = None, result = newText,
          approval = None)

        // Save
        val List(edit: PostActionDto[PAP.EditPost], publ: EditApp) =
          dao.savePageActions(testPage, List(editNoId, publNoId))._2

        // Verify markup type changed
        dao.loadPage(testPage.id) must beLike {
          case Some(d: PageParts) => {
            val editedPost = d.getPost_!(post.id)
            editedPost.currentText must_== "Edited text 054F2x"
            editedPost.markup must_== "html"
          }
        }
      }
    }



    // -------- Load recent actions

    "load recent actions" >> {

      lazy val badIp = Some("99.99.99.99")
      lazy val ip = Some("1.1.1.1")

      def hasLoginsIdtysAndUsers(people: People) =
        people.logins.nonEmpty && people.identities.nonEmpty &&
        people.users.nonEmpty

      "from IP, find nothing" in {
        val (actions, people) =
            dao.loadRecentActionExcerpts(fromIp = badIp, limit = 5)
        actions must beEmpty
        people must_== People.None
      }

      "from IP, find a post, and edits of that post" in {
        val (actions, people) =
           dao.loadRecentActionExcerpts(fromIp = ip, limit = 99)

        /* Not yet possible: logins etc not loaded.
        actions.length must be_>(0)
        actions foreach { action =>
          val ipMatches = action.login_!.ip == ip
          // val targetActionIpMatches = action.target.map(_.ip) == Some(ip)
          val targetActionIpMatches = true // not implemented :-(
          (ipMatches || targetActionIpMatches) must_== true
        }
        */
      }

      "from IP, find `limit`" in {
        val (actions, people) =
           dao.loadRecentActionExcerpts(fromIp = ip, limit = 2)
        //actions.length must be_>=(?)
        /*
        If `loadRecentActionExcerpts` loaded only own Post:s, and actions
        on them, e.g. this would be possible:
        val ownActions = actions filter { action =>
          action.login_!.ip == ip && action.action.isInstanceOf[Post]
        }
        ownActions.length must_== 2
        */
      }

      "by identity id, find nothing" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
           byIdentity = Some("9999999"), limit = 99)
        actions must beEmpty
        people must_== People.None
      }

      "by identity id, find ..." in {
        // Not implemented, because no OpenID identity currently does anything.
      }

      "by path, find nothing, in non existing tree and folder" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
          pathRanges = PathRanges(
            trees = Seq("/does/not/exist/"),
            folders = Seq("/neither/do/i/")),
          limit = 99)
        actions.length must_== 0
        people must_== People.None
      }

      "by path, find something, in root tree" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
          pathRanges = PathRanges(trees = Seq("/")), limit = 99)
        actions.length must be_>(0)
        hasLoginsIdtysAndUsers(people) must beTrue
      }

      "by path, find something, in /folder/" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
          pathRanges = PathRanges(folders = Seq(defaultPagePath.folder)),
          limit = 99)
        actions.length must be_>(0)
        hasLoginsIdtysAndUsers(people) must beTrue
      }

      "by page id, find nothing, non existing page" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
          pathRanges = PathRanges(pageIds = Seq("nonexistingpage")),
          limit = 99)
        actions.length must_== 0
        people must_== People.None
      }

      "by page id, find something, when page exists" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
          pathRanges = PathRanges(pageIds = Seq(testPage.id)),
          limit = 99)
        actions.length must be_>(0)
        hasLoginsIdtysAndUsers(people) must beTrue
      }

      "by page id, folder and tree, find something" in {
        val (actions, people) = dao.loadRecentActionExcerpts(
          pathRanges = PathRanges(
            pageIds = Seq(testPage.id),  // exists
            folders = Seq("/folder/"),  // there's a page in this folder
            trees = Seq("/")),  // everything
          limit = 99)
        actions.length must be_>(0)
        hasLoginsIdtysAndUsers(people) must beTrue
      }
    }



    // -------- OpenID login

    var exOpenId_loginReq: LoginGrant = null
    def exOpenId_loginGrant: LoginGrant = exOpenId_loginReq  // correct name
    var exOpenId_userIds = mut.Set[String]()
    "save a new OpenID login and create a user" in {
      val loginReq = LoginRequest(T.login, T.identityOpenId)
      exOpenId_loginReq = dao.saveLogin(loginReq)
      for (id <- exOpenId_loginReq.login.id ::
                  exOpenId_loginReq.identity.id ::
                  exOpenId_loginReq.user.id :: Nil) {
        id.contains("?") must_== false
        // weird! the compiler says: ';' expected but integer literal found
        // on the next row (if commented in).
        // id.length must be_> 1  // non-empty, and no weird 1 char id

        // Only dummy user ids (created for each IdentitySimple)
        // start with "-":
        id must not startWith("-")
      }
      exOpenId_loginReq.identity.id must_==  exOpenId_loginReq.login.identityId
      exOpenId_loginReq.user.id must_== exOpenId_loginReq.identity.userId
      exOpenId_loginReq.user must matchUser(
          displayName = T.identityOpenId.firstName,
          email = T.identityOpenId.email,
          // Country info not available for all identity types and currently
          // not copied from a new Identity to the related new User.
          country = "", // T.identityOpenId.country,
          website = "",
          isSuperAdmin = Boolean.box(false))
      exOpenId_userIds += exOpenId_loginReq.user.id
      ok
    }

    "list OpenID user" in {
      val openIdEntry = dao.listUsers(UserQuery()).find(_._2 != List("Guest"))
      openIdEntry must_== Some(
        (exOpenId_loginGrant.user, List(T.identityOpenId.oidEndpoint)))
    }

    "reuse the IdentityOpenId and User just created" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
          T.identityOpenId)
      val grant = dao.saveLogin(loginReq)
      grant.login.id must_!= exOpenId_loginReq.login.id
      grant.identity must_== exOpenId_loginReq.identity
      // The very same user should have been found.
      grant.user must matchUser(exOpenId_loginReq.user)
    }

    // COULD test to change name + email too, instead of only changing country.

    "update the IdentityOpenId, if attributes (country) changed" in {
      // Change the country attribute. The Dao should automatically save the
      // new value to the database, and use it henceforth.
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
          T.identityOpenId.copy(country = "Norway"))
      val grant = dao.saveLogin(loginReq)
      grant.login.id must_!= exOpenId_loginReq.login.id
      grant.identity must_== exOpenId_loginReq.identity.
          asInstanceOf[IdentityOpenId].copy(country = "Norway")
      // The user shouldn't have been changed, only the OpenID identity attrs.
      grant.user must matchUser(exOpenId_loginReq.user)
    }

    //"have exactly one user" in {  // or, 3? there're 2 IdentitySimple users?
    //}

    var exOpenId_loginGrant_2: LoginGrant = null

    // COULD test w/ new tenant but same claimed_ID, should also result in
    // a new User. So you can customize your user, per tenant.
    "create new IdentityOpenId and User for a new claimed_id" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
        T.identityOpenId.copy(oidClaimedId = "something.else.com"))
      val grant = dao.saveLogin(loginReq)
      grant.login.id must_!= exOpenId_loginReq.login.id
      // A new id to a new user, but otherwise identical.
      grant.user.id must_!= exOpenId_loginReq.user.id
      grant.user must matchUser(exOpenId_loginReq.user, id = grant.user.id)
      exOpenId_userIds.contains(grant.user.id) must_== false
      exOpenId_userIds += grant.user.id
      exOpenId_loginGrant_2 = grant
    }

    var exGmailLoginGrant: LoginGrant = null

    "create new IdentityOpenId and User for a new claimed_id, Gmail addr" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
        T.identityOpenId.copy(
          oidEndpoint = IdentityOpenId.GoogleEndpoint,
          oidRealm = "some.realm.com",
          oidClaimedId = "google.claimed.id",
          email = "example@gmail.com"))
      exGmailLoginGrant = dao.saveLogin(loginReq)
      val grant = exGmailLoginGrant
      exOpenId_userIds.contains(grant.user.id) must_== false
      exOpenId_userIds += grant.user.id
      ok
    }

    "lookup OpenID identity, by login id" in {
      dao.loadIdtyDetailsAndUser(
          forLoginId = exGmailLoginGrant.login.id) must beLike {
        case Some((identity, user)) =>
          identity must_== exGmailLoginGrant.identity
          user must_== exGmailLoginGrant.user
      }
    }

    "lookup OpenID identity, by claimed id" in {
      // (Use _2 because the first one has had its country modified)
      val oidSaved = exOpenId_loginGrant_2.identity.asInstanceOf[IdentityOpenId]
      val partialIdentity = oidSaved.copy(id = "?", userId = "?")
      dao.loadIdtyDetailsAndUser(forIdentity = partialIdentity) must beLike {
        case Some((identity, user)) =>
          identity must_== exOpenId_loginGrant_2.identity
          user must_== exOpenId_loginGrant_2.user
      }
    }

    "lookup OpenID identity, by email, for Gmail" in {
      val partialIdentity = IdentityOpenId(
         id = "?", userId = "?", oidEndpoint = IdentityOpenId.GoogleEndpoint,
         oidVersion = "?", oidRealm = "?", oidClaimedId = "?",
         oidOpLocalId = "?", firstName = "?", email = "example@gmail.com",
         country = "?")
      dao.loadIdtyDetailsAndUser(forIdentity = partialIdentity) must beLike {
        case Some((identity, user)) =>
          identity must_== exGmailLoginGrant.identity
          user must_== exGmailLoginGrant.user
      }
    }

    //"have exactly two users" in {  // no, 4? 2 + 2 = 4
    //}

    /*
    "create a new user, for a new tenant (but same claimed_id)" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
                              T.identityOpenId)
      val grant = dao.saveLogin("some-other-tenant-id", loginReq)
      grant.login.id must_!= exOpenId_loginReq.login.id
      grant.user must beLike {
        case None => false
        case Some(u) =>
          // A new id to a new user, but otherwise identical.
          u.id must_!= exOpenId_loginReq.user.id
          u must matchUser(exOpenId_loginReq.user, id = u.id)
          exOpenId_userIds += u.id
      }
    } */

    "load relevant OpenID logins, when loading a Page" in {
      // Save a post, using the OpenID login. Load the page and verify
      // the OpenID identity and user were loaded with the page.
      val newPost = PostActionDto.copyCreatePost(T.post,
        parentPostId = PageParts.BodyId, text = "",
        loginId = exOpenId_loginGrant.login.id,
        userId = exOpenId_loginGrant.user.id)
      var postId = PageParts.NoId
      testPage += exOpenId_loginGrant.user
      dao.savePageActions(testPage, List(newPost)) must beLike {
        case (_, List(savedPost: PostActionDto[PAP.CreatePost])) =>
          postId = savedPost.id
          savedPost must matchPost(newPost, id = postId)
      }

      dao.loadPage(testPage.id) must beLike {
        case Some(d: PageParts) =>
          d must havePostLike(newPost, id = postId)
          d.people.nilo(exOpenId_loginReq.login.id) must beLike {
            case Some(n: NiLo) =>  // COULD make separate NiLo test?
              n.login.id must_== exOpenId_loginReq.login.id
              n.login.identityId must_== n.identity_!.id
              // The OpenID country attr was changed from Sweden to Norway.
              n.identity_! must_== exOpenId_loginReq.identity.
                  asInstanceOf[IdentityOpenId].copy(country = "Norway",
                      // When a page is loaded, uninteresting OpenID details
                      // are not loaded, to save bandwidth. Instead they
                      // are set to "?".
                      oidEndpoint = "?", oidVersion = "?", oidRealm = "?",
                      oidClaimedId = "?", oidOpLocalId = "?")
              n.identity_!.asInstanceOf[IdentityOpenId].firstName must_==
                                                                      "Laban"
              n.identity_!.userId must_== n.user_!.id
              // Identity data no longer copied to User.
              //n.user_! must matchUser(displayName = "Laban",
              //                        email = "oid@email.hmm")
          }
      }
    }

    // -------- Email

    var emailEx_loginGrant: LoginGrant = null
    var emailEx_email = "Imail@ex.com"

    // Test users, *after* they've been configured to receive email.
    var emailEx_UnauUser: User = null
    var emailEx_OpenIdUser: User = null

    "by default send no email to a new IdentitySimple" in {
      val loginReq = LoginRequest(T.login.copy(date = new ju.Date),
        T.identitySimple.copy(email = emailEx_email, name = "Imail"))
      val grant = dao.saveLogin(loginReq)
      val Some((idty, user)) = dao.loadIdtyAndUser(forLoginId = grant.login.id)
      user.emailNotfPrefs must_== EmailNotfPrefs.Unspecified
      emailEx_loginGrant = grant
      ok
    }

    "configure email to IdentitySimple" in {
      def login = emailEx_loginGrant.login
      dao.configIdtySimple(loginId = login.id,
            ctime = new ju.Date, emailAddr = emailEx_email,
            emailNotfPrefs = EmailNotfPrefs.Receive)
      val Some((idty, user)) = dao.loadIdtyAndUser(
         forLoginId = emailEx_loginGrant.login.id)
      user.emailNotfPrefs must_== EmailNotfPrefs.Receive
      emailEx_UnauUser = user  // save, to other test cases
      ok
    }

    "by default send no email to a new Role" in {
      val login = exOpenId_loginGrant.login
      val Some((idty, user)) = dao.loadIdtyAndUser(forLoginId = login.id)
      user.emailNotfPrefs must_== EmailNotfPrefs.Unspecified
    }

    "configure email to a Role" in {
      // Somewhat dupl code, see `def testAdmin` test helper.
      val userToConfig = exOpenId_loginGrant.user
      val login = exOpenId_loginGrant.login
      dao.configRole(loginId = login.id,
         ctime = new ju.Date, roleId = userToConfig.id,
         emailNotfPrefs = Some(EmailNotfPrefs.Receive))
      val Some((idty, userConfigured)) =
         dao.loadIdtyAndUser(forLoginId = login.id)
      userConfigured.emailNotfPrefs must_== EmailNotfPrefs.Receive
      emailEx_OpenIdUser = userConfigured  // remember, to other test cases
      ok
    }


    // -------- Notifications and emails

    // An unauthenticated user and an authenticated user.
    // They have already been inserted in the db, and want email notfs.
    lazy val unauUser = emailEx_UnauUser
    lazy val auUser = emailEx_OpenIdUser

    // A notification to the unauthenticated user.
    lazy val unauUserNotfSaved = NotfOfPageAction(
      ctime = new ju.Date,
      recipientUserId = unauUser.id,
      pageTitle = "EventPageForUnauUser",
      pageId = testPage.id,
      eventType = NotfOfPageAction.Type.PersonalReply,
      eventActionId = ex2_id,
      triggerActionId = ex2_id,
      recipientActionId = ex2_emptyPost.payload.parentPostId,
      recipientUserDispName = "RecipientUser",
      eventUserDispName = "EventUser",
      triggerUserDispName = None,
      emailPending = true)

    // A notification to the authenticated user.
    // (Shouldn't really be possible, because now one event+recipientActionId
    // maps to 2 notfs! But after I've added a PK to DW1_NOTFS_PAGE_ACTIONS,
    // that PK will allow only one. Then I'll have to fix/improve this test
    // case.)
    lazy val auUserNotfSaved = unauUserNotfSaved.copy(
      eventActionId = exEdit_editId,
      recipientActionId = exEdit_postId,
      // eventType = should-change-from-reply-to-edit
      recipientUserId = auUser.id,  // not correct but works for this test
      pageTitle = "EventPageForAuUser")

    "load and save notifications" >> {

      "find none, when there are none" in {
        dao.loadNotfByEmailId("BadEmailId") must_== None
        dao.loadNotfsForRole(unauUser.id) must_== Nil
        dao.loadNotfsForRole(unauUser.id) must_== Nil
        val notfsLoaded = systemDbDao.loadNotfsToMailOut(
                                          delayInMinutes = 0, numToLoad = 10)
        notfsLoaded.usersByTenantAndId must_== Map.empty
        notfsLoaded.notfsByTenant must_== Map.empty
      }

      "save one, to an unauthenticated user" >> {

        "load it, by user id" in {
          dao.saveNotfs(unauUserNotfSaved::Nil)

          val notfsLoaded = dao.loadNotfsForRole(unauUser.id)
          notfsLoaded must beLike {
            case List(notfLoaded: NotfOfPageAction) =>
              notfLoaded must_== unauUserNotfSaved
          }
        }

        "load it, by time, to mail out" in {
          val notfsToMail = systemDbDao.loadNotfsToMailOut(
             delayInMinutes = 0, numToLoad = 10)
          notfsToMail.usersByTenantAndId.get((defaultTenantId, unauUser.id)
             ) must_== Some(unauUser)
          val notfsByTenant = notfsToMail.notfsByTenant(defaultTenantId)
          notfsByTenant.length must_== 1
          notfsByTenant.head must_== unauUserNotfSaved
        }
      }

      "save one, to an authenticated user" >> {

        "load it, by user id" in {
          dao.saveNotfs(auUserNotfSaved::Nil)

          val notfsLoaded = dao.loadNotfsForRole(auUser.id)
          notfsLoaded must beLike {
            case List(notfLoaded: NotfOfPageAction) =>
              notfLoaded must_== auUserNotfSaved
          }
        }

        "load it, by time, to mail out" in {
          val notfsToMail = systemDbDao.loadNotfsToMailOut(
             delayInMinutes = 0, numToLoad = 10)
          notfsToMail.usersByTenantAndId.get((defaultTenantId, auUser.id)
             ) must_== Some(auUser)
          val notfsByTenant = notfsToMail.notfsByTenant(defaultTenantId)
          val notfFound = notfsByTenant.find(
             _.recipientUserId == auUserNotfSaved.recipientUserId)
          notfFound must_== Some(auUserNotfSaved)
        }
      }

      "not load any notf, when specifying another user's id " in {
        val notfsLoaded = dao.loadNotfsForRole("WrongUserId")
        notfsLoaded must_== Nil
      }

      "not load any notf, because they are too recent" in {
        val notfsLoaded =
          systemDbDao.loadNotfsToMailOut(delayInMinutes = 15, numToLoad = 10)
        notfsLoaded.usersByTenantAndId must_== Map.empty
        notfsLoaded.notfsByTenant must_== Map.empty
      }

    }


    def testLoginViaEmail(emailId: String, emailSentOk: Email)
          : LoginGrant  = {
      val loginNoId = Login(id = "?", prevLoginId = None, ip = "?.?.?.?",
         date = now, identityId = emailId)
      val loginReq = LoginRequest(loginNoId, IdentityEmailId(emailId))
      val loginGrant = dao.saveLogin(loginReq)
      lazy val emailIdty = loginGrant.identity.asInstanceOf[IdentityEmailId]
      emailIdty.email must_== emailSentOk.sentTo
      emailIdty.notf.flatMap(_.emailId) must_== Some(emailSentOk.id)
      emailIdty.notf.map(_.recipientUserId) must_== Some(loginGrant.user.id)
      loginGrant
    }


    "support emails, to unauthenticated users" >> {

      lazy val emailId = "10"

      lazy val emailToSend = Email(
        id = emailId,
        sentTo = "test@example.com",
        sentOn = None,
        subject = "Test Subject",
        bodyHtmlText = "<i>Test content.</i>",
        providerEmailId = None)

      lazy val emailSentOk = emailToSend.copy(
        sentOn = Some(now),
        providerEmailId = Some("test-provider-id"))

      lazy val emailSentFailed = emailSentOk.copy(
        providerEmailId = None,
        failureText = Some("Test failure"))

      def loadNotfToMailOut(userId: String): Seq[NotfOfPageAction] = {
        val notfsToMail = systemDbDao.loadNotfsToMailOut(
          delayInMinutes = 0, numToLoad = 10)
        val notfs = notfsToMail.notfsByTenant(defaultTenantId)
        val usersNotfs = notfs.filter(_.recipientUserId == userId)
        // All notfs loaded to mail out must have emails pending.
        usersNotfs.filterNot(_.emailPending).size must_== 0
        usersNotfs
      }

      "find the notification to mail out, to the unauth. user" in {
        val notfs: Seq[NotfOfPageAction] = loadNotfToMailOut(unauUser.id)
        notfs.size must_!= 0
        notfs must_== List(unauUserNotfSaved)
      }

      "save an email, connect it to the notification, to the unauth. user" in {
        dao.saveUnsentEmailConnectToNotfs(emailToSend, unauUserNotfSaved::Nil)
          // must throwNothing (how to test that?)
      }

      "skip notf, when loading notfs to mail out; email already created" in {
        val notfs: Seq[NotfOfPageAction] = loadNotfToMailOut(unauUser.id)
        notfs.size must_== 0
      }

      "load the saved email" in {
        val emailLoaded = dao.loadEmailById(emailToSend.id)
        emailLoaded must_== Some(emailToSend)
      }

      "load the notification, find it connected to the email" in {
        // BROKEN many notfs might map to 1 email!
        dao.loadNotfByEmailId(emailToSend.id) must beLike {
          case Some(notf) =>
            notf.emailId must_== Some(emailToSend.id)
          case None => failure("No notf found")
        }
      }

      "update the email, to sent status" in {
        dao.updateSentEmail(emailSentOk)
        // must throwNothing (how to test that?)
      }

      "load the email again, find it in okay status" in {
        val emailLoaded = dao.loadEmailById(emailToSend.id)
        emailLoaded must_== Some(emailSentOk)
      }

      "update the email, to failed status" in {
        dao.updateSentEmail(emailSentFailed)
        // must throwNothing (how to test that?)
      }

      "load the email again, find it in failed status" in {
        val emailLoaded = dao.loadEmailById(emailToSend.id)
        emailLoaded must_== Some(emailSentFailed)
      }

      "update the failed email to sent status (simulates a re-send)" in {
        dao.updateSentEmail(emailSentOk)
        // must throwNothing (how to test that?)
      }

      "load the email yet again, find it in sent status" in {
        val emailLoaded = dao.loadEmailById(emailToSend.id)
        emailLoaded must_== Some(emailSentOk)
      }

      "login and unsubscribe, via email" in {
        val loginGrant = testLoginViaEmail(emailId, emailSentOk)
        loginGrant.user.isAuthenticated must_== false
        dao.configIdtySimple(loginId = loginGrant.login.id,
          ctime = loginGrant.login.date, emailAddr = emailSentOk.sentTo,
          emailNotfPrefs = EmailNotfPrefs.DontReceive)
        // must throwNothing (how to test that?)
      }

      // COULD verify email prefs changed to DontReceive?
    }


    "support emails, to authenticated users" >> {

      lazy val emailId = "11"

      lazy val emailToSend = Email(
        id = emailId,
        sentTo = "test@example.com",
        sentOn = None,
        subject = "Test Subject",
        bodyHtmlText = "<i>Test content.</i>",
        providerEmailId = None)

      lazy val emailSentOk = emailToSend.copy(
        sentOn = Some(now),
        providerEmailId = Some("test-provider-id"))

      "save an email, connect it to a notification, to an auth. user" in {
        dao.saveUnsentEmailConnectToNotfs(emailToSend, auUserNotfSaved::Nil)
        // must throwNothing (how to test that?)
      }

      "load the notification, find it connected to the email" in {
        dao.loadNotfByEmailId(emailToSend.id) must beLike {
          case Some(notf) =>
            notf.emailId must_== Some(emailToSend.id)
          case None => failure("No notf found")
        }
      }

      "update the email, to sent status" in {
        dao.updateSentEmail(emailSentOk)
        // must throwNothing (how to test that?)
      }

      "load the email, find it in sent status" in {
        val emailLoaded = dao.loadEmailById(emailToSend.id)
        emailLoaded must_== Some(emailSentOk)
      }

      "login and unsubscribe, via email" in {
        val loginGrant = testLoginViaEmail(emailId, emailSentOk)
        loginGrant.user.isAuthenticated must_== true
        dao.configRole(loginId = loginGrant.login.id,
          ctime = loginGrant.login.date, roleId = loginGrant.user.id,
          emailNotfPrefs = Some(EmailNotfPrefs.DontReceive))
        // must throwNothing (how to test that?)
      }

      // COULD verify email prefs changed to DontReceive?
    }


    // -------- Admins

    "create and revoke admin privs" >> {

      def testAdmin(makeAdmin: Boolean, makeOwner: Option[Boolean]) = {
        // Somewhat dupl code, see "configure email to a Role" test.
        val userToConfig = exOpenId_loginGrant.user
        val login = exOpenId_loginGrant.login
        dao.configRole(loginId = login.id, ctime = new ju.Date, roleId = userToConfig.id,
          isAdmin = Some(makeAdmin), isOwner = makeOwner)
        val Some((idty, userConfigured)) = dao.loadIdtyAndUser(forLoginId = login.id)
        userConfigured.isAdmin must_== makeAdmin
        emailEx_OpenIdUser = userConfigured  // remember, to other test cases
        ok
      }

      "make a Role an administrator" in {
        testAdmin(true, makeOwner = None)
      }

      "change the Role back to a normal user" in {
        testAdmin(false, makeOwner = None)
      }

      "make a Role an admin and owner" in {
        testAdmin(true, makeOwner = Some(true))
      }

      "change the Role back to a normal user" in {
        testAdmin(false, makeOwner = Some(false))
      }
    }


    // -------- Move a page

    var allPathsCanonicalFirst: List[PagePath] = null

    "move and rename pages" >> {

      lazy val pagePath = dao.lookupPagePath(testPage.id).get

      var oldPaths: List[PagePath] = Nil
      var newPath: PagePath = null
      var previousPath: PagePath = null

      def testThat(pathCount: Int, oldPaths:  List[PagePath],
            redirectTo: PagePath) = {
        assert(pathCount == oldPaths.size) // test test suite
        val newPath = redirectTo
        for (oldPath <- oldPaths) {
          // If page id not shown in URL, remove id, or the path will be
          // trivially resolved.
          val pathPerhapsNoId =
            if (oldPath.showId == false) oldPath.copy(pageId = None)
            else oldPath
          dao.checkPagePath(pathPerhapsNoId) must_== Some(newPath)
        }
        ok
      }

      "leave a page as is" in {
        // No move/rename options specified:
        dao.moveRenamePage(pageId = testPage.id) must_== pagePath
        oldPaths ::= pagePath
        ok
      }

      "won't move a non-existing page" in {
        dao.moveRenamePage(
          pageId = "non_existing_page",
          newFolder = Some("/folder/"), showId = Some(false),
          newSlug = Some("new-slug")) must throwAn[Exception]
      }

      "move a page to another folder" in {
        newPath = dao.moveRenamePage(pageId = testPage.id,
          newFolder = Some("/new-folder/"))
        newPath.folder must_== "/new-folder/"
        newPath.pageSlug must_== pagePath.pageSlug
        newPath.showId must_== pagePath.showId
      }

      "and redirect one old path to new path" in {
        testThat(1, oldPaths, redirectTo = newPath)
      }

      "rename a page" in {
        oldPaths ::= newPath
        newPath = dao.moveRenamePage(
          pageId = testPage.id,
          newSlug = Some("new-slug"))
        newPath.folder must_== "/new-folder/"
        newPath.pageSlug must_== "new-slug"
        newPath.showId must_== pagePath.showId
      }

      "and redirect two old paths to new path" in {
        testThat(2, oldPaths, redirectTo = newPath)
      }

      "toggle page id visibility in URL" in {
        oldPaths ::= newPath
        newPath = dao.moveRenamePage(
          pageId = testPage.id,
          showId = Some(!pagePath.showId))
        newPath.folder must_== "/new-folder/"
        newPath.pageSlug must_== "new-slug"
        newPath.showId must_== !pagePath.showId
      }

      "and redirect three old paths to new path" in {
        testThat(3, oldPaths, redirectTo = newPath)
      }

      "move and rename a page at the same time" in {
        oldPaths ::= newPath
        previousPath = newPath // we'll move it back to here, soon
        newPath = dao.moveRenamePage(
          pageId = testPage.id,
          newFolder = Some("/new-folder-2/"),
          showId = Some(true), newSlug = Some("new-slug-2"))
        newPath.folder must_== "/new-folder-2/"
        newPath.pageSlug must_== "new-slug-2"
        newPath.showId must_== true
      }

      "and redirect four old paths to new path" in {
        testThat(4, oldPaths, redirectTo = newPath)
      }

      "list the page at the correct location" in {
        val pagePathsDetails = dao.listPagePaths(
          PathRanges(trees = Seq("/")),
          include = v0.PageStatus.All,
          sortBy = v0.PageSortOrder.ByPath,
          limit = Int.MaxValue,
          offset = 0
        )
        pagePathsDetails must beLike {
          case list: List[(PagePath, PageMeta)] =>
            list.find(_._1 == newPath) must beSome
        }
      }

      "move-rename to a location that happens to be its previous location" in {
        oldPaths ::= newPath
        newPath = dao.moveRenamePage(
          pageId = testPage.id,
          newFolder = Some(previousPath.folder),
          showId = Some(previousPath.showId),
          newSlug = Some(previousPath.pageSlug))
        newPath must_== previousPath
      }

      "and redirect five paths to the current (a previous) location" in {
        testThat(5, oldPaths, redirectTo = newPath)
        // For next test:
        allPathsCanonicalFirst = newPath :: oldPaths.filterNot(_ == newPath)
        ok
      }
    }


    // -------- List page paths


    "list page paths including redirects" in {
      val keys = allPathsCanonicalFirst
      val pageId = keys.head.pageId.get
      val pathsLoaded = dao.lookupPagePathAndRedirects(pageId)
      val keysFound = keys.filter(key => pathsLoaded.find(_ == key).isDefined)
      val superfluousPathsLoaded =
        pathsLoaded.filter(pathLoaded => keys.find(_ == pathLoaded).isEmpty)
      keysFound must_== keys
      superfluousPathsLoaded must_== Nil
      pathsLoaded.length must_== keys.length // Not needed? Feels safer.
      // The canonical path must be the first one listed.
      pathsLoaded.head must_== keys.head
    }

    "list no paths for a non-existing page" in {
      dao.lookupPagePathAndRedirects("badpageid") must_== Nil
    }


    // -------- Move page to previous location


    "move a page to its previous location" >> {

      "fail to move a non-existing page" in {
        val badPath = PagePath(defaultTenantId, "/folder/", None,
          showId = false, pageSlug = "non-existing-page-532853")
        dao.movePageToItsPreviousLocation(badPath) must
          throwA[PageNotFoundByPathException]
      }

      var origHomepage: Page = null
      var newHomepage: Page = null

      def homepagePathNoId = PagePath(defaultTenantId, "/", None, false, "")

      def homepagePathWithIdTo(page: Page) =
        homepagePathNoId.copy(pageId = Some(page.id))

      "create page /_old/homepage" in {
        origHomepage = createPage("/_old/homepage", showId = false)
        ok
      }

      "move it page to / (homepage)" in {
        val homepagePath = homepagePathWithIdTo(origHomepage)
        dao.moveRenamePage(homepagePath)
        val newPath = dao.checkPagePath(homepagePath.copy(pageId = None))
        newPath must_== Some(homepagePath)
      }

      "move it back to its previous location" in {
        val oldPath = PagePath(defaultTenantId, "/_old/",
          Some(origHomepage.id), showId = false, pageSlug = "homepage")
        val newPath = dao.movePageToItsPreviousLocation(homepagePathNoId)
        newPath must_== Some(oldPath)
      }

      "create a new homepage at /new-homepage" in {
        newHomepage = createPage("/new-homepage", showId = true)
        ok
      }

      "move the new homepage to /" in {
        val homepagePath = homepagePathWithIdTo(newHomepage)
        dao.moveRenamePage(homepagePath)
        val newPath = dao.checkPagePath(homepagePath.copy(pageId = None))
        newPath must_== Some(homepagePath)
      }

      "move new homepage back to its previous location" in {
        dao.movePageToItsPreviousLocation(homepagePathNoId) must beLike {
          case Some(pagePath) =>
            pagePath.pageSlug must_== "new-homepage"
            pagePath.folder must_== "/"
            pagePath.showId must_== true
        }
      }

      "move the original homepage to / again" in {
        val homepagePath = homepagePathWithIdTo(origHomepage)
        dao.moveRenamePage(homepagePath)
        val newPath = dao.checkPagePath(homepagePath.copy(pageId = None))
        newPath must_== Some(homepagePath)
      }
   }



    // -------- Page path clashes


    def createPage(path: String, showId: Boolean = false): Page = {
      val pagePath =
        PagePath.fromUrlPath(defaultTenantId, path = path) match {
          case PagePath.Parsed.Good(path) => path.copy(showId = showId)
          case x => failure(s"Test broken, bad path: $x")
        }
      dao.createPage(Page.newEmptyPage(PageRole.Generic, pagePath,
        author = loginGrant.user))
    }


    "not overwrite page paths, but overwrite redirects, when creating page" >> {

      var page_f_index: Page = null
      var page_f_page: Page = null

      "create page /f/ and /f/page" in {
        page_f_index = createPage("/f/")
        page_f_page = createPage("/f/page")
      }

      "reject new page /f/ and /f/page, since would overwrite paths" in {
        createPage("/f/") must throwA[PathClashException]
        createPage("/f/page") must throwA[PathClashException]
      }

      "create pages /f/-id, /f/-id-page and /f/-id2-page, since ids shown" in {
        createPage("/f/", showId = true)
        createPage("/f/page/", showId = true)
        createPage("/f/page/", showId = true) // ok, since different id
        ok
      }

      "move page /f/ to /f/former-index, redirect old path" in {
        dao.moveRenamePage(page_f_index.id, newSlug = Some("former-index"))
        val newPath = dao.checkPagePath(page_f_index.path.copy(pageId = None))
        newPath must_== Some(page_f_index.path.copy(pageSlug = "former-index"))
      }

      "move page /f/page to /f/former-page, redirect old path" in {
        dao.moveRenamePage(page_f_page.id, newSlug = Some("former-page"))
        val newPath = dao.checkPagePath(page_f_page.path.copy(pageId = None))
        newPath must_== Some(page_f_page.path.copy(pageSlug = "former-page"))
      }

      "create new page /f/, overwrite redirect to /f/former-index" in {
        val page_f_index_2 = createPage("/f/")
        page_f_index_2 must_!= page_f_index
        // Now the path that previously resolved to page_f_index
        // must instead point to page_f_index_2.
        val path = dao.checkPagePath(page_f_index.path.copy(pageId = None))
        path must_== Some(page_f_index_2.path)
      }

      "create new page /f/page, overwrite redirect to /f/page-2" in {
        val page_f_page_2 = createPage("/f/page")
        page_f_page_2 must_!= page_f_page
        // Now the path that previously resolved to page_f_page
        // must instead point to page_f_page_2.
        val path = dao.checkPagePath(page_f_page.path.copy(pageId = None))
        path must_== Some(page_f_page_2.path)
      }
    }



    "not overwrite page paths, when moving pages" >> {

      var page_g_index: Page = null
      var page_g_2: Page = null
      var page_g_page: Page = null
      var page_g_page_2: Page = null

      "create page /g/, /g/2, and /g/page, /g/page-2" in {
        page_g_index = createPage("/g/")
        page_g_2 = createPage("/g/2")
        page_g_page = createPage("/g/page")
        page_g_page_2 = createPage("/g/page-2")
      }

      "refuse to move /g/2 to /g/ — would overwrite path" in {
        dao.moveRenamePage(page_g_2.id, newSlug = Some("")) must
          throwA[PathClashException]
      }

      "refuse to move /g/page-2 to /g/page — would overwrite path" in {
        dao.moveRenamePage(page_g_page_2.id, newSlug = Some("page")) must
          throwA[PathClashException]
      }

      "move /g/ to /g/old-ix, redirect old path" in {
        val newPath = dao.moveRenamePage(page_g_index.id, newSlug = Some("old-ix"))
        val resolvedPath = dao.checkPagePath(page_g_index.path.copy(pageId = None))
        resolvedPath must_== Some(newPath)
        resolvedPath.map(_.pageSlug) must_== Some("old-ix")
      }

      "move /g/page to /g/former-page, redirect old path" in {
        val newPath = dao.moveRenamePage(page_g_page.id, newSlug = Some("old-page"))
        val resolvedPath = dao.checkPagePath(page_g_page.path.copy(pageId = None))
        resolvedPath must_== Some(newPath)
        resolvedPath.map(_.pageSlug) must_== Some("old-page")
      }

      "now move /g/2 to /g/ — overwrite redirect, fine" in {
        val newPath = dao.moveRenamePage(page_g_2.id, newSlug = Some(""))
        newPath must_== page_g_2.path.copy(pageSlug = "")
        // Now the path that previously resolved to page_g_index must
        // point to page_g_2.
        val resolvedPath = dao.checkPagePath(page_g_index.path.copy(pageId = None))
        resolvedPath must_== Some(newPath)
        // And page_g_2's former location must point to its new location.
        val resolvedPath2 = dao.checkPagePath(page_g_2.path.copy(pageId = None))
        resolvedPath2 must_== Some(newPath)
      }

      "and move /g/page-2 to /g/page — overwrite redirect" in {
        val newPath = dao.moveRenamePage(page_g_page_2.id, newSlug = Some("page"))
        newPath must_== page_g_page_2.path.copy(pageSlug = "page")
        // Now the path that previously resolved to page_g_page must
        // point to page_g_page_2.
        val resolvedPath = dao.checkPagePath(page_g_page.path.copy(pageId = None))
        resolvedPath must_== Some(newPath)
        // And page_g_page_2's former location must point to its new location.
        val resolvedPath2 = dao.checkPagePath(page_g_page_2.path.copy(pageId = None))
        resolvedPath2 must_== Some(newPath)
      }
    }



    // -------- Move many pages

    /*
    RelDbTenantDao._movePages throws:
        unimplemented("Moving pages and updating DW1_PAGE_PATHS.CANONICAL")

    "move many pages" >> {

      "move Nil pages" in {
        dao.movePages(Nil, fromFolder = "/a/", toFolder = "/b/") must_== ()
      }

      "won't move non-existing pages" in {
        dao.movePages(List("nonexistingpage"), fromFolder = "/a/",
            toFolder = "/b/") must_== ()
      }

      "can move page to /move-pages/folder1/" in {
        val pagePath = dao.lookupPagePathByPageId(testPage.id).get
        testMovePages(pagePath.folder, "/move-pages/folder1/")
      }

      "can move page from /move-pages/folder1/ to /move-pages/f2/" in {
        testMovePages("/move-pages/folder1/", "/move-pages/f2/")
      }

      "can move page from /move-pages/f2/ to /_drafts/move-pages/f2/" in {
        testMovePages("/", "/_drafts/", "/_drafts/move-pages/f2/")
      }

      "can move page from /_drafts/move-pages/f2/ back to /move-pages/f2/" in {
        testMovePages("/_drafts/", "/", "/move-pages/f2/")
      }

      "won't move pages that shouldn't be moved" in {
        // If moving pages specified by id:
        // Check 1 page, same tenant but wrong id.
        // And 1 page, other tenant id but same id.
        // If moving all pages in a certain folder:
        // And 1 page, same tenand, wrong folder.
        // And 1 page, other tenand, correct folder.
      }

      "can move two pages, specified by id, at once" in {
      }

      "can move all pages in a folder at once" in {
      }

      "throws, if illegal folder names specified" in {
        testMovePages("no-slash", "/") must throwA[RuntimeException]
        testMovePages("/", "no-slash") must throwA[RuntimeException]
        testMovePages("/regex-*?[]-chars/", "/") must throwA[RuntimeException]
      }

      def testMovePages(fromFolder: String, toFolder: String,
            resultingFolder: String = null) {
        dao.movePages(pageIds = List(testPage.id),
          fromFolder = fromFolder, toFolder = toFolder)
        val pagePathAfter = dao.lookupPagePathByPageId(testPage.id).get
        pagePathAfter.folder must_== Option(resultingFolder).getOrElse(toFolder)
        pagePathAfter.pageId must_== Some(testPage.id)
      }
    }
    */


    // -------- Create more websites

    "create new websites" >> {

      lazy val creatorLogin = exOpenId_loginGrant.login
      lazy val creatorIdentity =
         exOpenId_loginGrant.identity.asInstanceOf[IdentityOpenId]
      lazy val creatorRole = exOpenId_loginGrant.user

      var newWebsiteOpt: Tenant = null
      var newHost = TenantHost("website-2.ex.com", TenantHost.RoleCanonical,
         TenantHost.HttpsNone)

      def newWebsiteDao() =
        newTenantDbDao(v0.QuotaConsumers(tenantId = newWebsiteOpt.id))

      var homepageId = "?"

      val homepageTitle = PostActionDto.forNewTitleBySystem(
        "Default Homepage", creationDati = now)

      def createWebsite(suffix: String): Option[(Tenant, User)] = {
        dao.createWebsite(
          name = "website-"+ suffix, address = "website-"+ suffix +".ex.com",
          ownerIp = creatorLogin.ip, ownerLoginId = creatorLogin.id,
          ownerIdentity = creatorIdentity, ownerRole = creatorRole)
      }

      "create a new website, from existing tenant" in {
        createWebsite("2") must beLike {
          case Some((site, user)) =>
            newWebsiteOpt = site
            ok
        }
      }

      "not create the same website again" in {
        createWebsite("2") must_== None
      }

      "lookup the new website, from existing tenant" in {
        systemDbDao.loadTenants(newWebsiteOpt.id::Nil) must beLike {
          case List(websiteInDb) =>
            websiteInDb must_== newWebsiteOpt.copy(hosts = List(newHost))
        }
      }

      "not create too many websites from the same IP" in {
        def create100Websites() {
          for (i <- 3 to 100)
            createWebsite(i.toString)
        }
        create100Websites() must throwA[TooManySitesCreatedException]
      }

      "create a default homepage, with a title authored by SystemUser" in {
        val emptyPage = PageParts(guid = "?")
        val pagePath = v0.PagePath(newWebsiteOpt.id, "/", None, false, "")
        val dao = newWebsiteDao()
        val page = dao.createPage(Page.newPage(
          PageRole.Generic, pagePath, emptyPage, author = SystemUser.User))
        homepageId = page.id
        dao.savePageActions(page.withoutPath, List(homepageTitle))
        ok
      }

      "load the homepage title by SystemUser" in {
        // Now the DbDao must use SystemUser._ stuff instead of creating
        // new login, identity and user.
        newWebsiteDao().loadPage(homepageId) must beLike {
          case Some(page: PageParts) =>
            page.title must beLike {
              case Some(title) =>
                title.currentText must_== homepageTitle.payload.text
                title.login_! must_== SystemUser.Login
                title.identity_! must_== SystemUser.Identity
                title.user_! must_== SystemUser.User
            }
        }
      }
    }



    // -------- Qutoa

    "manage quota" >> {

      lazy val role = exOpenId_loginGrant.user
      lazy val ip = "1.2.3.4"

      lazy val tenantConsumer = QuotaConsumer.Tenant(defaultTenantId)
      lazy val tenantIpConsumer = QuotaConsumer.PerTenantIp(defaultTenantId, ip)
      lazy val globalIpConsumer = QuotaConsumer.GlobalIp(ip)
      lazy val roleConsumer = QuotaConsumer.Role(defaultTenantId, roleId = role.id)

      lazy val consumers = List[QuotaConsumer](
         tenantConsumer, tenantIpConsumer, globalIpConsumer, roleConsumer)

      "find none, if there is none" in {
        val quotaStateByConsumer = systemDbDao.loadQuotaState(consumers)
        quotaStateByConsumer must beEmpty
      }

      "do nothin, if nothing to do" in {
        systemDbDao.useMoreQuotaUpdateLimits(Map[QuotaConsumer, QuotaDelta]())
          // ... should throw nothing
      }

      lazy val initialQuotaUse = QuotaUse(paid = 0, free = 200, freeload = 300)

      lazy val firstLimits = QuotaUse(0, 1002, 1003)

      lazy val initialResUse = ResourceUse(
         numLogins = 1,
         numIdsUnau = 2,
         numIdsAu = 3,
         numRoles = 4,
         numPages = 5,
         numActions = 6,
         numActionTextBytes = 7,
         numNotfs = 8,
         numEmailsOut = 9,
         numDbReqsRead = 10,
         numDbReqsWrite = 11)

      lazy val initialDeltaTenant = QuotaDelta(
         mtime = new ju.Date,
         deltaQuota = initialQuotaUse,
         deltaResources = initialResUse,
         newFreeLimit = firstLimits.free,
         newFreeloadLimit = firstLimits.freeload,
         initialDailyFree = 50,
         initialDailyFreeload = 60,
         foundInDb = false)

      lazy val initialDeltaTenantIp = initialDeltaTenant.copy(
         initialDailyFreeload = 61)

      lazy val initialDeltaGlobalIp = initialDeltaTenant.copy(
         initialDailyFreeload = 62)

      lazy val initialDeltaRole = initialDeltaTenant.copy(
         initialDailyFreeload = 63)

      lazy val initialDeltas = Map[QuotaConsumer, QuotaDelta](
         tenantConsumer -> initialDeltaTenant,
         tenantIpConsumer -> initialDeltaTenantIp,
         globalIpConsumer -> initialDeltaGlobalIp,
         roleConsumer -> initialDeltaRole)

      lazy val initialQuotaStateTenant = QuotaState(
         ctime = initialDeltaTenant.mtime,
         mtime = initialDeltaTenant.mtime,
         quotaUse = initialQuotaUse,
         quotaLimits = firstLimits,
         quotaDailyFree = 50,
         quotaDailyFreeload = 60,
         resourceUse = initialResUse)

      lazy val initialQuotaStateTenantIp = initialQuotaStateTenant.copy(
         quotaDailyFreeload = 61)

      lazy val initialQuotaStateGlobalIp = initialQuotaStateTenant.copy(
         quotaDailyFreeload = 62)

      lazy val initialQuotaStateRole = initialQuotaStateTenant.copy(
         quotaDailyFreeload = 63)

      "create new quota entries, when adding quota" in {
        systemDbDao.useMoreQuotaUpdateLimits(initialDeltas)
        val quotaStateByConsumer = systemDbDao.loadQuotaState(consumers)

        quotaStateByConsumer.get(tenantConsumer) must_==
           Some(initialQuotaStateTenant)

        quotaStateByConsumer.get(tenantIpConsumer) must_==
           Some(initialQuotaStateTenantIp)

        quotaStateByConsumer.get(globalIpConsumer) must_==
           Some(initialQuotaStateGlobalIp)

        quotaStateByConsumer.get(roleConsumer) must_==
           Some(initialQuotaStateRole)
      }

      var laterQuotaStateTenant: QuotaState = null
      var laterQuotaStateGlobalIp: QuotaState = null

      "add quota and resource deltas, set new limits" in {
        // Add the same deltas again, but with new limits and mtime.

        val laterTime = new ju.Date
        val newLimits = initialQuotaStateTenant.quotaLimits.copy(
           free = 2002, freeload = 2003)

        val laterDeltas = initialDeltas.mapValues(_.copy(
          mtime = laterTime,
          newFreeLimit = newLimits.free,
          newFreeloadLimit = newLimits.freeload,
          foundInDb = true))

        laterQuotaStateTenant = initialQuotaStateTenant.copy(
          mtime = laterTime,
          quotaUse = initialQuotaUse + initialQuotaUse,
          quotaLimits = newLimits,
          resourceUse = initialResUse + initialResUse)

        val laterQuotaStateTenantIp = laterQuotaStateTenant.copy(
          quotaDailyFreeload = 61)

        laterQuotaStateGlobalIp = laterQuotaStateTenant.copy(
          quotaDailyFreeload = 62)

        val laterQuotaStateRole = laterQuotaStateTenant.copy(
          quotaDailyFreeload = 63)

        systemDbDao.useMoreQuotaUpdateLimits(laterDeltas)
        val quotaStateByConsumer = systemDbDao.loadQuotaState(consumers)

        quotaStateByConsumer.get(tenantConsumer) must_==
           Some(laterQuotaStateTenant)

        quotaStateByConsumer.get(tenantIpConsumer) must_==
           Some(laterQuotaStateTenantIp)

        quotaStateByConsumer.get(globalIpConsumer) must_==
           Some(laterQuotaStateGlobalIp)

        quotaStateByConsumer.get(roleConsumer) must_==
           Some(laterQuotaStateRole)
      }

      "not lower limits or time" in {
        val lowerLimits = initialQuotaStateTenant.quotaLimits.copy(
          free = 502, freeload = 503)
        // Set time to 10 ms before current mtime.
        val earlierTime = new ju.Date(
           laterQuotaStateTenant.mtime.getTime - 10)

        val lowerLimitsDelta = QuotaDelta(
          // This mtime should be ignored, because it's older than db mtime.
          mtime = earlierTime,
          deltaQuota = QuotaUse(),
          deltaResources = ResourceUse(),
          // These 2 limits should be ignored: the Dao won't
          // lower the limits.
          newFreeLimit = lowerLimits.free,
          newFreeloadLimit = lowerLimits.freeload,
          // These 2 limits should not overwrite db values.
          initialDailyFree = 1,
          initialDailyFreeload = 2,
          foundInDb = true)

        // Keep mtime and limits unchanged.
        val unchangedQuotaState = laterQuotaStateTenant

        systemDbDao.useMoreQuotaUpdateLimits(
          Map(tenantConsumer -> lowerLimitsDelta))

        val quotaStateByConsumer =
           systemDbDao.loadQuotaState(tenantConsumer::Nil)

        quotaStateByConsumer.get(tenantConsumer) must_==
           Some(unchangedQuotaState)
      }

      "not complain if other server just created quota state entry" in {
        // Set foundInDb = false, for an entry that already exists.
        // The server will attempt to insert it, which causes a unique key
        // error. But the server should swallow it, and continue
        // creating entries for other consumers, and then add deltas.

        val timeNow = new ju.Date
        assert(!initialDeltaGlobalIp.foundInDb)
        val delta = initialDeltaGlobalIp.copy(mtime = timeNow)

        // First and Last are new consumers. Middle already exists.
        val globalIpConsumerFirst = QuotaConsumer.GlobalIp("0.0.0.0")
        val globalIpConsumerMiddle = globalIpConsumer
        val globalIpConsumerLast = QuotaConsumer.GlobalIp("255.255.255.255")
        val ipConsumers = List(
          globalIpConsumerFirst, globalIpConsumerMiddle, globalIpConsumerLast)

        val deltas = Map[QuotaConsumer, QuotaDelta](
           globalIpConsumerFirst -> delta,
           globalIpConsumerMiddle -> delta,  // causes unique key error
           globalIpConsumerLast -> delta)

        val resultingStateFirstLast = initialQuotaStateGlobalIp.copy(
           ctime = timeNow,
           mtime = timeNow)

        val resultingStateMiddle = laterQuotaStateGlobalIp.copy(
           mtime = timeNow,
           quotaUse = laterQuotaStateGlobalIp.quotaUse + initialQuotaUse,
           resourceUse = laterQuotaStateGlobalIp.resourceUse + initialResUse)

        // New entries sould be created for First and Last,
        // although new-entry-creation fails for Middle (already exists).
        systemDbDao.useMoreQuotaUpdateLimits(deltas)
        val quotaStateByConsumer = systemDbDao.loadQuotaState(ipConsumers)

        quotaStateByConsumer.get(globalIpConsumerFirst) must_==
           Some(resultingStateFirstLast)

        quotaStateByConsumer.get(globalIpConsumerMiddle) must_==
           Some(resultingStateMiddle)

        quotaStateByConsumer.get(globalIpConsumerLast) must_==
           Some(resultingStateFirstLast)
      }

    }


    // -------------
    //val ex3_emptyPost = PostActionDto.copyCreatePost(T.post,
    // parentPostId = Page.BodyId, text = "Lemmings!")
    //"create many many random posts" in {
    //  for (i <- 1 to 10000) {
    //    dao.savePageActions(
    //          "-"+ testPage.parts (what??), List(ex3_emptyPost)) must beLike {
    //      case List(p: Post) => ok
    //    }
    //  }
    //}
  }

}


// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list
