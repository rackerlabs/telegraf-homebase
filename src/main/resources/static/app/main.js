angular.module("ConfigApp", [
    'ui.router',
    'ngMaterial'
])

    .config(function($stateProvider, $urlRouterProvider) {

        var loginState = {
            name: 'login',
            url: '/login',
            templateUrl: 'views/login.html',
            controller: 'LoginController'
        };

        var listState = {
            name: 'list',
            url: '/list',
            templateUrl: 'views/list.html',
            controller: 'ListController'
        };

        $stateProvider.state(loginState);
        $stateProvider.state(listState);

        $urlRouterProvider.when('', '/login');
    })

    .config(function($compileProvider) {
        // will be removed in 1.7.0
        $compileProvider.preAssignBindingsEnabled(true);
    })

    .factory('ConfigApi', function($http) {
        var session = {
            tenantId: 'ac-1'
        };

        return {
            login: function(tenantId) {
                session.tenantId = tenantId;
            },

            getConfigs: function() {
                return $http.get('/config/'+session.tenantId);
            },

            addRegional: function(region, definition, title) {
                return $http({
                    method: 'POST',
                    url: '/config/'+session.tenantId+'/regional',
                    data: {
                        regions: [region],
                        text: definition,
                        title: title
                    }
                })
            },

            addAssigned: function(tags, definition, title) {
                return $http({
                    method: 'POST',
                    url: '/config/'+session.tenantId+'/assigned',
                    data: {
                        text: definition,
                        title: title,
                        assignmentTags: tags
                    }
                })
            },

            remove: function(region, id) {
                return $http.delete('/config/'+session.tenantId+'/'+id)
            },

            currentTenant: function() {
                return session.tenantId;
            },

            tags: function() {
                return $http.get('/config/'+session.tenantId+'/tags');
            }
        }
    })

    .controller('LoginController', function($scope, $state, ConfigApi){
        $scope.tenantId = ConfigApi.currentTenant();

        $scope.login = function() {
            ConfigApi.login($scope.tenantId);
            $state.go('list')
        }
    })

    .controller('ListController', function($scope, $mdDialog, $mdToast, ConfigApi){

        $scope.configs = [];
        function getConfigs(showToast) {
            ConfigApi.getConfigs().then(function success(resp){
                $scope.configs = resp.data;
                if (showToast) {
                    $mdToast.show(
                        $mdToast.simple()
                            .textContent('Loaded configs')
                    );
                }
            });
        }
        getConfigs();
        $scope.reload = function() {
            getConfigs(true);
        };

        $scope.configApi = ConfigApi;

        $scope.remove = function(c) {
            ConfigApi.remove(c.region, c.id).then(function success(resp) {
                $mdToast.show(
                    $mdToast.simple()
                        .textContent('Removed '+c.id)
                );

                getConfigs();
            })
        };

        $scope.add = function() {
            $mdDialog.show({
                templateUrl: 'dialogs/add.html',
                controller: 'AddController',
                fullscreen: true
            }).then(function ok(details){
                var result;
                if (details.region != null) {
                    result = ConfigApi.addRegional(details.region, details.definition, details.title);
                } else {
                    result = ConfigApi.addAssigned(details.tags, details.definition, details.title);
                }

                result
                    .then(function success(resp) {
                        $mdToast.show(
                            $mdToast.simple()
                                .textContent('Added '+ resp.data.created[0])
                        );

                        getConfigs();
                    }, function failed(resp){
                        $mdToast.show(
                            $mdToast.simple()
                                .textContent('ERROR: '+resp.data.message+' ('+resp.status+')')
                                .toastClass('toast-error')
                                .action('Close')
                                .hideDelay(0)
                        );
                    })
            })
        };
    })

    .filter('excluding', function() {
        return function(input, excludingThese) {
            return _.omitBy(input, function(val,key) {
                return _.has(excludingThese, key);
            });
        }
    })

    .controller('AddController', function($scope, $mdDialog, ConfigApi) {
        $scope.regions = ['west', 'central', 'east'];
        $scope.region = $scope.regions[0];
        $scope.title = '';
        $scope.definition = '';
        $scope.selectedModeIndex = 0;
        $scope.tags = {};
        $scope.currentTagName = null;
        $scope.selectedTags = {};

        $scope.$watch('selectedModeIndex', function (val) {
            if (val === 1) {
                ConfigApi.tags().then(
                    function success(resp) {
                        $scope.tags = resp.data;
                    }
                )
            }
        });

        $scope.examples = [
            {
                label: "HTTP response",
                definition: '[[inputs.http_response]]\n' +
                '  address = "https://www.rackspace.com"\n' +
                '  response_timeout = "5s"\n' +
                '  method = "GET"\n' +
                '  follow_redirects = true'
            },
            {
                label: 'SNMP',
                definition: '[[inputs.snmp]]\n' +
                '  agents = [ "10.5.4.157:161" ]\n' +
                '  version = 2\n' +
                '  community = "public"\n' +
                '\n' +
                '  name = "printer"\n' +
                '  [[inputs.snmp.field]]\n' +
                '    name = "name"\n' +
                '    oid = ".1.3.6.1.2.1.1.5.0"\n' +
                '    is_tag = true\n' +
                '  [[inputs.snmp.field]]\n' +
                '    name = "total-page-count-letter-simplex"\n' +
                '    oid = ".1.3.6.1.4.1.11.2.3.9.4.2.1.1.16.1.1.13.0"\n' +
                '  [[inputs.snmp.field]]\n' +
                '    name = "total-page-count-letter-duplex"\n' +
                '    oid = ".1.3.6.1.4.1.11.2.3.9.4.2.1.1.16.1.1.14.0"'
            },
            {
                label: 'Memory',
                definition: '[[inputs.mem]]'
            }
        ];

        $scope.$watch('selectedExample', function (val) {
            $scope.definition = val;
        });

        $scope.add = function() {
            $mdDialog.hide({
                region: $scope.selectedModeIndex === 0 ? $scope.region : null,
                title: $scope.title,
                definition: $scope.definition,
                tags: $scope.selectedModeIndex === 1 ? $scope.selectedTags : null
            })
        };

        $scope.addTag = function() {
            $scope.selectedTags[$scope.currentTagName] = $scope.currentTagValue;
            $scope.currentTagName = null;
            $scope.currentTagValue = null;
        };

        $scope.cancel = function () {
            $mdDialog.cancel();
        };
    })
;