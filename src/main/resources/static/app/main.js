angular.module("ConfigApp", [
    'ngRoute',
    'ngMaterial'
])

    .config(function($routeProvider, $locationProvider) {
        $routeProvider
            .when('/list', {
                templateUrl: 'views/list.html',
                controller: 'ListController'
            })
            .otherwise({
                templateUrl: 'views/login.html',
                controller: 'LoginController'
            });
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
                return $http.get('/config');
            },

            add: function(region, definition, comment) {
                return $http({
                    method: 'POST',
                    url: '/config/'+session.tenantId+'/'+region,
                    data: definition,
                    params: {
                        comment: comment
                    },
                    headers: {
                        'Content-Type': 'text/plain'
                    }
                })
            },

            remove: function(region, id) {
                return $http.delete('/config/'+session.tenantId+'/'+region+'/'+id)
            }
        }
    })

    .controller('LoginController', function($scope, $location, ConfigApi){
        $scope.tenantId = "ac-1";

        $scope.login = function() {
            ConfigApi.login($scope.tenantId);
            $location.url("/list");
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
                ConfigApi.add(details.region, details.definition, details.comment)
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
        $scope.comment = "";
        $scope.definition = '[[inputs.http_response]]\n' +
            '  ## Server address (default http://localhost)\n' +
            '  address = "https://www.rackspace.com"\n' +
            '  ## Set response_timeout (default 5 seconds)\n' +
            '  response_timeout = "5s"\n' +
            '  ## HTTP Request Method\n' +
            '  method = "GET"\n' +
            '  ## Whether to follow redirects from the server (defaults to false)\n' +
            '  follow_redirects = true';

        $scope.add = function() {
            $mdDialog.hide({
                region: $scope.region,
                comment: $scope.comment,
                definition: $scope.definition
            })
        };

        $scope.cancel = function () {
            $mdDialog.cancel();
        };
    })
;