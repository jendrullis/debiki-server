/* Shows action links (Reply, Edit, etc buttons) and binds to Javascript.
 * Copyright (C) 2010 - 2012 Kaj Magnus Lindberg (born 1979)
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


(function() {

var d = { i: debiki.internal, u: debiki.v0.util };
var $ = d.i.$;


d.i.bindActionAndFoldLinksForSinglePost = function(post) {
  bindActionLinksImpl(post, true)
};


d.i.bindActionLinksForSinglePost = function(post) {
  bindActionLinksImpl(post, false)
};


/** Handling all posts at once is perhaps 10x faster than one post at a time,
  * if there are 200-300 posts.
  */
d.i.bindActionLinksForAllPosts = function() {
  bindActionLinksImpl(undefined);
};


function bindActionLinksImpl(anyPost, bindFoldLinks) {
  var $actions, $collapses;
  var collapseSelectors =
      '.dw-t > .dw-z,' +
      '.dw-res.dw-zd > li > .dw-z,' +
      '.dw-p.dw-zd > .dw-z';

  if (anyPost) {
    $actions = $(anyPost).parent().children('.dw-as');
    var $thread = $actions.parent();
    if (bindFoldLinks) {
      $collapses = $thread.children('.dw-z');
      $collapses.add($thread.find(collapseSelectors));
    }
    else {
      $collapses = $();
    }
  }
  else {
    $actions = $('.dw-t > .dw-as, .dw-p-as-hz');
    $collapses = $(collapseSelectors);
  }

  // On delegating events for reply/rate/edit.
  // Placing a jQuery delegate on e.g. .debiki instead, entails that
  // these links are given excessively low precedence on Android:
  // on a screen touch, any <a> nearby that has a real click event
  // is clicked instead of the <a> with a delegate event. The reply/
  // reply/rate/edit links becomes virtually unclickable (if event
  // delegation is used instead).

  $actions.children('.dw-a-reply').click(d.i.$showReplyForm);
  $actions.children('.dw-a-rate').click(d.i.$showRatingForm);
  $actions.children('.dw-a-more').click(function(event) {
    event.preventDefault();
    $(this).closest('.dw-p-as').find('.dw-p-as-more')
        .show()
        .end().end().remove();
  });

  $actions.find('.dw-a-edit').click(d.i.$showEditsDialog);
  $actions.find('.dw-a-flag').click(d.i.$showFlagForm);
  $actions.find('.dw-a-delete').click(d.i.$showDeleteForm);
  $actions.find('.dw-a-collapse-post').click(d.i.$showActionDialog('CollapsePost'));
  $actions.find('.dw-a-collapse-tree').click(d.i.$showActionDialog('CollapseTree'));

  $actions.find('.dw-a-flag-suggs').click(showNotImplMessage);

  // Action links are shown on hover.
  $actions.css('visibility', 'hidden');
  // But show the article's reply button.
  $('.dw-p-as-hz').css('visibility', 'visible');

  $collapses.click(d.i.$toggleCollapsed);
};


function showNotImplMessage() {
  alert("Not implemented, sorry");
}


/** Takes long? Can be made 5-10 x faster if I do it for all posts at once?
  */
d.i.shohwActionLinksOnHoverPost  = function(post) {
  var $thread = $(post).dwCheckIs('.dw-p').closest('.dw-t');
  var $post = $thread.filter(':not(.dw-depth-0)').children('.dw-p');
  var $actions = $thread.children('.dw-p-as');

  // (Better avoid delegates for frequent events such as mouseenter.)
  $post.add($actions).mouseenter(function() {
    var $i = $(this);

    // If actions are already shown for an inline child post, ignore event.
    // (Sometimes the mouseenter event is fired first for an inline child
    // post, then for its parent — and then actions should be shown for the
    // child post; the parent should ignore the event.)
    var inlineChildActionsShown = $i.find('#dw-p-as-shown').length;

    // If the post is being edited, show no actions.
    // (It's rather confusing to be able to Reply to the edit <form>.)
    var isBeingEdited = $i.children('.dw-f-e:visible').length;

    if (isBeingEdited)
      d.i.hideActions();
    else if (!inlineChildActionsShown)
      $i.each(d.i.$showActions);
    // else leave actions visible, below the inline child post.
  });

  $thread.mouseleave(function() {
    // If this is an inline post, show the action menu for the parent post
    // since we're hovering that post now.
    $(this).closest('.dw-p').each(d.i.$showActions);
  });
};


// Shows actions for the current post, or the last post hovered.
d.i.$showActions = function() {
  // Hide any action links already shown; show actions for one post only.
  d.i.hideActions();
  // Show links for the the current post.
  $(this).closest('.dw-t').children('.dw-as')
    .css('visibility', 'visible')
    .attr('id', 'dw-p-as-shown');
};


d.i.hideActions = function() {
  $('#dw-p-as-shown')
      .css('visibility', 'hidden')
      .removeAttr('id');
};


$.fn.dwActionLinkEnable = function() {
  setActionLinkEnabled(this, true);
  return this;
};


$.fn.dwActionLinkDisable = function() {
  setActionLinkEnabled(this, false);
  return this;
};


function setActionLinkEnabled($actionLink, enabed) {
  if (!$actionLink.length) return;
  d.u.bugIf(!$actionLink.is('.dw-a'));
  if (!enabed) {
    // (Copy the event list; off('click') destroys the original.)
    var handlers = $actionLink.data('events')['click'].slice();
    $actionLink.data('DisabledHandlers', handlers);
    $actionLink.addClass('dw-a-disabled').off('click');
  } else {
    var handlers = $actionLink.data('DisabledHandlers');
    $actionLink.removeData('DisabledHandlers');
    $.each(handlers, function(index, handler) { 
      $actionLink.click(handler);
    });
    $actionLink.removeClass('dw-a-disabled');
  }
};


})();

// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list
