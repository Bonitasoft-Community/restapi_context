package com.bonitasoft.rest.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.Format;
import java.text.SimpleDateFormat;

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
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.data.ArchivedDataInstance;
import org.bonitasoft.engine.bpm.data.ArchivedDataNotFoundException;
import org.bonitasoft.engine.bpm.data.DataInstance;
import org.bonitasoft.engine.bpm.data.DataNotFoundException;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bdm.Entity;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;
import org.bonitasoft.engine.business.data.BusinessDataReference;
import org.bonitasoft.engine.business.data.MultipleBusinessDataReference;
import org.bonitasoft.engine.business.data.SimpleBusinessDataReference;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.web.extension.ResourceProvider
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder


import org.bonitasoft.web.extension.rest.RestApiController;
import org.bonitasoft.web.extension.rest.RestAPIContext;
 
class GetContext implements RestApiController {

	private static final Logger logger = LoggerFactory.getLogger(GetContext.class);


	private static final String cstActionCaseId = "caseId";
	private static final String cstActionProcessDefinitionId = "processDefinitionId";
	private static final String cstActionIsCaseArchived = "isCaseArchived";
	private static final String cstActionTaskId = "taskId";
	private static final String cstActionisTaskArchived = "isTaskArchived";



	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	class ContextCaseId																*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	// a Case has multiple access : a ProcessInstance or a ArchiveProcessInstance, a TaskInstance or an ArchiveTaskInstance...
	private static class ContextCaseId {
		Long taskInstanceId = null;
		Long processInstanceId = null;

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
			return null;
		}

		public String trace()
		{
			String trace="ContextCaseid:";
			trace += (processInstance!=null ? "ProcessInstance[Open-"+processInstance.getId()+"],":"")
			trace += (archivedProcessInstance!=null ? "ProcessInstance[Archived-"+archivedProcessInstance.getId()+"/"+archivedProcessInstance.getSourceObjectId() +"],":"")
			trace += (activityInstance!=null ? "ActivityInstance[Active-"+activityInstance.getId()+"],":"")
			trace += (archivedActivityInstance!=null ? "ActivityInstance[Archived-"+archivedActivityInstance.getId()+"],":"");
			return trace;
		}

	}



	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	class PerformanceTrace															*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	private static class PerformanceTrace {
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
		DataInstance contextData=null;
		String sourceContextData="";
		String contextDataSt = null;
		boolean isLog=false;
		Boolean isLogFromParameter=null;

		try
		{
			APIClient apiClient = context.apiClient;
			ProcessAPI processAPI = context.apiClient.processAPI;
			BusinessDataAPI businessDataAPI = context.apiClient.businessDataAPI;
			ContextCaseId contextCaseId = new ContextCaseId();

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
			logRest( isLog,"=================== GetContext RESTAPI");
			
			//----------------- get the perimeter (taskid or processInstanceId)
			try
			{
				if (request.getParameter("taskId")!=null)
				{
					contextCaseId.taskInstanceId = Long.valueOf( request.getParameter("taskId"));
					ActivityInstance activityInstance = processAPI.getActivityInstance( contextCaseId.taskInstanceId);
					contextCaseId.processInstanceId = activityInstance.getParentContainerId();
				}
			} catch(Exception e ) 
			// no worry if an exception arrived here : that's mean the user doesn't give a taskId, error is manage after (no taskid+noCaseId=error)
			{
				sourceContextData+="Error with taskId["+request.getParameter("taskId")+"] : "+e.toString();
			};

			if (contextCaseId.processInstanceId == null) {
				try
				{
					if (request.getParameter("caseId")!=null)
						contextCaseId.processInstanceId = Long.valueOf( request.getParameter("caseId"));
				} catch(Exception e ) 
				// no worry if an exception arrived here : that's mean the user doesn't give a taskId, error is manage after (no taskid+noCaseId=error)
				{
					sourceContextData+="Error with caseId["+request.getParameter("caseId")+"] : "+e.toString();
				
				};
			}

			if (contextCaseId.processInstanceId == null)
			{
				logError( rootResult, "Parameter [taskId] or [caseId] required ("+sourceContextData+")");
				return;
			}


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
				if (contextCaseId.taskInstanceId !=null && contextCaseId.taskInstanceId == null)
					contextCaseId.archivedActivityInstance = processAPI.getArchivedActivityInstance( contextCaseId.taskInstanceId );
			}
			catch(Exception e)
			{
				// no worry if an exception arrived here : that's mean it's maybe an InstanceId : so error will be managed after
				// logRest( isLog, "No ArchivedProcessinstance found by ["+contextCaseId.processInstanceId+"] : "+e.toString() );
			}
			if (contextCaseId.processInstance == null && contextCaseId.archivedProcessInstance == null)
			{
				rootResult.put("caseId", contextCaseId.processInstanceId);
				logError(rootResult, "caseId unknown");
				return;
			}
			if (contextCaseId.taskInstanceId != null && contextCaseId.activityInstance==null && contextCaseId.archivedActivityInstance==null)
			{
				rootResult.put("caseId", contextCaseId.processInstanceId);
				rootResult.put("taskId", contextCaseId.taskInstanceId);
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
				rootResult.putAt("sourcecontextdata", sourceContextData);
			}
			//--------------- return the content
			JsonSlurper slurper = new JsonSlurper();
			Object contextDataMap = slurper.parseText(contextDataSt);
			if (contextDataMap == null )
			{
				logError(rootResult, "The JSON information is missing : "+sourceContextData);
			}
			else if (! contextDataMap instanceof Map)
			{
				logError( rootResult,  "Local Variable [context] must be a MAP");
			}
			else
			{
				performanceTrace.addMarker("JsonParse");

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


				// get the list of all Business Data

				Map<String,BusinessDataReference> listBusinessData= new HashMap<String,BusinessDataReference>();

				if (contextCaseId.processInstanceId!=null)
				{
					logRest(isLog, "Collect BusinessData from: processinstanceid["+contextCaseId.processInstanceId+"]");
					
					List<BusinessDataReference> tempList =context.apiClient.businessDataAPI.getProcessBusinessDataReferences(contextCaseId.processInstanceId, 0,1000);
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

					tempList =context.apiClient.businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
					if (tempList!=null)
					{
						logRest(isLog, "Collect BusinessData from: archivedActivityInstance.getSourceObjectId() ["+tempList.size()+"]");
						for (BusinessDataReference bde : tempList)
							listBusinessData.put( bde.getName(), bde );

					}
					tempList =context.apiClient.businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getId(), 0,1000);
					if (tempList!=null)
					{
						logRest(isLog, "Collect BusinessData from: archivedActivityInstance.getId() ["+tempList.size()+"]");
						for (BusinessDataReference bde : tempList)
							listBusinessData.put( bde.getName(), bde );
					}
				}
				performanceTrace.addMarker("collectListBusinessData");


				// now, process the list
				for (Object varName : contextDataMap.keySet())
				{
					String varAction = contextDataMap.get( varName ) !=null ? contextDataMap.get( varName ).toString() : null;
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
								completeValue( rootResult, data.getName(), varAction, contextCaseId, apiClient, (Map<String,Object>) contextDataMap, isLog);
							}
							performanceTrace.addMarker("getAllProcessData");
						}

						if (contextCaseId.activityInstance!=null)
						{
							List<DataInstance> listDataInstance = processAPI.getActivityDataInstances(contextCaseId.activityInstance.getId(), 0,1000);
							for (DataInstance data : listDataInstance)
							{
								completeValue(rootResult, data.getName(), varAction, contextCaseId, apiClient, (Map<String,Object>) contextDataMap, isLog);
							}
							performanceTrace.addMarker("getAllActivityData");

						}
						// ----- archived part
						if (contextCaseId.archivedProcessInstance!=null)
						{
							instanceForBdm = contextCaseId.archivedProcessInstance.getId();
							List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedProcessDataInstances(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
							for (ArchivedDataInstance data : listDataInstance)
							{
								completeValue( rootResult, data.getName(), varAction, contextCaseId, apiClient, (Map<String,Object>) contextDataMap, isLog);
							}
							performanceTrace.addMarker("getAllArchivedProcessData");

						}
						if (contextCaseId.archivedActivityInstance!=null)
						{
							List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedActivityDataInstances(contextCaseId.archivedActivityInstance.getSourceObjectId(), 0,1000);
							for (ArchivedDataInstance data : listDataInstance)
							{
								completeValue( rootResult, data.getName(), varAction, contextCaseId, apiClient, (Map<String,Object>) contextDataMap, isLog);
							}
							performanceTrace.addMarker("getAllArchivedActivityData");

						}



						// ---------------------- business Data
						// logRest(isLog, "Search BDM with processInstanceId=["+contextCaseId.processInstanceId+"] instanceForBdm="+instanceForBdm);
						//logRest(isLog, "contextCaseId.trace() =["+contextCaseId.trace()+"]");
						// logRest(isLog, "archivedProcessInstance =["+contextCaseId.archivedProcessInstance+"]");

						for (BusinessDataReference businessData : listBusinessData.values())
						{
							logRest(isLog, "Loop Get BDM["+businessData.getName()+"] / type["+businessData.getType()+"]");
							completeValueBdmData( rootResult, businessData, contextCaseId, apiClient, contextDataMap, isLog );
						}
						performanceTrace.addMarker("getBusinessData");


					}
					else if (cstActionCaseId.equals(varAction)) {
						rootResult.put(varName, contextCaseId.processInstanceId);
						// logRest(isLog,"cstActionCaseId :  new Result["+rootResult+"]");
					}

					else if (cstActionProcessDefinitionId.equals(varAction))
						rootResult.put( varName, contextCaseId.getProcessDefinitionId());


					else if (cstActionIsCaseArchived.equals(varAction))
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
							completeValueBdmData( rootResult, listBusinessData.get( varName ), contextCaseId, apiClient, contextDataMap, isLog );
							performanceTrace.addMarker("getBdmData["+varName+"]");
						}
						else
						{
							completeValue( rootResult, varName, varAction, contextCaseId, apiClient, (Map<String,Object>) contextDataMap, isLog);
							performanceTrace.addMarker("getData["+varName+"]");
						}

						listBusinessData


					}

				} // end of list
			}
			performanceTrace.addMarker("getFinalResult");
			if (isLog)
			{
				logRest( isLog, "Final rootResult "+rootResult.toString())
				logRest( isLog,"Performance :"+performanceTrace.trace() );
				rootResult.put("performanceRestContextCall", performanceTrace.trace() );
			}

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
	/*	completeValue																	*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */
	/**
	 * search the variable
	 * @param rootResult : the rootResult to complete.
	 * @param varName : the name of the variable to describe
	 * @param processInstanceId
	 * @param taskInstanceId
	 */
	private void completeValue( Map<String,Object> rootResult,
			String varName,
			String varAction,
			ContextCaseId contextCaseId,
			APIClient apiClient,
			Map<String,Object> contextDataMap,
			boolean isLog)
	{

		ProcessAPI processAPI = apiClient.processAPI;
		BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

		// logger.info("completeValue: Get variable["+varName+"]");


		if (contextCaseId.processInstance != null)
			try
			{
				DataInstance dataInstance = processAPI.getProcessDataInstance(varName.toString(), contextCaseId.processInstance.getId() );
				logRest( isLog, "completeValue: Get variable["+varName+"] is a PROCESSDATA : ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
				completeValueFromData( rootResult, dataInstance.getName(), dataInstance.getValue(), varAction);
				return;
			} catch (DataNotFoundException dnte) {};

		if (contextCaseId.activityInstance != null)
			try
			{
				// logger.info("Try get localvariable["+varName+"]");
				DataInstance dataInstance = processAPI.getActivityDataInstance(varName.toString(), contextCaseId.activityInstance.getId() );
				logRest( isLog, "completeValue: Get variable["+varName+"] is a ACTIVITYDATA: ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
				completeValueFromData( rootResult, dataInstance.getName(), dataInstance.getValue(), varAction );
				return;
			} catch (DataNotFoundException dnte) {};

		if (contextCaseId.archivedProcessInstance != null)
			try
			{
				logRest( isLog, "completeValue: search variable["+varName+"] in getArchivedProcessDataInstance");
				ArchivedDataInstance archivedDataInstance = processAPI.getArchivedProcessDataInstance (varName.toString(), contextCaseId.archivedProcessInstance.getSourceObjectId() );
				logRest( isLog, "completeValue: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

				completeValueFromData( rootResult, archivedDataInstance.getName(), archivedDataInstance.getValue(), varAction );
				return;
			} catch (ArchivedDataNotFoundException dnte) {};

		if (contextCaseId.archivedActivityInstance != null)
		{

			try
			{
				logRest( isLog, "completeValue: search variable["+varName+"] in getArchivedActivityDataInstance");
				ArchivedDataInstance archivedDataInstance = processAPI. getArchivedActivityDataInstance( varName.toString(), contextCaseId.archivedActivityInstance.getSourceObjectId() );
				logRest( isLog, "completeValue: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

				completeValueFromData( rootResult, archivedDataInstance.getName(),archivedDataInstance.getValue(), varAction);
				return;
			} catch (ArchivedDataNotFoundException dnte) {};
		}



		return;
	}

	/**
	 * save the value in the rootResult. If the value is a Obect 
	 */
	private void completeValueFromData( Map<String,Object> rootResult, String varName, Object varValue, String varAction )
	{
		rootResult.put(varName, transformValue( varValue, varAction) );
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

			logger.info("completeValueBdmData.6:Dao loaded : dao["+ dao +"] listStorageIds["+listStorageIds+"]");

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
				// logger.info("completeValueBdmData.9: got the class["+classBdmEntity.getName()+"]");

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

		Method[] listMethods= classBdmEntity.getMethods();

		//	logger.info("Field= "+listFields+" Methods="+listMethods);
		for (Method method : listMethods)
		{
			try
			{
				logger.info("loadBdmVariableOneLevel.10.a method["+method.getName()+"]");

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

					// ok, the context pilot now
					boolean keepIt=true;
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


	/* -------------------------------------------------------------------------------- */
	/*																					*/
	/*	transformValue																	*/
	/*																					*/
	/* -------------------------------------------------------------------------------- */

	/**
	 * Transform the value accord
	 */
	private static SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

	private Object transformValue( Object data, String varAction )
	{
		if (data==null)
			return null;
		if (data instanceof Date)
		{
			if ("date".equals(varAction))
				return sdfDate.format( (Date) data);
			else if ("datetime".equals(varAction))
				return sdfDateTime.format( (Date) data);
			return sdfDateTime.format( (Date) data);
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
