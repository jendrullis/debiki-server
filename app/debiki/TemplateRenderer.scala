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

package debiki

import com.debiki.v0._
import controllers.PageRequest
import java.{util => ju, lang => jl}
import play.api._
import Prelude._


object TemplateRenderer {


  private val BuiltinThemesPrefix = "builtin."  // after '/' has been replaced with '.'
  private val DefaultThemeName = s"${BuiltinThemesPrefix}default20121009"


  def renderTemplate(pageReq: PageRequest[_], appendToBody: xml.NodeSeq = Nil)
        : String =
    try {
      renderTemplateImpl(pageReq, appendToBody)
    }
    catch {
      case ex: PageConfigException =>
        views.html.specialpages.brokenPage(ex).body
      case ex: BadTemplateException =>
        views.html.specialpages.brokenPage(ex).body
      case ex: WebsiteConfigException =>
        views.html.specialpages.brokenPage(ex).body
    }


  private def renderTemplateImpl(
        pageReq: PageRequest[_], appendToBody: xml.NodeSeq): String = {

    val tpi = new TemplateProgrammingInterface(pageReq, appendToBody)

    if (pageReq.pageRoot.isPageConfigPost || pageReq.pagePath.isConfigPage) {
      // Use a page that we know for sure is not broken, so it's possible
      // to fix errors. And do this before loading any config values,
      // since a config file might be corrupted (exception thrown).
      return views.html.specialpages.template(
        tpi, isPageSettings = pageReq.pageRoot.isPageConfigPost).body
    }

    // Handle page config values.
    //if (commentVisibility == CommentVisibility.ShowOnClick) {
    //  curHeadTags = curHeadTags ++ HtmlPageSerializer.tagsThatHideShowInteractions
    //}

    // Might have to add ng-app to <html>, for AngularJS to work?

    //  <html class={classes}>
    //    <head>{curHeadTags}</head>
    //    {<body>{curBodyTags ++ appendToBody}</body> % curBodyAttrs}
    //  </html> % curHtmlAttrs

    // For now, use the same template for all websites.
    // In the future: Create more templates, and check which one to use in _site.conf.
    // In the distant future, implement my ideas in play-thoughts.txt.



    val theme = {
      val themeUnsafe = tpi.websiteConfigValue("theme") orIfEmpty DefaultThemeName
      // People place themes in file system dirs, so allow them to use "/" when
      // specifying in which directory the theme is located? This is more user friendly
      // than forcing them to use Javas package delimiter, '.'? But we need to convert to '.'
      // now so we can look up the Scala package + class.
      val themeNoDelims = themeUnsafe.replace('/', '.')
      // Don't allow anyone to use the www.debiki.com template:
      if (themeNoDelims == "wwwdebikicom" && !pageReq.host.endsWith("debiki.com")
          && !pageReq.host.startsWith("localhost:"))
        DefaultThemeName
      else
        themeNoDelims
    }

    val template = tpi.pageConfigValue("template") orIfEmpty {
      pageReq.pageRole_! match {
        case PageRole.BlogPost => "blogPost"
        case PageRole.Blog => "blog"
        case PageRole.ForumGroup => "forumGroup"
        case PageRole.Forum => "forum"
        case PageRole.ForumTopic => "forumTopic"
        case PageRole.Code => "codePage"
        case _ =>
        // A blog post template works well for most pages?
        "blogPost"
      }
    }

    renderThemeTemplate(theme, template, tpi)
  }


  private def renderThemeTemplate(theme: String, template: String,
        tpi: TemplateProgrammingInterface): String = {

    // Search one of two folders for the theme file, either themes/ or themesbuiltin/:
    // a few built-in default themes are located in app/views/themesbuiltin/,
    // other site specific themes are placed in app/views/themes/, which is a
    // softlink to a ../themes/ folder in a supposed parent Git repository with
    // site specific stuff.
    val viewClassName =
      if (theme startsWith BuiltinThemesPrefix) s"views.html.themes$theme.$template"
      else s"views.html.themes.$theme.$template"

    try {
      val viewClass : Class[_] = Play.current.classloader.loadClass(viewClassName)
      val renderMethod: jl.reflect.Method = viewClass.getDeclaredMethod(
        "apply", classOf[TemplateProgrammingInterface])
      val result = renderMethod.invoke(viewClass, tpi)
      val htmlText = result.asInstanceOf[templates.Html].body
      htmlText
    }
    catch {
      case ex: jl.ClassNotFoundException =>
        throw PageConfigException(
          "DwE7F3X9", s"Template not found: `$template', theme: `$theme'")
      case ex: jl.NoSuchMethodException =>
        throw new PageConfigException(
          "DwE68St8", o"""Template `$template', theme: `$theme', is broken.
          Does it not start with `@(tpi: TemplateProgrammingInterface)'?""")
      case wrappingException: jl.reflect.InvocationTargetException =>
        val originalException = wrappingException.getCause
        throw originalException
    }
  }


  class PageConfigException(errorCode: String, details: String)
    extends DebikiException(errorCode, details)

  object PageConfigException {
    def apply(errorCode: String, details: String) =
      new PageConfigException(errorCode, details)
  }


  class BadTemplateException(errorCode: String, details: String)
    extends DebikiException(errorCode, details)

  object BadTemplateException {
    def apply(errorCode: String, details: String) =
      new BadTemplateException(errorCode, details)
  }

}
