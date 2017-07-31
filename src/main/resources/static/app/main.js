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

            add: function(region, definition, title) {
                return $http({
                    method: 'POST',
                    url: '/config/'+session.tenantId+'/'+region,
                    data: definition,
                    params: {
                        title: title
                    },
                    headers: {
                        'Content-Type': 'text/plain'
                    }
                })
            },

            remove: function(region, id) {
                return $http.delete('/config/'+session.tenantId+'/'+region+'/'+id)
            },

            currentTenant: function() {
                return session.tenantId;
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
                ConfigApi.add(details.region, details.definition, details.title)
                    .then(function success(resp) {
                        $mdToast.show(
                            $mdToast.simple()
                                .textContent('Added '+resp.data.id)
                        );

                        getConfigs();
                    })
            })
        };
    })

    .controller('AddController', function($scope, $mdDialog) {
        $scope.regions = ['west', 'central', 'east'];
        $scope.region = $scope.regions[0];
        $scope.title = '';
        $scope.definition = '';

        $scope.examples = [
            {
                label: "HTTP response",
                definition: '[[inputs.http_response]]\n' +
                '  ## Server address (default http://localhost)\n' +
                '  address = "https://www.rackspace.com"\n' +
                '  ## Set response_timeout (default 5 seconds)\n' +
                '  response_timeout = "5s"\n' +
                '  ## HTTP Request Method\n' +
                '  method = "GET"\n' +
                '  ## Whether to follow redirects from the server (defaults to false)\n' +
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
            }
        ];

        $scope.$watch('selectedExample', function (val) {
            $scope.definition = val;
        });

        $scope.add = function() {
            $mdDialog.hide({
                region: $scope.region,
                title: $scope.title,
                definition: $scope.definition
            })
        };

        $scope.cancel = function () {
            $mdDialog.cancel();
        };
    })
;