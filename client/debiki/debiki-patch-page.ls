/* Patches the page with new data from server, e.g. updates an edited comment.
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

d = i: debiki.internal, u: debiki.v0.util
$ = d.i.$;



onPagePatchedCallbacks = []


debiki.onPagePatched = !(callback) ->
  onPagePatchedCallbacks.push callback



/**
 * Inserts new replies and replaces old threads and edited posts with
 * new HTML provided by the server. And calls all onPagePatchedCallbacks.
 *
 * Returns and object with info on what
 * was patched.
 */
d.i.patchPage = (patches) ->
  result = patchedThreads: [], patchedPosts: []

  for pageId, threadPatches of patches.threadsByPageId || {}
    if pageId is d.i.pageId
      for patch in threadPatches
        patchThreadWith patch, { onPage: pageId, result }

  for pageId, postPatches of patches.postsByPageId || {}
    if pageId is d.i.pageId
      for patch in postPatches
        patchPostWith patch, { onPage: pageId, result }

  for c in onPagePatchedCallbacks
    c()

  result



patchThreadWith = (threadPatch, { onPage, result }) ->
  pageId = onPage
  isNewThread = ! $('#post-' + threadPatch.id).length
  $newThread = $ threadPatch.html

  if isNewThread
    $prevThread = d.i.findThread$ threadPatch.prevThreadId
    $parentThread = d.i.findThread$ threadPatch.ancestorThreadIds[0]
    if $prevThread.length
      insertThread $newThread, after: $prevThread
    else if $parentThread.length
      appendThread $newThread, to: $parentThread
    $newThread.addClass 'dw-m-t-new'
  else
    replaceOldWith $newThread, onPage: pageId

  $newThread.dwFindPosts!each !->
    d.i.$initPostAndParentThread.apply this
    d.i.showAllowedActionsOnly this

  drawArrows = ->
    # Really both $drawTree, and $drawParents for each child post??
    # (Not $drawPost; $newThread might have child threads.)
    $newThread.each d.i.SVG.$drawTree

    # 1. Draw arrows after post has been inited, because initing it
    # might change its size.
    # 2. If some parent is an inline post, *its* parent might need to be
    # redrawn. So redraw all parents.
    $newThread.dwFindPosts!.each d.i.SVG.$drawParents

  # Don't draw arrows until all posts have gotten their final position.
  # (The caller might remove a horizontal reply button, and show it again,
  # later, and the arrows might be drawn incorrectly if drawn inbetween.)
  setTimeout drawArrows, 0

  result.patchedThreads.push $newThread



patchPostWith = (postPatch, { onPage, result }) ->
  pageId = onPage
  $newPost = $ postPatch.html # absent if edit not applied
  $oldPost = $ ('#post-' + postPatch.postId)
  $newActions = $ postPatch.actionsHtml
  $oldActions = $oldPost.parent!children '.dw-p-as'
  isEditPatch = !!postPatch.editId

  if not isEditPatch
    void # Skip the messages below.
  else if !postPatch.isEditApplied
    addMessageToPost(
        'Your suggestions are pending review. Click the pen icon at the ' +
            'lower right corner of this comment, to view all suggections.',
        $oldPost)

  shallReplacePost = !isEditPatch || postPatch.isEditApplied
  if shallReplacePost
    $newPost.addClass 'dw-m-t-new'
    replaceOldWith $newPost, onPage: pageId

    $newPost.each d.i.$initPost

    $newThread = $newPost.dwClosestThread!
    $newThread.each d.i.SVG.$drawTree
    $newThread.dwFindPosts!.each d.i.SVG.$drawParents

  $oldActions.replaceWith $newActions

  editedPost =
    if shallReplacePost then $newPost[0] else $oldPost[0]

  d.i.bindActionLinksForSinglePost editedPost
  d.i.showAllowedActionsOnly editedPost

  result.patchedPosts.push $newThread


/**
 * Inserts a HTML message above the post.
 */
addMessageToPost = (message, $post) ->
  $post.prepend $(
      '<div class="dw-p-pending-mod">' + message + '</div>')


insertThread = ($thread, { after }) ->
  $pervSibling = after
  if $pervSibling.parent!is 'li'
    # Horizontal layout. Threads are wrapped in <li>s (with
    # display: table-cell).
    $thread = $('<li></li>').append $thread
    $pervSibling = $pervSibling.parent!
  $pervSibling.after $thread



appendThread = ($thread, { to }) ->
  $parent = to
  $childList = $parent.children '.dw-res'
  if !$childList.length
    # This is the first child thread; create empty child thread list.
    $childList = $("<ol class='dw-res'/>").appendTo $parent
  if $parent.is '.dw-hor'
    # Horizontal layout, see comment in `insertThread` above.
    $thread = $('<li></li>').append $thread
  $thread.appendTo $childList


replaceOldWith = ($new, { onPage }) ->
  # WOULD verify that $new is located `onPage`, if in the future it'll be
  # possible to edit e.g. blog posts from a blog post list page.
  $('#' + $new.attr 'id').replaceWith $new


# vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list
