'use strict'


describe 'AdminService', ->
  beforeEach module('AdminModule')

  ###
  describe 'loadNonEmptyFolders', ->
    it 'should list test folders', inject (AdminService) ->
      expect(AdminService.loadNonEmptyFolders()).toEqual
        '/': {}
        '/folder-1/': {}

  describe 'loadPagesInFolders', ->
    it 'should list nothing if no folders specified', inject (AdminService) ->
      expect(AdminService.loadPagesInFolders()).toEqual {}

    it 'should list pages in /', inject (AdminService) ->
      expect(AdminService.loadPagesInFolders(['/'])).toEqual
        '/':
          allPagesLoaded: true
          pagesById:
            p01:
              slug: ''
              showId: false
              authorsById:
                r935: { dispName: 'Rolf Rolander Rosengren' }
                r215: { dispName: 'Sven Svansson' }
            p02:
              slug: 'page-slug'
              showId: true
              authorsById:
                r215: { dispName: 'Bo Ek' }
  ###


###
describe 'AdminService2', ->

  adminService = undefined

  beforeEach ->
    module('AdminModule')()
    $injector = angular.injector ['AdminModule']
    adminService = $injector.get 'AdminService'

    #inject ($injector) ->
    #  adminService = $injector.get 'AdminService'
      #notify = $injector.get('notify');

  #describe 'version', ->
    #it 'should return current version', inject((version) ->
      #expect(version).toEqual('0.1')
    #)

  describe 'loadNonEmptyFolders', ->
    it 'should list example folders', ->
      expect(adminService.loadNonEmptyFolders()).toEqual
        '/': {}
        '/folder-1/': {}

describe 'AdminService', ->
  #?? beforeEach module('AdminModule')
  beforeEach ->
    #mock = {alert: jasmine.createSpy()};

    #module(function($provide) {
      #$provide.value('$window', mock);
    #});

    inject ($injector) ->
      adminService = $injector.get 'AdminService'

  it('should not alert first two notifications', function() {
    notify('one');
    notify('two');

    expect(mock.alert).not.toHaveBeenCalled();
  });

  it('should alert all after third notification', function() {
    notify('one');
    notify('two');
    notify('three');

    expect(mock.alert).toHaveBeenCalledWith("one\ntwo\nthree");
  });

  it('should clear messages after alert', function() {
    notify('one');
    notify('two');
    notify('third');
    notify('more');
    notify('two');
    notify('third');

    expect(mock.alert.callCount).toEqual(2);
    expect(mock.alert.mostRecentCall.args).toEqual(["more\ntwo\nthird"]);
  });

###
# vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list
