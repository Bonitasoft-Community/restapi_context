package com.bonitasoft.rest.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.lang.reflect.Field;

import groovy.json.JsonBuilder
import groovy.json.JsonException;
import groovy.json.JsonSlurper;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.http.HttpHeaders

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse












import org.bonitasoft.engine.api.APIClient;
import org.bonitasoft.engine.api.BusinessDataAPI;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.ProfileAPI;
import org.bonitasoft.engine.bpm.data.ArchivedDataInstance;
import org.bonitasoft.engine.bpm.data.ArchivedDataNotFoundException;
import org.bonitasoft.engine.bpm.data.DataInstance;
import org.bonitasoft.engine.bpm.data.DataNotFoundException;
import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.bpm.document.DocumentCriterion;
import org.bonitasoft.engine.bpm.document.DocumentDefinition;
import org.bonitasoft.engine.bpm.document.DocumentListDefinition;
import org.bonitasoft.engine.bpm.document.DocumentsSearchDescriptor;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.bpm.flownode.FlowElementContainerDefinition;
import org.bonitasoft.engine.bpm.parameter.ParameterCriterion;
import org.bonitasoft.engine.bpm.parameter.ParameterInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bdm.Entity;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;
import org.bonitasoft.engine.business.data.BusinessDataReference;
import org.bonitasoft.engine.business.data.MultipleBusinessDataReference;
import org.bonitasoft.engine.business.data.SimpleBusinessDataReference;
import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.web.extension.ResourceProvider
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import org.bonitasoft.engine.profile.Profile;
import org.bonitasoft.web.extension.rest.RestApiController;
import org.bonitasoft.web.extension.rest.RestAPIContext;
 

/**
* See page.properties for list of version

 */
class GetContext implements RestApiController {

	private static final Logger logger = LoggerFactory.getLogger(GetContext.class);


	private static final String cstActionCaseId = "caseId";
	private static final String cstActionProcessDefinitionId = "processDefinitionId";
	private static final String cstActionIsCaseArchived = "isCaseArchived";
	private static final String cstActionTaskId = "taskId";
	private static final String cstActionisTaskArchived = "isTaskArchived";


	/**
	 * it's one of this possibility
	 */
	public enum enuTypeUrl { PROCESSINSTANCIATION, CASEOVERVIEW, TASKEXECUTION, UNKNOW };

	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	class ContextCaseId																*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	// a Case has multiple access : a ProcessInstance or a ArchiveProcessInstance, a TaskInstance or an ArchiveTaskInstance...
	private static class ContextCaseId {
		Long taskInstanceId = null;
		Long processInstanceId = null;
		Long processDefinitionId = null;
		 
		ProcessInstance processInstance=null;
		ActivityInstance activityInstance = null;
		ArchivedProcessInstance archivedProcessInstance = null;
		ArchivedActivityInstance archivedActivityInstance=null;

			
		public Long getProcessDefinitionId()
		{
			if ( processInstance!=null)
				return processInstance.getProcessDefinitionId();
			if (archivedProcessInstance!=null)
				return archivedProcessInstance.getProcessDefinitionId();
			return processDefinitionId;			
		}

		public String trace()
		{
			String trace="ContextCaseid:";
			trace += (processInstance!=null ? "ProcessInstance[Open-"+processInstance.getId()+"],":"")
			trace += (archivedProcessInstance!=null ? "ProcessInstance[Archived-"+archivedProcessInstance.getId()+"/"+archivedProcessInstance.getSourceObjectId() +"],":"")
			trace += (activityInstance!=null ? "ActivityInstance[Active-"+activityInstance.getId()+"],":"")
			trace += (archivedActivityInstance!=null ? "ActivityInstance[Archived-"+archivedActivityInstance.getId()+"],":"");
			trace +=  "processDefinitionId["+processDefinitionId+"],";
			return trace;
		}
		
		/**
		 * context Data to control what we send back to the result
		 */
		Map<String,Object> contextData;
		boolean isAllowAllVariables=false;
		public void setContextData( Map<String,Object> ctx)
		{
			contextData = ctx;
			isAllowAllVariables=false;
			for (Object varNameIt : contextData.keySet())
			{
				if ("*".equals(varNameIt))
				{
					isAllowAllVariables=true;
				}
			}
		}
		
		public Map<String,Object>  getContextData()
		{
			return contextData;
		}
		
		public boolean isAllowVariableName(String varName )
		{
			if (isAllowAllVariables)
				return true;
			for (Object varNameIt : contextData.keySet())
			{
				if (varNameIt.equals(varName))
					return true;
			}
			return false;			
				
		}

	}



	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	class PerformanceTrace															*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	public static class PerformanceTrace {
		private List<Map<String,Object>> listOperations = new ArrayList<Map<String,Object>>();
		public void addMarker(String operation)
		{
			long currentTime= System.currentTimeMillis();
			Map<String,Object> oneOperation = new HashMap<String,Object>();
			oneOperation.put("t",System.currentTimeMillis());
			oneOperation.putAt("n",operation);
			listOperations.add( oneOperation );
		}

		public String trace()
		{
			String result="";
			for (int i=1;i<listOperations.size();i++)
			{
				String time = ((long) listOperations.get( i ).getAt("t")) - ((long)listOperations.get( i-1 ).getAt("t"));
				result+= listOperations.get( i ).getAt("n")+":"+time+" ms,";
			}
			String time = ((long) listOperations.get( listOperations.size()-1 ).getAt("t")) - ((long)listOperations.get( 0 ).getAt("t"));
			result+="Total "+time+" ms";
			return result;
		}
	}


	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	dohandle																		*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	@Override
	RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {
		// To retrieve query parameters use the request.getParameter(..) method.
		// Be careful, parameter values are always returned as String values
		PerformanceTrace performanceTrace = new PerformanceTrace();
		performanceTrace.addMarker("start");

		// Here is an example of how you can retrieve configuration parameters from a properties file
		// It is safe to remove this if no configuration is required
		Map<String,Object> rootResult = new HashMap<String,Object>();
		Map<String,Object> contextResult= new HashMap<String,Object>();
		
		DataInstance contextData=null;
		String sourceContextData="";
		String contextDataSt = null;
		boolean isLog=false;
		Boolean isLogFromParameter=null;
		
		def detectTypeUrl = enuTypeUrl.UNKNOW;
		
		try
		{
			APIClient apiClient = context.apiClient;
			APISession apiSession = context.getApiSession();
			ProcessAPI processAPI = apiClient.processAPI;
			IdentityAPI identityAPI = apiClient.identityAPI;
			ProfileAPI profileAPI = apiClient.profileAPI;
			BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;
			ContextCaseId contextCaseId = new ContextCaseId();

			// the URL can be call in the UI Designe with 
			// ../API/extension/context?taskId={{taskId}}&processId={{processId}}
			// because the same form may be used in a PROCESS INSTANCTIATION or in a TASK.
			// Pb : in the URL, there are only a ID which contains the processDefinitionId (form Instantiation) or taskId (task)
			// So, how can we detect we are in fact in a PROCESS INSTANCIATION or in a TASK ? 
			// the complete URL is in fact  
			// http://localhost:8080/bonita/portal/resource/process/Aegrotat/1.22/API/extension/context?taskId=8994946062106177132&processId=89949460621
			// http://localhost:8080/bonita/portal/resource/taskInstance/Aegrotat/1.22/Review%20Medical/API/extension/context?taskId=66313&processId=66313
			// so we add a new control based on the URL.
		
			String url = request.getParameter("url");
			
			if (url !=null)
			{
				// /bonita/portal/resource/process/ExpenseNote/1.0/content/==> Create
				// /bonita/portal/resource/processInstance/ExpenseNote/1.0/content/==> Overview
				// /bonita/portal/resource/taskInstance/ExpenseNote/1.0/Modify/content/  ==> Modify
				if (url.indexOf("resource/process/")!=-1)
					detectTypeUrl= enuTypeUrl.PROCESSINSTANCIATION;
				else if (url.indexOf("resource/processInstance/")!=-1)
					detectTypeUrl= enuTypeUrl.CASEOVERVIEW;
				else if (url.indexOf("resource/taskInstance/")!=-1)
					detectTypeUrl= enuTypeUrl.TASKEXECUTION;

				logger.info(" URL ["+url+"] isProcessInstanciation="+(detectTypeUrl == enuTypeUrl.PROCESSINSTANCIATION)+", isProcessOverview="+(detectTypeUrl == enuTypeUrl.CASEOVERVIEW)+", isTaskExecution="+(detectTypeUrl == enuTypeUrl.TASKEXECUTION));
			}
			
			
			//----------------------------  decode each parameters
			try
			{
				// attention, if we suppose this is a FormInstantiation task, we don't try to get the taskId
				if (request.getParameter("taskId")!=null &&  detectTypeUrl != enuTypeUrl.PROCESSINSTANCIATION)
					contextCaseId.taskInstanceId = Long.valueOf( request.getParameter("taskId"));
			} catch(Exception e )
			{
				sourceContextData+="Error with taskId["+request.getParameter("taskId")+"] : "+e.toString();
			};
			
			try
			{
				if (request.getParameter("processId")!=null)
					contextCaseId.processDefinitionId = Long.valueOf( request.getParameter("processId"));
			} catch(Exception e )
			{
				sourceContextData+="Error with processId["+request.getParameter("processId")+"] : "+e.toString();			
			};
			
			
			try
			{
				if (request.getParameter("log")!=null)
				{
					isLogFromParameter = Boolean.valueOf( request.getParameter("log"));
					isLog = isLogFromParameter;
				}
			}
			catch(Exception e) {
				logError( rootResult, "a Boolean is expected for the parameters log ["+request.getParameter("log")+"]" );
			}
			
			// let's go
			logRest( isLog,"=================== GetContext RESTAPI");
			
			//----------------- get the perimeter (taskid or processInstanceId or ProcessId)
			try
			{
				if (contextCaseId.taskInstanceId!=null)
				{
					ActivityInstance activityInstance = processAPI.getActivityInstance( contextCaseId.taskInstanceId);
					contextCaseId.processInstanceId = activityInstance.getParentContainerId();
				}
			} catch(Exception e ) 
			{
				logger.info(" taskInstanceId=["+contextCaseId.taskInstanceId+"] Exception ["+e.toString()+"]");
				
				// Actually, if the user give a ProcessId that's mean that we are in the task instantiation. Because with the UI Designer, there are no way 
				// to give a taskId=xxx&processId=xxx : the only parameter in the url is "id" !
				if (contextCaseId.processDefinitionId !=null)
				{
					contextCaseId.taskInstanceId=null;
					contextCaseId.processInstanceId=null;
				}
				else
					sourceContextData+="Error with taskId["+request.getParameter("taskId")+"] : "+e.toString();
			};
		

			
            // may be updated by the taskId		
			if (contextCaseId.processInstanceId == null) {
				try
				{
					if (request.getParameter("caseId")!=null && detectTypeUrl != enuTypeUrl.PROCESSINSTANCIATION)
						contextCaseId.processInstanceId = Long.valueOf( request.getParameter("caseId"));
				} catch(Exception e ) 
				// no worry if an exception arrived here : that's mean the user doesn't give a taskId, error is manage after (no taskid+noCaseId=error)
				{
					sourceContextData+="Error with caseId["+request.getParameter("caseId")+"] : "+e.toString();
				
				};
			}
			
			
			if (contextCaseId.processInstanceId == null && contextCaseId.processDefinitionId==null)
			{
				logError( rootResult, "Parameter [taskId] or [caseId] or [processId] required ("+sourceContextData+")");
				return;
			}


			String defaultDateFormat= request.getParameter("dateformat");
			if ("DATELONG".equalsIgnoreCase(defaultDateFormat))
			{
				defaultDateFormat= DateFormat.DATELONG;
				sourceContextData+="DateFormat[DATELONG];";
			}
			if ("DATETIME".equalsIgnoreCase(defaultDateFormat))
			{
				defaultDateFormat= DateFormat.DATETIME;
				sourceContextData+="DateFormat[DATETIME];";
			}
			if ("DATEJSON".equalsIgnoreCase(defaultDateFormat))
			{
				defaultDateFormat= DateFormat.DATEJSON;
				sourceContextData+="DateFormat[DATEJSON];";
			}
			String version = request.getParameter("version");
			if (version!=null)
				contextResult.put("version", "2.5");
				
			
			// ------------------ retrieve correct information
			// if the processinstance exist ? The task Id ?

			try
			{
				contextCaseId.processInstance = processAPI.getProcessInstance( contextCaseId.processInstanceId);
				if (contextCaseId.taskInstanceId !=null)
					contextCaseId.activityInstance = processAPI.getActivityInstance( contextCaseId.taskInstanceId);
			}
			catch(Exception e)
			{
				// no worry if an exception arrived here : that's mean it's maybe an Archive InstanceId
				// logRest( isLog, "No processinstance found by ["+ contextCaseId.processInstanceId+"] : "+e.toString() );
				// sourceContextData+="Error getting Object processInstance and ActivityInstance from processId["+contextCaseId.processInstanceId+"] activityId["+contextCaseId.taskInstanceId+"] : "+e.toString();
			}
			// same with archived... yes, in Bonita, class are different...
			try
			{
				if (contextCaseId.processInstance ==null)
					contextCaseId.archivedProcessInstance = processAPI.getFinalArchivedProcessInstance( contextCaseId.processInstanceId );
				if (contextCaseId.taskInstanceId !=null && contextCaseId.activityInstance == null)
					contextCaseId.archivedActivityInstance = processAPI.getArchivedActivityInstance( contextCaseId.taskInstanceId );
			}
			catch(Exception e)
			{
				// no worry if an exception arrived here : that's mean it's maybe an InstanceId : so error will be managed after
				// logRest( isLog, "No ArchivedProcessinstance found by ["+contextCaseId.processInstanceId+"] : "+e.toString() );
			}
			if (contextCaseId.processInstance == null && contextCaseId.archivedProcessInstance == null && contextCaseId.processDefinitionId==null)
			{
				contextResult.put("caseId", contextCaseId.processInstanceId);
				logError(rootResult, "caseId unknown");
				return;
			}
			if (contextCaseId.taskInstanceId != null && contextCaseId.activityInstance==null && contextCaseId.archivedActivityInstance==null)
			{
				contextResult.put("caseId", contextCaseId.processInstanceId);
				contextResult.put("taskId", contextCaseId.taskInstanceId);
				logError(rootResult, "taskId unknown");
				return;
			}


			sourceContextData +=contextCaseId.trace();
			performanceTrace.addMarker("Detectparameter");

			//----------------------- get the pilot
			if (contextCaseId.taskInstanceId !=null)
			{
				try
				{
					contextData = processAPI.getActivityDataInstance("context", contextCaseId.taskInstanceId);
					contextDataSt = contextData.getValue();
					sourceContextData+="ActivityDataInstance[context] value="+contextData.getValue();
				} catch(DataNotFoundException dnte ) 
				// Ok, no worry, let's try different options
				{}
			}
			if (contextData == null && contextCaseId.processInstanceId !=null)
			{
				try
				{
					contextData = processAPI.getProcessDataInstance("globalcontext", contextCaseId.processInstanceId);
					contextDataSt = contextData.getValue();
					sourceContextData+="ProcessDataInstance[globalcontext] value="+contextData.getValue();
				} catch(DataNotFoundException dnte ) 
				// ok, maybe no context where given ?
				{}
			}
			if (contextData == null && contextCaseId.archivedProcessInstance!=null)
			{
				try
				{
					contextData = processAPI.getArchivedProcessDataInstance("globalcontext", contextCaseId.archivedProcessInstance.getSourceObjectId());
					contextDataSt = contextData.getValue();
					sourceContextData+="ArchivedProcessDataInstance[globalcontext] value="+contextData.getValue();
				} catch(ArchivedDataNotFoundException dnte ) 
				// still Ok, search after
				{}
			}
			
			// no data were given : create a default one
			if (contextData == null)
			{
				contextDataSt = "{ \"*\" : \"all\",";
				contextDataSt += ", \""+cstActionCaseId+"\" : \""+cstActionCaseId+"\"";
				contextDataSt += ", \""+cstActionProcessDefinitionId+"\" : \""+cstActionProcessDefinitionId+"\"";
				contextDataSt += ", \""+cstActionIsCaseArchived+"\" : \""+cstActionIsCaseArchived+"\"";
				contextDataSt += ", \""+cstActionTaskId+"\" : \""+cstActionTaskId+"\"";
				contextDataSt += ", \""+cstActionisTaskArchived+"\" : \""+cstActionisTaskArchived+"\"";
				contextDataSt += "}";


				sourceContextData+="Default value="+contextDataSt;
			}

			performanceTrace.addMarker("getPilot");


			logRest( isLog, "SourceContextData = "+sourceContextData);
			if (isLog)
			{
				contextResult.putAt("sourcecontextdata", sourceContextData);
			}
			//--------------- return the content
			JsonSlurper slurper = new JsonSlurper();
			Object contextDataMap = slurper.parseText(contextDataSt);
			if (contextDataMap == null )
			{
				// is acceptable if we ask for the processId
				if ( ! (contextCaseId.processInstance == null && contextCaseId.archivedProcessInstance==null))
					logError(rootResult, "The JSON information is missing : "+sourceContextData);
			}
			else if (! contextDataMap instanceof Map)
			{
				logError( rootResult,  "Local Variable [context] must be a MAP");
			}
			else
			{
				
				performanceTrace.addMarker("JsonParse");
				contextCaseId.setContextData((Map<String,Object>) contextDataMap );
				
				// decode the Log
				if (isLogFromParameter==null) {
					try
					{
						isLog = Boolean.valueOf( contextDataMap.get("RESTCONTEXTISLOG" ) );
					}
					catch( Exception e)
					// Ok, if the value is not given or it's not a Boolean, no worry
					{}
				}

				// get the content now
				getContent( rootResult, isLog,  contextCaseId, performanceTrace, apiClient );
				
			}
			
			if (contextCaseId.processInstance == null && contextCaseId.archivedProcessInstance==null && contextCaseId.processDefinitionId!=null && (detectTypeUrl != enuTypeUrl.CASEOVERVIEW))
			{
				// logger.info(" processInstance/archived=["+contextCaseId.processInstance+"/"+contextCaseId.archivedProcessInstance+"] taskId["+contextCaseId.taskInstanceId+"] isProcessInstanciation="+isProcessInstanciation+", isProcessOverview="+isProcessOverwiew+", isTaskExecution="+isTaskExecution);
				detectTypeUrl = enuTypeUrl.PROCESSINSTANCIATION;
			}
			if (contextCaseId.activityInstance != null)
			{
				detectTypeUrl= enuTypeUrl.TASKEXECUTION;
			}
			// process initialisation ?
			contextResult.put("isProcessInstanciation", Boolean.valueOf( detectTypeUrl == enuTypeUrl.PROCESSINSTANCIATION ) );
			contextResult.put("isProcessOverview", Boolean.valueOf( detectTypeUrl == enuTypeUrl.CASEOVERVIEW));
			contextResult.put("isTaskExecution", Boolean.valueOf( detectTypeUrl == enuTypeUrl.TASKEXECUTION));

				
			// is this user is an administrator ?
			contextResult.putAt("isAdministrator", false);
				
			List<Profile> listProfiles=profileAPI.getProfilesForUser(apiSession.getUserId());
			for (Profile profile : listProfiles)
			{
				if (profile.getName().equals("Administrator"))
					contextResult.putAt("isAdministrator", true);
			}
			User user =identityAPI.getUser(apiSession.getUserId());
			contextResult.put("userid", user.getId());
			contextResult.put("username", user.getUserName());
			contextResult.put("processdefinitionid", contextCaseId.processDefinitionId)
			contextResult.put("taskid", contextCaseId.taskInstanceId)
			contextResult.put("caseid", contextCaseId.processInstanceId); // contextCaseId.taskInstanceId 
			
		
			
			performanceTrace.addMarker("getFinalResult");
			if (isLog)
			{
				logRest( isLog, "Final rootResult "+rootResult.toString())
				logRest( isLog,"Performance :"+performanceTrace.trace() );
				contextResult.put("performanceRestContextCall", performanceTrace.trace() );
			}
			
			// and now put the context in the result (nota : we may overwride by this way the context variable)
			rootResult.put("context", contextResult);
			
		} catch(DataNotFoundException dnte )
		{
			logError( rootResult, "Expect [context] or [globalcontext] variable to pilot what to search");
		}
		catch (JsonException je)
		{
			logError( rootResult,"Bad JSON "+sourceContextData+" : "+je.toString());
		}
		catch(Exception e )
		{
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionDetails = sw.toString();
			logError( rootResult, e.toString()+" at "+exceptionDetails);

		}
		finally {
			logRest(isLog,"=================== End GetContext RESTAPI");

			// Send the result as a JSON representation
			// You may use buildPagedResponse if your result is multiple
			return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(rootResult).toPrettyString())
		}
	}

	
	
	/**
	 * get the content of the REST API
	 * @param isLog
	 * @param contextCaseId
	 * @param context
	 */
	private void getContent( Map<String,Object> rootResult,
			boolean isLog, 
			ContextCaseId contextCaseId, 
			PerformanceTrace performanceTrace2,
			APIClient apiClient) 
	{
		// get the list of all Business Data
		ProcessAPI processAPI = apiClient.processAPI;
		BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

		Map<String,BusinessDataReference> listBusinessData= new HashMap<String,BusinessDataReference>();
		
		
		if (contextCaseId.processInstanceId!=null)
		{
			logRest(isLog, "Collect BusinessData from: processinstanceid["+contextCaseId.processInstanceId+"]");
			
			List<BusinessDataReference>  tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.processInstanceId, 0,1000);
			if (tempList!=null)
			{
				logRest(isLog, "Collect BusinessData from: processinstanceid["+tempList.size()+"]");
				for (BusinessDataReference bde : tempList)
					listBusinessData.put( bde.getName(), bde );

			}
		}
		// from the archivedProcessInstance now
		if (contextCaseId.archivedProcessInstance!=null)
		{
			
			// logger.info(">>> *BEGIN* ArchivedProcessInstanceExecutionContext<<");
			Map<String,Serializable> map = processAPI.getArchivedProcessInstanceExecutionContext(contextCaseId.archivedProcessInstance.getId());
			for (String key : map.keySet() )
			{
				if (map.get( key ) instanceof BusinessDataReference)
				{
					// we got an archive Business Data Reference !
					// logger.info(">>> Detect["+key+"] businessVariable");
					BusinessDataReference bde = (BusinessDataReference) map.get( key ) ;
					listBusinessData.put( bde.getName(), bde );
				}
			}
			logRest(isLog, "Collect BusinessData from: getArchivedProcessInstanceExecutionContext :archivedProcessInstance.getId() ["+listBusinessData.size()+"]");
			// logger.info(">>> *END* ArchivedProcessInstanceExecutionContext<<");

			List<BusinessDataReference>  tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
			if (tempList!=null)
			{
				logRest(isLog, "Collect BusinessData from: archivedActivityInstance.getSourceObjectId() ["+tempList.size()+"]");
				for (BusinessDataReference bde : tempList)
					listBusinessData.put( bde.getName(), bde );

			}
			tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getId(), 0,1000);
			if (tempList!=null)
			{
				logRest(isLog, "Collect BusinessData from: archivedActivityInstance.getId() ["+tempList.size()+"]");
				for (BusinessDataReference bde : tempList)
					listBusinessData.put( bde.getName(), bde );
			}
		}
		performanceTrace2.addMarker("collectListBusinessData");


		// now, process the list
		for (Object varName : contextCaseId.contextData.keySet())
		{
			String varAction = contextCaseId.contextData.get( varName ) !=null ? contextCaseId.contextData.get( varName ).toString() : null;
			logRest(isLog, "Loop Get variable["+varName+"] / action["+varAction+"]");

			if (varName.equals("*"))
			{
				Long instanceForBdm = null;
				//------------ active part
				if (contextCaseId.processInstance!=null)
				{
					instanceForBdm = contextCaseId.processInstance.getId();
					List<DataInstance> listDataInstance = processAPI.getProcessDataInstances(contextCaseId.processInstance.getId(), 0,1000);
					for (DataInstance data : listDataInstance)
					{
						logRest(true, "************************** DataInstance detected ["+data.getName()+"]");
						completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.contextData, isLog);
					}
					performanceTrace2.addMarker("getAllProcessData");
				}

				if (contextCaseId.activityInstance!=null)
				{
					List<DataInstance> listDataInstance = processAPI.getActivityDataInstances(contextCaseId.activityInstance.getId(), 0,1000);
					for (DataInstance data : listDataInstance)
					{
						completeValueProcessVariable(rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.contextData, isLog);
					}
					performanceTrace2.addMarker("getAllActivityData");

				}
				// ----- archived part
				if (contextCaseId.archivedProcessInstance!=null)
				{
					instanceForBdm = contextCaseId.archivedProcessInstance.getId();
					List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedProcessDataInstances(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
					for (ArchivedDataInstance data : listDataInstance)
					{
						completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.contextData, isLog);
					}
					performanceTrace2.addMarker("getAllArchivedProcessData");

				}
				if (contextCaseId.archivedActivityInstance!=null)
				{
					List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedActivityDataInstances(contextCaseId.archivedActivityInstance.getSourceObjectId(), 0,1000);
					for (ArchivedDataInstance data : listDataInstance)
					{
						completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.contextData, isLog);
					}
					performanceTrace2.addMarker("getAllArchivedActivityData");

				}



				// ---------------------- business Data
				// logRest(isLog, "Search BDM with processInstanceId=["+contextCaseId.processInstanceId+"] instanceForBdm="+instanceForBdm);
				//logRest(isLog, "contextCaseId.trace() =["+contextCaseId.trace()+"]");
				// logRest(isLog, "archivedProcessInstance =["+contextCaseId.archivedProcessInstance+"]");

				for (BusinessDataReference businessData : listBusinessData.values())
				{
					logRest(isLog, "Loop Get BDM["+businessData.getName()+"] / type["+businessData.getType()+"]");
					completeValueBdmData( rootResult, businessData, contextCaseId, apiClient, contextCaseId.contextData, isLog );
				}
				performanceTrace2.addMarker("getBusinessData");

			    //--------------------- parameters
				List<ParameterInstance>	listParameters = processAPI.getParameterInstances(contextCaseId.processDefinitionId, 0, 100, ParameterCriterion.NAME_ASC);
				if (listParameters!=null)
					for (ParameterInstance parameter : listParameters)
						rootResult.put( parameter.getName(), parameter.getValue());
				
			}
			else if (cstActionCaseId.equals(varAction) && (contextCaseId.processInstanceId != null)) {
				rootResult.put(varName, contextCaseId.processInstanceId);
				// logRest(isLog,"cstActionCaseId :  new Result["+rootResult+"]");
			}

			else if (cstActionProcessDefinitionId.equals(varAction))
				rootResult.put( varName, contextCaseId.getProcessDefinitionId());


			else if (cstActionIsCaseArchived.equals(varAction) && (contextCaseId.processInstanceId != null))
				rootResult.put( varName, contextCaseId.processInstance==null );

			else if (cstActionTaskId.equals(varAction) && contextCaseId.taskInstanceId !=null)
				rootResult.put( varName, contextCaseId.taskInstanceId);

			else if (cstActionisTaskArchived.equals(varAction) && contextCaseId.taskInstanceId !=null)
				rootResult.put( varName, contextCaseId.activityInstance == null);
			else
			{

				// We want to load the data varName : is that a business Data ?
				if (listBusinessData.containsKey( varName ))
				{
					completeValueBdmData( rootResult, listBusinessData.get( varName ), contextCaseId, apiClient, contextCaseId.contextData, isLog );
					performanceTrace2.addMarker("getBdmData["+varName+"]");
				}
				else
				{
					completeValueProcessVariable( rootResult, varName, varAction, contextCaseId, apiClient, contextCaseId.contextData, isLog);
					performanceTrace2.addMarker("getData["+varName+"]");
				}

				


			}

		} // end of list
		
		
		// process the document
		/* produce per document:
		{"src":
			{"id":501,"processInstanceId":5001,"name":"oneFile",
				"author":4,"creationDate":1479345110650,
				"fileName":"2013-05-14 09.09.23.jpg",
				"contentMimeType":"image/jpeg",
				"contentStorageId":"501",
				"url":"documentDownload?fileName=2013-05-14 09.09.23.jpg&contentStorageId=501",
				"description":"",
				"version":"1",
				"index":-1,
				"contentFileName":"2013-05-14 09.09.23.jpg"},
				*/
		
		if (contextCaseId.processInstanceId!=null)
		{
			List<Document> listDocuments = processAPI.getLastVersionOfDocuments(contextCaseId.processInstanceId, 0,100, DocumentCriterion.NAME_ASC);
			
			
			for (Document oneDoc : listDocuments)
			{
				if (oneDoc == null)
					continue;
					
				if (! contextCaseId.isAllowVariableName(oneDoc.getName()))
					continue;

				logRest(isLog, "************************** Doc detected ["+(oneDoc==null ? null : (oneDoc.getName()+" index="+oneDoc.getIndex()))+"]");
					
				Map<String,String> oneDocumentMap = new HashMap<String,Object>();
				oneDocumentMap.put("id", oneDoc.getId());
				oneDocumentMap.put("processInstanceId", contextCaseId.processInstanceId);
				oneDocumentMap.put("name", oneDoc.getName());
				oneDocumentMap.put("author", oneDoc.getAuthor());
				oneDocumentMap.put("creationDate", oneDoc.getCreationDate().getTime());
				oneDocumentMap.put("fileName", oneDoc.getContentFileName());
				oneDocumentMap.put("contentMimeType", oneDoc.getContentMimeType());
				oneDocumentMap.put("contentStorageId", oneDoc.getContentStorageId());
				oneDocumentMap.put("url", oneDoc.getUrl());
				oneDocumentMap.put("description", oneDoc.getDescription() );
				oneDocumentMap.put("version", oneDoc.getVersion());
				oneDocumentMap.put("index", oneDoc.getIndex());
				oneDocumentMap.put("contentFileName", oneDoc.getContentFileName());
				oneDocumentMap.put("hasContent", oneDoc.hasContent());
	
				Map<String,String> ctxDocumentMap = new HashMap<String,Object>();
				ctxDocumentMap.put("src", oneDocumentMap );
				
				if (oneDoc.getIndex() ==-1)
				{
					// not a multiple
					rootResult.put( oneDoc.getName(), ctxDocumentMap);
				}
				else
				{
					List listDocs= rootResult.get( oneDoc.getName());
					if (listDocs==null)
						listDocs = new ArrayList();
					// we may have the index 5 when at this moment the list contains only 2 item : extends the list
					while (listDocs.size()<= oneDoc.getIndex())
						listDocs.add( null );
					listDocs.set( oneDoc.getIndex(),ctxDocumentMap); 
					rootResult.put( oneDoc.getName(), listDocs);
				}
				
			}
		} else 
		{
			// detect all document and create some empty variable
			DesignProcessDefinition designProcessDefinition = processAPI.getDesignProcessDefinition( contextCaseId.processDefinitionId );
			FlowElementContainerDefinition flowElementContainerDefinition = designProcessDefinition.getFlowElementContainer();
			List<DocumentDefinition> listDocumentDefinition =	flowElementContainerDefinition.getDocumentDefinitions();
			for (DocumentDefinition documentDefinition : listDocumentDefinition )
				rootResult.put( documentDefinition.getName(), new HashMap());
				
			List<DocumentListDefinition> listDocumentListDefinition =	flowElementContainerDefinition.getDocumentListDefinitions();
			for (DocumentListDefinition documentListDefinition : listDocumentListDefinition )
				rootResult.put( documentListDefinition.getName(), new ArrayList());
		
		}
		
	}
	
	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	buildResponse																	*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	/**
	 * Build an HTTP response.
	 *
	 * @param  responseBuilder the Rest API response builder
	 * @param  httpStatus the status of the response
	 * @param  body the response body
	 * @return a RestAPIResponse
	 */
	RestApiResponse buildResponse(RestApiResponseBuilder responseBuilder, int httpStatus, Serializable body) {
		return responseBuilder.with {
			withResponseStatus(httpStatus)
			withResponse(body)
			build()
		}
	}

	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	buildPageResponse																*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	/**
	 * Returns a paged result like Bonita BPM REST APIs.
	 * Build a response with content-range data in the HTTP header.
	 *
	 * @param  responseBuilder the Rest API response builder
	 * @param  body the response body
	 * @param  p the page index
	 * @param  c the number of result per page
	 * @param  total the total number of results
	 * @return a RestAPIResponse
	 */
	RestApiResponse buildPagedResponse(RestApiResponseBuilder responseBuilder, Serializable body, int p, int c, long total) {
		return responseBuilder.with {
			withAdditionalHeader(HttpHeaders.CONTENT_RANGE,"$p-$c/$total");
			withResponse(body)
			build()
		}
	}


	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	completeValueProcessVariable																	*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	/**
	 * search the variable
	 * @param rootResult : the rootResult to complete.
	 * @param varName : the name of the variable to describe
	 * @param processInstanceId
	 * @param taskInstanceId
	 */
	private void completeValueProcessVariable( Map<String,Object> rootResult,
			String varName,
			String varAction,
			ContextCaseId contextCaseId,
			APIClient apiClient,
			Map<String,Object> contextDataMap,
			boolean isLog)
	{

		ProcessAPI processAPI = apiClient.processAPI;
		BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

		logRest( isLog, "=== completeValueProcessVariable.begin: Get variable["+varName+"] varAction["+varAction+"] contextDataMap["+contextDataMap+"]");


		if (contextCaseId.processInstance != null)
			try
			{
				DataInstance dataInstance = processAPI.getProcessDataInstance(varName.toString(), contextCaseId.processInstance.getId() );
				logRest( isLog, "completeValueProcessVariable: Get variable["+varName+"] is a PROCESSDATA.a : ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
				completeValueFromData( rootResult, dataInstance.getName(), dataInstance.getValue(), varAction, contextDataMap, isLog);
				return;
			} catch (DataNotFoundException dnte) {};

		if (contextCaseId.activityInstance != null)
			try
			{
				// logger.info("Try get localvariable["+varName+"]");
				DataInstance dataInstance = processAPI.getActivityDataInstance(varName.toString(), contextCaseId.activityInstance.getId() );
				logRest( isLog, "completeValueProcessVariable: Get variable["+varName+"] is a ACTIVITYDATA: ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
				completeValueFromData( rootResult, dataInstance.getName(), dataInstance.getValue(), varAction, contextDataMap, isLog );
				return;
			} catch (DataNotFoundException dnte) {};

		if (contextCaseId.archivedProcessInstance != null)
			try
			{
				logRest( isLog, "completeValueProcessVariable: search variable["+varName+"] in getArchivedProcessDataInstance");
				ArchivedDataInstance archivedDataInstance = processAPI.getArchivedProcessDataInstance (varName.toString(), contextCaseId.archivedProcessInstance.getSourceObjectId() );
				logRest( isLog, "completeValueProcessVariable: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

				completeValueFromData( rootResult, archivedDataInstance.getName(), archivedDataInstance.getValue(), varAction,contextDataMap, isLog );
				return;
			} catch (ArchivedDataNotFoundException dnte) {};

		if (contextCaseId.archivedActivityInstance != null)
		{

			try
			{
				logRest( isLog, "completeValueProcessVariable: search variable["+varName+"] in getArchivedActivityDataInstance");
				ArchivedDataInstance archivedDataInstance = processAPI. getArchivedActivityDataInstance( varName.toString(), contextCaseId.archivedActivityInstance.getSourceObjectId() );
				logRest( isLog, "completeValueProcessVariable: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

				completeValueFromData( rootResult, archivedDataInstance.getName(),archivedDataInstance.getValue(), varAction, contextDataMap, isLog);
				return;
			} catch (ArchivedDataNotFoundException dnte) {};
		}


		logRest( isLog, "=== completeValueProcessVariable.end: Get variable["+varName+"]");
		
		return;
	}

	/**
	 * save the value in the rootResult. If the value is a Obect 
	 */
	private void completeValueFromData( Map<String,Object> rootResult, String varName, Object varValue, String varAction,Map<String,Object> contextDataMap, boolean isLog )
	{
		Object contextAction = contextDataMap.get( varName );
		
		logRest( isLog, "========================= completeValueFromData.begin: Get variable["+varName+"] action["+varAction+"] contextAction["+contextAction+"] contextDataMap["+contextDataMap+"] value["+varValue+"] ");
		if (contextAction instanceof Map)
		{
			logRest( isLog, " completeValueFromData.: Action is MAP : "+contextAction);
			// attention, we may have at this point an object : it's time to transform it in MAP, LIST, ...
			Object varValueTransformed = varValue;
			if (varValueTransformed!= null)
				if (! ( ( varValueTransformed instanceof Map) || (varValueTransformed instanceof List)))
				{
					logRest( isLog, " completeValueFromData.: Transform the object to MAP : "+varValueTransformed);					
					String jsonSt = new JsonBuilder( varValueTransformed ).toPrettyString();					
					varValueTransformed = new JsonSlurper().parseText(jsonSt);
					logRest( isLog, " completeValueFromData.: Transform the objecin MAP : "+varValueTransformed);				
				}
			
			
			// we have a Map like { Attribut1: xxx}
			if (varValueTransformed instanceof Map)
			{
				logRest( isLog, " completeValueFromData.: Value is MAP : "+varValueTransformed);
				Map<String,Object> subResult = new HashMap<String,Object>();
				rootResult.put(varName, subResult);
				for (String key : contextAction.keySet())
				{
					logRest( isLog, " completeValueFromData. recursiv call : key["+key+"] varValueTransformed.get( key )["+varValueTransformed.get( key )+"] contextAction["+contextAction+"]");
					
					completeValueFromData( subResult, key, varValueTransformed.get( key ), null, contextAction, isLog);
				}
			}
			else if (varValueTransformed instanceof List)
			{
				logRest( isLog, " completeValueFromData.: Value is LIST : "+varValueTransformed);
				// Ok, apply the action on each element of the list
				List<Map<String,Object>> subResult = new ArrayList<Map<String,Object>>();
				rootResult.put(varName, subResult);
				for (int i=0; i< ((List) varValueTransformed).size();i++)
				{
					Map<String,Object> subResultIterator = new HashMap<String,Object>();
					subResult.add(subResultIterator);
					for (String key : contextAction.keySet())
					{
						completeValueFromData( subResultIterator, key, ((List) varValueTransformed).getAt( i ).get( key ), null, contextAction, isLog);
					}
	
				}
				
			}
			else if (varValue == null)
			{
				rootResult.put(varName,null);
			}
			else
			{
				logRest( isLog, " completeValueFromData.: Value is not a MAP and not a LIST do nothing : "+varValue.getClass().getName());
				// action is a MAP and the value is not... not, do nothing here
			}
		} // end of varcontext as a Map
		else
		{
			logRest( isLog, " completeValueFromData.: Direct transformation ("+(contextAction==null ? "data" : contextAction.toString())+"]");
			rootResult.put(varName, transformValue( varValue, contextAction==null ? "data" : contextAction.toString()) );
		}
		// special case for an enum
		if (varValue instanceof Enum)
		{
			List<String> listOptions = new ArrayList<String>();
			// get the enummeration
			Object[] options = ((Enum) varValue).values();
			for ( Object oneOption : options)
			{
				listOptions.add( oneOption.toString() );				
			}
			rootResult.put(varName+"_list", listOptions );
		}
		logRest( isLog, "========================= completeValueFromData.end: Get variable["+varName+"]");
		
	}
	
	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	completeValueBdmData															*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	/**
	 * LoadBdmValue
	 */
	private void completeValueBdmData( Map<String,Object> rootResult,
			BusinessDataReference businessData,
			ContextCaseId contextCaseId,
			APIClient apiClient,
			Map<String,Object> contextDataMap,
			boolean isLog)
	{

		ProcessAPI processAPI = apiClient.processAPI;
		BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

		// logger.info("completeValueBdmData: Get variable["+businessData.getName()+"]");

		try
		{

			// logger.info("completeValueBdmData.2: Get Business Reference ["+businessData.getName()+"]");

			// the result is maybe a HASHMAP or a LIST<HASMAP>
			Object resultBdm = null;
			boolean isMultiple=false;
			List<Long> listStorageIds = new ArrayList<Long>();
			if (businessData instanceof MultipleBusinessDataReference)
			{
				// this is a multiple data
				
				isMultiple=true;
				logRest(isLog,"completeValueBdmData.3 Get MULTIPLE Business Reference ["+businessData.getName()+"] : type["+businessData.getType()+"]");
				if (((MultipleBusinessDataReference) businessData).getStorageIds()==null)
					listStorageIds.add( null ); // add a null value to have a result (bdm name + null) and geet the resultBdm as null 
				else
				{
					resultBdm = new ArrayList<HashMap<String,Object>>();
					listStorageIds.addAll( ((MultipleBusinessDataReference) businessData).getStorageIds());
				}
			}
			if (businessData instanceof SimpleBusinessDataReference)
			{
				resultBdm = new HashMap<String,Object>();
				isMultiple=false;
				logRest(isLog,"completeValueBdmData.3: Get SIMPLE Business Reference ["+businessData.getName()+"] : type["+businessData.getType()+"]");
				// if null, add it even to have a result (bdm name + null)
				listStorageIds.add( ((SimpleBusinessDataReference) businessData).getStorageId());
			}
			// logger.info("completeValueBdmData.3bis : Set ["+resultBdm+"] in result");

		
			String classDAOName = businessData.getType()+"DAO";
			// logger.info("completeValueBdmData.4: Get Business Reference ["+businessData.getName()+"] it's a BDM-type["+businessData.getType()+"] classDao=["+classDAOName+"]");

			Class classDao = Class.forName( classDAOName );
			if (classDao==null)
			{
				// a problem here...
				logError( rootResult, "completeValueBdmData.5:>>>>>>>>>>>>>>>>>>>>  DaoClass ["+classDAOName+"] not Found");

				return;
			}
			//logger.info("completeValueBdmData.5:classDao Loaded ["+classDao.getName()+"]");


			BusinessObjectDAO dao = apiClient.getDAO( classDao );

			// logger.info("completeValueBdmData.6:Dao loaded : dao["+ dao +"] listStorageIds["+listStorageIds+"]");

			// now, check each BDM
			for (Long storageId : listStorageIds)
			{
				
				HashMap saveOneBdm = null;
				if (isMultiple)
				{
					saveOneBdm = storageId==null? null: new HashMap<String,Object>();
					resultBdm.add( saveOneBdm );
				}
				else
				{
					saveOneBdm = resultBdm;
					
					if (storageId==null)
						resultBdm=null; // in this situation, we want to have only one null at the end, and we know that the listStorageIds has only one item
				}
				
				if (storageId==null)
				{
					continue;
				}
				// logger.info("completeValueBdmData.7: Get Business Reference ["+businessData.getName()+"] : type["+businessData.getType()+"] storageId["+storageId+"]");

				Entity dataBdmEntity = dao.findByPersistenceId(storageId);
				if (dataBdmEntity==null)
				{
					logError( rootResult, "The BDM variable["+businessData.getName()+"] storageId["+storageId+"] does not exist anymore " );
					return;
				}
				// logger.info("completeValueBdmData.8: Got the BdmEntity");


				Class classBdmEntity= dataBdmEntity.getClass();
				logger.info("completeValueBdmData.9: got the class["+classBdmEntity.getName()+"]");

				// Start the recursive
				// example: variable is summerOrder : {}
				// Bdm is summerOrder -> Lines -> Ticket
				// saveOneBdm : this is the local level at this moment
				// ContextDataMap.get("summerorder") give the context to work (example
				// "summerOrder" : {
				//              "name": "data",
				//              " ticket": "*",
				//              "lines" : {  "linename" : "data",
				//                           "ticket" : { "solicitante" : "data" },
				//				         	 "price":"data"
				//	  		    }
				// }

				loadBdmVariableOneLevel(rootResult, saveOneBdm, dataBdmEntity, contextDataMap.get( businessData.getName() ), isLog );


			}
			
			// save the result now
			logRest(isLog,"completeValueBdmData.6: Final result ["+businessData.getName()+"] : value["+resultBdm+"]");			
			rootResult.put( businessData.getName(), resultBdm);
			
			
			// def dao = context.apiClient.getDAO(MyDao.class)
			// def data= dao.findByPersistenceId(ref.storageId)
		} catch (Exception e) {
			logError(rootResult, "Error during get Business Data Information: "+e.toString());
		};

		return;
	}

	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	loadBdmVariableOneLevel															*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	/*
	 * load recursively the BDM Variable
	 * 
	 * saveLocalLevel : attributes are save in this level
	 * dataBdmEntity is the current variable.
	 * contextLocalLevel is the current level of context to apply
	 * Ex : 
	 * 
	 * data: summerOrder
	 * Context : {
	 "name": "data",
	 "ticket": "*",
	 "lines" : {  "linename" : "data",
	 "ticket" : { "solicitante" : "data" },
	 "price":"data"
	 }
	 * We run all the different method.
	 * getName() ==> name is reference, save it
	 * getVendor() ==> vendor is not referenced, skip it
	 * getTicket() is a BDM so get ticket : referenced, then call recursively. To say "*" then the sub context is NULL
	 * getLines() is a BDM : call recursively given { "linename..." as the sub context} 
	 */
	private void loadBdmVariableOneLevel( Map<String,Object> rootResult,
			Map<String,Object> saveLocalLevel,
			Entity dataBdmEntity,
			Map<String,Object> contextLocalLevel,
			boolean isLog)
	{
		Class classBdmEntity= dataBdmEntity.getClass();
		// logger.info("loadBdmVariableOneLevel.10a ---------loadBdmVariableOneLevel class["+classBdmEntity.getName()+"] contextLocalLevel["+contextLocalLevel.toString()+"]");

		
		Map<String,String> privateFields = new HashMap<String,String>();
		Field[] declaredFields = dataBdmEntity.getClass().getDeclaredFields();
		Field[] fields         = dataBdmEntity.getClass().getFields();
		Method[] listMethods   = classBdmEntity.getMethods();
		Class[] listClasses    = classBdmEntity.getClasses();
		//logger.info("loadBdmVariableOneLevel.10a ---------loadBdmVariableOneLevel class["+classBdmEntity.getName()+"] declaredFields["+declaredFields.length+"] fields["+fields.length+"] Classes["+listClasses.length);

		for (Class onClass : listClasses) {
			//logger.info("loadBdmVariableOneLevel.10b ---------loadBdmVariableOneLevel class["+classBdmEntity.getName()+"] class["+onClass.getName()+"]");
		}
		
		for (Field field : declaredFields) {
			final Class<?> fieldType = field.getType();
			//logger.info("loadBdmVariableOneLevel.10b ---------loadBdmVariableOneLevel class["+classBdmEntity.getName()+"] declaredFields.fieldName["+field.getName()+"] fieldType["+fieldType.toString());
			
			
			/* if (shouldSkipField(fieldType)) {
				          continue;
			}
			*/
				
			privateFields.put( field.getName().toLowerCase(), field.getName());
		}
		for (Field field : fields) {
			final Class<?> fieldType = field.getType();
			//logger.info("loadBdmVariableOneLevel.10b ---------loadBdmVariableOneLevel class["+classBdmEntity.getName()+"] fields.fieldName["+field.getName()+"] fieldType["+fieldType.toString());
			
			
			/* if (shouldSkipField(fieldType)) {
						  continue;
			}
			*/
				
			privateFields.put( field.getName().toLowerCase(), field.getName());
		}
			
			

		//	logger.info("Field= "+listFields+" Methods="+listMethods);
		for (Method method : listMethods)
		{
			try
			{
				// logger.info("loadBdmVariableOneLevel.10.c method["+method.getName()+"]");

				if ( (method.getName().startsWith("get") || method.getName().startsWith("is")) && method.getParameterTypes().length == 0
				&& ! "getClass".equals(method.getName())
				&& ! "getPersistenceVersion".equals(method.getName())
				&& ! "getHandler".equals(method.getName()) )
				{
					// call it !
					// logger.info("method=["+method.getName()+"]");

					Object value = method.invoke(dataBdmEntity, new Object[0]);
					// logger.info("loadBdmVariableOneLevel.10b method["+method.getName()+"] Result=["+value+"]");
					String nameAttribute = method.getName();

					if (nameAttribute.startsWith("get"))
					{
						nameAttribute = nameAttribute.substring(3); // getInvoice => Invoice
						nameAttribute = nameAttribute.substring(0,1).toLowerCase()+nameAttribute.substring(1);
					}
					else if (nameAttribute.startsWith("is"))
					{
						nameAttribute = nameAttribute.substring(2); // isInvoice => Invoice
						nameAttribute = nameAttribute.substring(0,1).toLowerCase()+nameAttribute.substring(1);
					}

					// search the Realname
					boolean keepIt=true;
					// logger.info("loadBdmVariableOneLevel.10d method["+method.getName()+"] nameAttribut["+nameAttribute	+"] RealName["+privateFields.get(nameAttribute.toLowerCase())+"] Result=["+value+"] keepIt="+keepIt+" Entity ? "+(value!=null && value instanceof Entity)+" classValue=["+(value !=null ? value.getClass().getName() : "null")+"]");
				
					if (privateFields.get(nameAttribute.toLowerCase())!=null)
						nameAttribute = privateFields.get(nameAttribute.toLowerCase());
					
					// ok, the context pilot now
					if (contextLocalLevel!=null)
					{
						Object contextInfo = contextLocalLevel.get(nameAttribute);
						keepIt = contextInfo != null;
					}
					// logger.info("loadBdmVariableOneLevel.10b method["+method.getName()+"] nameAttribut["+nameAttribute+"] Result=["+value+"] keepIt="+keepIt+" Entity ? "+(value!=null && value instanceof Entity)+" classValue=["+(value !=null ? value.getClass().getName() : "null")+"]");


					if (!keepIt)
						continue;

					// is that a BDM ? Attention, 2 case : a Entity or a List of Entity (Bdm can be multiple)
					boolean isABdm=false;
					if (value!=null)
					{
						if (value instanceof Entity)
							isABdm=true;
						if (value instanceof List)
						{
							if (((List) value).size()>0)
							{
								Object firstValue= ((List) value).getAt(0);
								isABdm = firstValue instanceof Entity;
								// logger.info("loadBdmVariableOneLevel.10c Value is a list firstValue=["+firstValue+"]");
							}
						}
					}

					if (isABdm)
					{
						// logger.info("loadBdmVariableOneLevel.10d SubChild detected");

						Object contextInfo = contextLocalLevel== null ? null: contextLocalLevel.get(nameAttribute);
						if (contextInfo instanceof String && "*".equals(contextInfo))
							contextInfo=null;
						// logger.info("loadBdmVariableOneLevel.10e SubChild contextInfo["+contextInfo+"]");

						// Ok, this is a Entity or a list of Entity. So, we have to create a Map or a List of Map
						if (value instanceof Entity)
						{
							Map<String,Object> bdmChild = new HashMap<String,Object>();
							saveLocalLevel.put(nameAttribute, bdmChild);
							loadBdmVariableOneLevel(rootResult, bdmChild, value, contextInfo, isLog )
						}
						if (value instanceof List)
						{
							List valueList = (List) value;
							List<Map<String,String>> listBdmChild=new ArrayList();
							saveLocalLevel.put(nameAttribute, listBdmChild);
							for ( Object valueInList : valueList )
							{
								Map<String,Object> bdmChild = new HashMap<String,Object>();
								listBdmChild.add( bdmChild );
								loadBdmVariableOneLevel(rootResult, bdmChild, valueInList, contextInfo, isLog )
							}

						}
					}
					else
						saveLocalLevel.put(nameAttribute, transformValue( value, contextLocalLevel==null ? null : contextLocalLevel.get( nameAttribute) ));

					// logger.info("loadBdmVariableOneLevel.10c saveOneBdm ="+saveLocalLevel.toString());

				}
			}
			catch( Exception e)
			{
				logError( rootResult, "Error during exploring the Bdm variable ["+dataBdmEntity.getClass().getName()+"] : "+e.toString() );
			}
		}

	}

	protected boolean shouldSkipField(Class<?> fieldType) {
		return fieldType.equals(javassist.util.proxy.MethodHandler.MethodHandler.class);
	}
		
	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	transformValue																	*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	/**
	 * Transform the value accord
	 */
	private static SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

	public enum DateFormat { DATELONG, DATETIME, DATEJSON };
	private DateFormat defaultDateFormat= DateFormat.DATELONG;
	private Object transformValue( Object data, String varAction )
	{
		if (data==null)
			return null;
		logger.info("========= TransformData["+data+"] varAction["+varAction+"]")
		if (data instanceof Date)
		{
			if ("date".equals(varAction))
				return sdfDate.format( (Date) data);
			else if ("datetime".equals(varAction))
				return sdfDateTime.format( (Date) data);
			else if ("datelong".equals(varAction))
				return ((Date) data).getTime();
			
			// use the default
			if (defaultDateFormat==DateFormat.DATELONG )
				return ((Date) data).getTime();
			if (defaultDateFormat==DateFormat.DATETIME )
				return sdfDateTime.format( (Date) data);
			if (defaultDateFormat==DateFormat.DATEJSON )
				return sdfDate.format( (Date) data);

			// default : be compatible with the UIDesigner which wait for a timestamp.
			return ((Date) data).getTime();
				
		}
		if (data instanceof List)
		{
			List<Object> listTransformed= new ArrayList<Object>();
			for (Object onItem : ((List) data))
			{
				// propagate the varAction to transform the date
				listTransformed.add( transformValue( onItem, varAction ));
			}
			return listTransformed;
		}
		return data;
	}

	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	logRest																			*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	private void logRest( boolean isLog, String logExplanation)
	{
		if (isLog)
			logger.info("com.bonitasoft.rest.context: "+logExplanation);
	}


	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	logError																		*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	// report an error
	private void logError(Map<String,Object> rootResult, String logExplanation )
	{
		logger.error("com.bonitasoft.rest.context: "+logExplanation);
		String error = rootResult.get("error");
		if (error!=null)
			error+=";"+logExplanation;
		else
			error = logExplanation;
		rootResult.put("error", error);
	}
}
