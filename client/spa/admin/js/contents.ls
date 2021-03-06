/* AngularJS controller for the admin dashboard Contents tab.
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


import prelude
d = i: debiki.internal, u: debiki.v0.util
bug = d.u.die2



/**
 * Stringifies various ListItem fields, for display in html.
 */
PrettyListItem =

  clearCache: !->
    void


  prettyUrl: ->
    # Remove any '?view-new-page=...&passhash=...' query string.
    prettyPath = @path.replace /\?.*/, ''
    if @path is '/' => '/ (homepage)'
    else prettyPath


  prettyTitle: ->
    if @title?.length => @title
    else if @path == '/_site.conf'
      "#{@path} (website configuration)"
    else switch @role
      | 'Code' => @path
      | 'Blog' =>
        # 1. The title of a blog is currently not shown (there is no blog
        # page Scala template that shows the title post) and therefore
        # cannot be edited. But showing "Blog" rather than "(Unnamed page)"
        # should make everything easier to understand?
        # 2. If this is /blog-2/ or -3, however, show "Blog 2" or "Blog 3".
        # 3. COULD make blog title editable, if needed by templates (if
        # there'll be a template that shows the title) or for SEO purposes.
        blogNo = @path.match //^/blog-([\d])+/$//
        if blogNo?.length == 2 => "Blog #{blogNo[1]}"
        else 'Blog'
      | 'ForumGroup' => '(Unnamed forum group)'
      | 'Forum' => '(Unnamed forum)'
      | 'ForumTopic' => '(Unnamed forum topic)'
      | _ => '(Unnamed page)'


  prettyRole: ->
    switch @role
      | 'Generic' => 'Page'
      | 'Code' => 'Code'
      | 'Blog' => 'Blog'
      | 'BlogPost' => 'Blog post'
      | 'ForumGroup' => 'Forum group'
      | 'Forum' => 'Forum'
      | 'ForumTopic' => 'Forum topic'
      | 'Wiki' => 'Wiki'
      | 'WikiPage' => 'Wiki page'
      | _ => ''


  prettyRoleTooltip: ->
    switch @role
      | 'Blog' => 'Lists blog posts.'
      | 'ForumGroup' => 'Groups forums and forum groups.'
      | 'Forum' => 'Lists forum topics.'
      | _ => ''


  prettyStatus: ->
    if @isPrivate! => 'Private'
    else @status


  isPrivate: ->
    # Pages or folders that start with '_' are private.
    # ('Published'/'Draft' is ignored by the server.)
    //\/_//.test @path


  prettyStatusTooltip: ->
    if @isPrivate! => 'Only administrators can view the page.'
    else switch @status
      | 'Published' => 'The page is visible to anyone.'
      | 'Draft' => '''
            Only the page's author and administrators can view the page.'''
      | _ => ''


  cssForRole: ->
    "page-role-#{@role}"


  prettyMenu: ->
    # Mock up the implementation for now.
    if @path == '/' => 'MainMenu#1'  # homepage probably no 1
    else if @role == 'Blog' => 'MainMenu#2'
    else => ''


  prettyMenuTooltip: ->
    'Menu item number 1 in a menu named "MainMenu" links to the page.'


  cssClassForMark: ->
    if @marks then ' marked-path' else ''


  stringifyOtherMarks: ->
    text = ''
    for mark in @marks || []
      switch mark
      | _ => ''
      /* Ignore other marks for now. I think they actually clutter up
      the page, make it harder to read.
      | 'NewSaved' => text += ' (new, saved)'
      | 'New' => text += ' (newly created)'
      | 'Edited' => text += ' (edited)'
        */
    text



/**
 * Page data for a row in the page table.
 */
class ListItem implements PrettyListItem

  ~>
    @included = false

  /**
   * Updates this ListItem with data from another list item,
   * without overwriting certain states.
   */
  update = !({ withDataFrom }) ->
    wasIncluded = @included
    @ <<< withDataFrom
    @included = wasIncluded
    @clearCache!



class PageListItem extends ListItem

  (page) ~>
    @ <<< page
    @pageId = @id # rename one of them?
    if @parentPageId => @isChildPage = true
    if find (== @role), ['Blog', 'Forum', 'WikiMainPage']
      @isMainPage = true
    super!

  slug: -> d.i.findPageSlugIn @path

  folderPath: -> d.i.parentFolderOfPage @path

  setPath: !({ newFolder, newSlug, showId }) ->
    if newSlug? && !newFolder? && !showId?
      # For now.
      # I haven't implemented `changeShowIdIn` or `changeFolderIn`.
      # If I do, place all change... functions in d.i.pagePath.*?
      @path = d.i.changePageSlugIn @path, to: newSlug
    else
      bug 'showId currently required [DwE28JW2]' unless showId?
      bug 'showId == true unsupported [DwE01bI3]' if showId
      bug 'newFolder currently required [DwE84KB35]' unless newFolder?
      bug 'newSlug currently required [DwE74kIR3]' unless newSlug?
      @path = newFolder + newSlug
      @folder = newFolder  # COULD try to derive @folder! from @path instead
                           # (or bug risk, need to update both at same time)
    @clearCache!



/**
 * Lists and creates pages, blogs, forums and wikis.
 *
 * Concerning page creation:
 * Pages (e.g. a blog main page) are created at default locations, e.g.
 * /blog/. Most people don't understand URLs anyway, and they who do,
 * can move it later, via the Move button.
 */
@PathsCtrl = ['$scope', 'AdminService', ($scope, adminService) ->


  $scope.listItems = []


  getPageById = (pageId) ->
    # COULD optimize: Store pages by id in an object too?
    # But bug risk, would need to sync with listItems.
    # Or write my own SortedMap datastructure? Or use this one?:
    # http://dailyjs.com/2012/09/24/linkedhashmap/
    find (.id == pageId), $scope.listItems


  $scope.countParentsOf = (pageItem) ->
    curItem = pageItem
    parentCount = 0
    while curItem.parentPageId
      parentCount += 1
      curItem = getPageById curItem.parentPageId
    parentCount


  getSelectedPageOrDie = ->
    bug('DwE83Iw2') if selectedPageListItems.length != 1
    selectedPageListItems[0]


  anySelectedPage = ->
    return void unless selectedPageListItems.length
    selectedPageListItems[0]


  # i18n: COULD Use http://xregexp.com/ instead of the build in regex
  # engine (so the title and slug will accept non-Latin chars),
  # and ... I suppose I then cannot use the ng-pattern directive,
  # I'll have to write an x-ng-xregexp directive?
  $scope.patterns =
    folderPath: //^/([^?#:\s]+/)?$//
    dummy: //#// # restores Vim's syntax highlighting
                 # (Vim thinks the first # starts a comment)
    pageTitle: //^.*$//
    # The page slug must not start with '-', because then the
    # rest of the slug could be mistaken for the page id.
    # Don't allow the slug to start with '_' — perhaps I'll decide
    # to give '_' some magic meaning in the future?
    pageSlug: //(^$)|(^[\w\d\.][\w\d\._-]*)$//


  $scope.createBlog = !->
    folder = findFreePathLike '/blog/'
    createPage {
        folder: folder
        pageSlug: ''
        showId: false
        # Publish the blog itself. I think people will otherwise forget
        # to publish it (because I do) and wonder why it's hidden.
        status: 'Published'
        pageRole: 'Blog' }


  $scope.createForum = !->
    folder = findFreePathLike '/forum/'
    createPage {
        folder: folder
        pageSlug: ''
        showId: false
        # Create a *published* forum — I'm always so confused when
        # some child forums appear, and others don't, because they're
        # published or not-yet-published.
        status: 'Published'
        pageRole: 'Forum' }


  $scope.createWrappingForumGroup = !->
    forum = getSelectedPageOrDie!
    bug 'DwE23W5' unless forum.role == 'Forum'
    insertForumGroup = !({ newPages, editedPages }) ->
      if newPages.length != 1 || newPages[0].pageRole != 'ForumGroup'
        bug 'DwE70q3'
      if editedPages.length != 1 || editedPages[0].pageRole != 'Forum'
        bug 'DwE6Rx5'
      handleSavedPage newPages[0]
      handleSavedPage editedPages[0]
    adminService.wrapForumInGroup forum.id, onSuccess: insertForumGroup


  $scope.createSubforum = !->
    mainForum = getSelectedPageOrDie!
    parentFolder = d.i.parentFolderOfPage mainForum.path
    createPage {
        folder: parentFolder
        pageSlug: 'subforum'
        showId: true
        status: 'Published'  # see comment in `createForum` above
        pageRole: 'Forum'
        parentPageId: mainForum.id }


  $scope.createLocalThemeStyle = !->
    createPage {
        folder: localThemeFolder
        pageSlug: localThemeStyleSlug
        showId: false
        status: 'Published'
        pageRole: 'Code' }


  localThemeFolder = '/themes/local/'
  localThemeStyleSlug = 'theme.css'
  localThemeStylePath = "#localThemeFolder#localThemeStyleSlug"


  $scope.createDraftPage = !->
    createPageInFolder '/'


  /**
   * Finds a path similar to `path` but where no page is currently located.
   * `path` must end with at slash.
   * For example, if there's already a page at /blog/,
   * `findFreePathLike '/blog/'` would return '/blog-2/' instead.
   */
  findFreePathLike = (path) ->
    pathNoSlash = initial path
    suffix = ''
    curPath = ''
    for count from 1 to 100
      curPath = switch count
        | 1 => path
        | 100 => throw Error "Too many pages like #path"
        | _ => "#pathNoSlash-#count/"
      oldPath = null
      for item in $scope.listItems
        if item.path == curPath
          oldPath = item.path
          break
      break if !oldPath
    curPath


  createPageInFolder = !(parentFolder) ->
    createPage {
        folder: parentFolder
        pageSlug: 'new-page'
        showId: true
        status: 'Draft'
        pageRole: 'Generic' }


  /**
   * Creates a new unsaved page and opens it in a new browser tab.
   * Adds it to the contents list, marked as new, and selects it,
   * if/when the user edits and saves the page (in the other browser tab).
   *
   * `pageData` should be a:
   *    { folder, pageSlug, showId, (pageRole, parentPageId) }
   * where (...) is optional.
   */
  createPage = !(pageData) ->
    # Open new tab directly in response to user click, or browser popup
    # blockers tend to block the new tab.
    newTab = window.open '', '_blank'

    adminService.getViewNewPageUrl pageData, !(viewNewPageUrl) ->
      newTab.location = viewNewPageUrl
      # If the new page is saved, `newTab` will call the `onPageSaved`
      # callback just below. Then we'll update $scope.listItems.


  adminService.onPageSaved handleSavedPage


  !function handleSavedPage (pageMeta, pageTitle)
    newlySavedPageItem = PageListItem(
        path: pageMeta.pagePath
        id: pageMeta.pageId
        title: pageTitle
        #authors: undefined # should be current user
        role: pageMeta.pageRole
        status: pageMeta.pageStatus
        parentPageId: pageMeta.parentPageId)

    isNewPage = not find (.id == newlySavedPageItem.id), $scope.listItems

    if isNewPage
      newlySavedPageItem.marks = ['New']
      listMorePagesDeriveFolders [newlySavedPageItem]
    else
      newlySavedPageItem.marks = ['Edited']
      updatePage newlySavedPageItem


  $scope.selectedPage =
    getSelectedPageOrDie


  $scope.anySelectedPageTitle = ->
    anySelectedPage!?prettyTitle()


  /**
   * Opens a page in a new browser tab.
   *
   * (If the page was just created, but has not been saved server side,
   * the server will create it lazily if you edit and save it.)
   */
  $scope.viewSelectedPage = !->
    pageItem = getSelectedPageOrDie!
    window.open pageItem.path, '_blank'
    # COULD add callback that if page saved: (see Git stash 196d8accb80b81)
    # pageItem.update withDataFrom: { title: any-new-title }
    # and then: redrawPageItems [pageItem]


  $scope.moveSelectedPageTo = !(newFolder) ->
    pageListItem = getSelectedPageOrDie!
    curFolder = pageListItem.folderPath!
    moveSelectedPages fromFolder: curFolder, toFolder: newFolder


  moveSelectedPages = !({ fromFolder, toFolder }) ->
    refreshPageList = ->
      for pageListItem in selectedPageListItems
        pageListItem.path .= replace fromFolder, toFolder
      redrawPageItems selectedPageListItems

    for pageListItem in selectedPageListItems
      adminService.movePages [pageListItem.pageId],
          { fromFolder, toFolder, callback: refreshPageList }


  $scope.renameSelectedPageTo = !({ newSlug, newTitle }) ->
    moveRenameSelectedPageTo { newSlug, newTitle }


  $scope.changeHomepageToSelectedPage = !->
    # The previous path to the current homepage will be reactivated
    # when we overwrite the current path with the selected page.
    # (So the current homepage will remain reachable.)
    moveRenameSelectedPageTo (
      newFolder: '/', newSlug: '', showId: false,
      pushExistingPageToPrevLoc: true)


  moveRenameSelectedPageTo = !({
      newFolder, newSlug, showId, newTitle, pushExistingPageToPrevLoc }) ->

    refreshPageList = !(data, status, headers, config) ->
      if status != 200
        $scope.showModalDialog (
          title: "Error #status"
          body: data || "Something went wrong.")
        return
      pageListItem.title = newTitle if newTitle?
      pageListItem.setPath { newFolder, newSlug, showId }

      if data.pagePushedToPrevLoc
        updatePage data.pagePushedToPrevLoc

      redrawPageItems [pageListItem]

    pageListItem = getSelectedPageOrDie!
    adminService.moveRenamePage pageListItem.pageId, {
        newFolder, newSlug, showId, newTitle, pushExistingPageToPrevLoc,
        callback: refreshPageList }


  $scope.changePageStatus = !(newStatus) ->
    pageListItem = getSelectedPageOrDie!

    refreshPageList = ->
      pageListItem.status = newStatus
      redrawPageItems selectedPageListItems

    adminService.changePageMeta(
        [{ pageId: pageListItem.pageId, newStatus }],
        callback: refreshPageList)


  loadAndListPages = !->
    adminService.listAllPages !(pagesById) ->
      listMorePagesDeriveFolders <|
          [PageListItem(page) for id, page of pagesById]


  redrawPageItems = !(pageItems) ->
    # Remove pageItem and add it again, so any missing parent folder
    # is created, in case pageItem has been moved.
    # For now, call listMorePagesDeriveFolders once per pageItem
    # (a tiny bit inefficient).
    for pageItem in pageItems
      $scope.listItems = reject (== pageItem), $scope.listItems
      listMorePagesDeriveFolders [pageItem]


  listMorePagesDeriveFolders = !(morePageItems) ->

    for item in morePageItems
      # If `item.isChildPage && !item.isMainPage`, and there're very
      # many such pages, could group them into a single table row
      # that expands on click.
      # Could also grup pages by path, e.g. all pages in /about/?
      $scope.listItems.push item

    redrawPageList!


  updatePage = !(pageItem) ->
    oldItem = find (.id == pageItem.id), $scope.listItems
    bug 'DwE6SH90' unless oldItem
    oldMarks = oldItem.marks
    oldItem <<< pageItem
    oldItem.marks = concat [oldItem.marks, oldMarks] |> unique
    redrawPageList!


  redrawPageList = !->
    sortItemsInPlace $scope.listItems
    $scope.updateSelections!


  /**
   * Sort table rows by parent page id, folder and page slug.
   */
  sortItemsInPlace = !(items) ->

    sortOrderOf = (item) ->
      return item._sortOrder if item._sortOrder
      # Derive and cache item's sort order.
      # Use ' ' as sort field delimiter — this results in the homepage
      # appearing at the top of the table. ((Since it'd start with
      # ' /  <homepage-id>'), before e.g. any '/_old/' folder or '/aaa/'
      # folder. Space is the lowest printable ASCII char, and doesn't appear
      # in the url path segment as folder or page slug.))
      parentSortOrder =
        if item.parentPageId
          parent = getPageById item.parentPageId
          sortOrderOf parent
        else
          ''
      mySafeFolder = item.folderPath!.replace ' ', '' # shouldn't be needed
      mySafeSlug = item.slug!.replace ' ', ''
      mySortOrder = "#mySafeFolder #mySafeSlug #{item.id}"
      item._sortOrder = "#parentSortOrder #mySortOrder"
      item._sortOrder

    for item in items
      item._sortOrder = void

    items.sort (a, b) ->
      return -1 if sortOrderOf(a) < sortOrderOf(b)
      return +1 if sortOrderOf(b) < sortOrderOf(a)
      return 0


  /**
   * Statistics on which types of pages that exist. Used to decide
   * which help tips to show.
   */
  $scope.pageStats = {}


  /**
   * Updates statistics on which types of pages exists.
   * Currently only does this if a new page is added.
   * (Concerning `.length`: what if a page is added and another
   * one removed? Cannot happen right now though)
   */
  $scope.$watch 'listItems.length', !->
    stats = $scope.pageStats = {}

    for item in $scope.listItems
      if item.role is 'Blog' => stats.blogExists = true
      if item.role is 'BlogPost' => stats.blogPostExists = true

    stats.blogOrForumExists =
        stats.blogExists  # || forumMainPageExists


  $scope.nothingSelected = true

  selectedPageListItems = []


  /**
   * Scans $scope.listItems and updates page selection count
   * variables.
   */
  $scope.updateSelections = !->
    selectedPageListItems := []
    numDrafts = 0
    numPublished = 0
    numForumGroups = 0
    numForums = 0
    $scope.homepageSelected = false
    $scope.localThemeStyleExists = false

    for item in $scope.listItems

      if item.path == localThemeStylePath
        $scope.localThemeStyleExists = true

      continue unless item.included

      selectedPageListItems.push item
      $scope.homepageSelected = true if item.path == '/'
      if item.status == 'Draft' && !item.isPrivate! => numDrafts += 1
      if item.status == 'Published' && !item.isPrivate! => numPublished += 1
      if item.role == 'ForumGroup' => numForumGroups += 1
      if item.role == 'Forum' => numForums += 1

    numPages = selectedPageListItems.length

    $scope.nothingSelected = numPages == 0
    $scope.onlySelectedOnePage = numPages == 1
    $scope.onlySelectedOneForumGroup = numForumGroups == 1 && numPages == 1
    $scope.onlySelectedOneForum = numForums == 1 && numPages == 1

    $scope.onlySelectedDrafts = numDrafts == numPages && numPages > 0
    $scope.onlySelectedPubldPages = numPublished == numPages && numPages > 0

    # In the future, show stats on the selected pages, in a <div> to the
    # right. Or show a preview, if only one single page selected.


  $scope.test =
    sortItemsInPlace: sortItemsInPlace
    PageListItem: PageListItem


  loadAndListPages!

  ]


# vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list
