'use strict';
/**
 *
 */

(function() {


var appCommand = angular.module('contextanalysemonitor', ['googlechart', 'ui.bootstrap','ngSanitize', 'ngModal', 'ngMaterial']);


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
appCommand.controller('ContextAnalyseControler',
	function ( $http, $scope,$sce,$filter ) {

	this.param={ 'taskid':20007, 'username':'Jan.Fisher'};
	this.inprogress=false;
	
	
	this.isInProgress = function()
	{
		console.log("InProgress="+this.inprogress);

		return this.inprogress;
	}
	
	this.callrestapi = function()
	{
		
		
		var self=this;
		self.inprogress=false;

		var url='/bonita/API/extension/context?taskId='+this.param.taskid+'&caseId='+this.param.caseid+'&processId='+this.param.processdefinitionid+'&log=true&analyse=true&usernameanalyse='+this.param.username;
		// http://localhost:8080/bonita/API/extension/context?taskId=2&caseId=2&&processId=2&log=true
		$http.get( url )
				.success( function ( jsonResult ) {
						console.log("history",jsonResult);
						// self.inprogress		= false;
						self.result 		= jsonResult;
				})
				.error( function() {
					alert('an error occure');
					// self.inprogress=false;
					})
				.finally(function() {
					// self.inprogress = false;
					console.log("finaly");
				});
					
				
	}

	// -----------------------------------------------------------------------------------------
	//  										Result
	// -----------------------------------------------------------------------------------------
	this.getResultVariable = function() {
		var listvar=[];
		for (var i in this.result)
		{
			
			if (i =="context")
				continue;
			var variabledef= { 'name':i,'value':this.result[ i ] }
			listvar.push( variabledef );
		}
		return listvar;
	} 
	
	this.getResultContext = function() {
		if (! this.result)
			return null;
		if (!this.result.context)
			return null;
		var contextvalue=this.result.context;
		var listvar=[];
		for (var i in contextvalue)
		{
			if ( i =="analysis")
				continue;
			var variabledef= { 'name':i,'value':contextvalue[i] }
			listvar.push( variabledef );
		}
		return listvar;
	} 
	
	this.getResultPermission = function() {
		if (! this.result || ! this.result.context || ! this.result.context.analysepermission)
			return null;
		return this.result.context.analysepermission;
		/*
		 * 
		 *var contextpermission=this.result.context.analysepermission;
		var listvar=[];
		for (var i in contextpermission)
		{
			var variabledef= { 'name':i,'value':contextpermission[i] }
			listvar.push( variabledef );
		}
		return listvar;
		*/
	} 
	this.getPermissionStyle =function(variableinfo) {
		if (variableinfo.result == true)
			return "background-color:#dff0d8";
		return "background-color:#f2dede";
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