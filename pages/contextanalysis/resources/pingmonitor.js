'use strict';
/**
 *
 */

(function() {


var appCommand = angular.module('pingmonitor', ['googlechart', 'ui.bootstrap','ngSanitize', 'ngModal', 'ngMaterial']);


/* Material : for the autocomplete
 * need 
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-animate.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-aria.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-messages.min.js"></script>

  <!-- Angular Material Library -->
  <script src="https://ajax.googleapis.com/ajax/libs/angular_material/1.1.0/angular-material.min.js">
 */



// --------------------------------------------------------------------------
//
// Controler Ping
//
// --------------------------------------------------------------------------

// Ping the server
appCommand.controller('PingControler',
	function ( $http, $scope,$sce,$filter ) {

	this.pingdate='';
	this.pinginfo='';
	this.listevents='';
	this.inprogress=false;
	
	this.ping = function()
	{
		
		this.pinginfo="Hello";
		
		var self=this;
		self.inprogress=true;
		
		$http.get( '?page=custompage_ping&action=ping' )
				.success( function ( jsonResult ) {
						console.log("history",jsonResult);
						self.pingdate 		= jsonResult.pingcurrentdate;
						self.pinginfo 		= jsonResult.pingserverinfo;
						self.listprocesses	= jsonResult.listprocesses;
						self.listusers		= jsonResult.listusers;
						self.listevents		= jsonResult.listevents;
						
						$scope.chartObject		 	= JSON.parse(jsonResult.chartObject);
		
						self.inprogress=false;
						
						
				})
				.error( function() {
					alert('an error occure');
					self.inprogress=false;
					});
				
	}


	// -----------------------------------------------------------------------------------------
	//  										Autocomplete
	// -----------------------------------------------------------------------------------------
	this.autocomplete={};
	
	this.queryUser = function(searchText) {
		var self=this;
		console.log("QueryUser HTTP CALL["+searchText+"]");
		
		self.autocomplete.inprogress=true;
		self.autocomplete.search = searchText;
 
		var param={ 'userfilter' :  self.autocomplete.search};
		
		
		var json = encodeURI( angular.toJson( param, false));
		
		return $http.get( '?page=custompage_ping&action=queryusers&jsonparam='+json )
		.then( function ( jsonResult ) {
			console.log("QueryUser HTTP SUCCESS.1 - result= "+angular.toJson(jsonResult, false));
				self.autocomplete.inprogress=false;
			 	self.autocomplete.listUsers =  jsonResult.data.listUsers;
				console.log("QueryUser HTTP SUCCESS length="+self.autocomplete.listUsers.length);
				return self.autocomplete.listUsers;
				},  function ( jsonResult ) {
				console.log("QueryUser HTTP THEN");
		});

	  };
	  
	// -----------------------------------------------------------------------------------------
	//  										Excel
	// -----------------------------------------------------------------------------------------

	this.exportData = function () 
	{  
		//Start*To Export SearchTable data in excel  
	// create XLS template with your field.  
		var mystyle = {         
        headers:true,        
			columns: [  
			{ columnid: 'name', title: 'Name'},
			{ columnid: 'version', title: 'Version'},
			{ columnid: 'state', title: 'State'},
			{ columnid: 'deployeddate', title: 'Deployed date'},
			],         
		};  
	
        //get current system date.         
        var date = new Date();  
        $scope.CurrentDateTime = $filter('date')(new Date().getTime(), 'MM/dd/yyyy HH:mm:ss');          
		var trackingJson = this.listprocesses
        //Create XLS format using alasql.js file.  
        alasql('SELECT * INTO XLS("Process_' + $scope.CurrentDateTime + '.xls",?) FROM ?', [mystyle, trackingJson]);  
    };
    

	// -----------------------------------------------------------------------------------------
	//  										Properties
	// -----------------------------------------------------------------------------------------
	this.propsFirstName='';
	this.saveProps = function() {
		var self=this;
		var param={ 'firstname': this.propsFirstName };
					  
		var json = encodeURI( angular.toJson( param, false));
		$http.get( '?page=custompage_ping&action=saveprops&jsonparam='+json )
				.success( function ( jsonResult ) {
						console.log("history",jsonResult);
						self.listevents		= jsonResult.listevents;
						alert('Properties saved');
				})
				.error( function() {
					alert('an error occure');
					});
	}
	
	this.loadProps =function() {
		var self=this;
		$http.get( '?page=custompage_ping&action=loadprops' )
				.success( function ( jsonResult ) {
						console.log("history",jsonResult);
						self.propsFirstName = jsonResult.firstname;
						self.listevents		= jsonResult.listevents;

				})
				.error( function() {
					alert('an error occure');
					});
	}
	this.loadProps();

	
	<!-- Manage the event -->
	this.getListEvents = function ( listevents ) {
		return $sce.trustAsHtml(  listevents );
	}
	<!-- Manage the Modal -->
	this.isshowDialog=false;
	this.openDialog = function()
	{
		this.isshowDialog=true;
	};
	this.closeDialog = function()
	{
		this.isshowDialog=false;
	}

});



})();