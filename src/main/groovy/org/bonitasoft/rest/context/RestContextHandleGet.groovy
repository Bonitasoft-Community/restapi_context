package org.bonitasoft.rest.context;

import groovy.json.JsonBuilder;
import groovy.json.JsonException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpHeaders;
import org.bonitasoft.engine.api.APIClient;
import org.bonitasoft.engine.api.BusinessDataAPI;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.ProfileAPI;
import org.bonitasoft.engine.bdm.Entity;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;
import org.bonitasoft.engine.bpm.data.ArchivedDataInstance;
import org.bonitasoft.engine.bpm.data.ArchivedDataNotFoundException;
import org.bonitasoft.engine.bpm.data.DataInstance;
import org.bonitasoft.engine.bpm.data.DataNotFoundException;
import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.bpm.document.ArchivedDocument;
import org.bonitasoft.engine.bpm.document.ArchivedDocumentsSearchDescriptor;
import org.bonitasoft.engine.bpm.document.DocumentCriterion;
import org.bonitasoft.engine.bpm.document.DocumentDefinition;
import org.bonitasoft.engine.bpm.document.DocumentListDefinition;
import org.bonitasoft.engine.bpm.flownode.FlowElementContainerDefinition;
import org.bonitasoft.engine.bpm.parameter.ParameterCriterion;
import org.bonitasoft.engine.bpm.parameter.ParameterInstance;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.business.data.BusinessDataReference;
import org.bonitasoft.engine.business.data.MultipleBusinessDataReference;
import org.bonitasoft.engine.business.data.SimpleBusinessDataReference;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.web.extension.rest.RestAPIContext;
import org.bonitasoft.web.extension.rest.RestApiController;
import org.bonitasoft.web.extension.rest.RestApiResponse;
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;


/**
 * See page.properties for list of version
 */
class RestContextHandleGet implements RestApiController {


    private static Logger logger = Logger.getLogger(RestContextHandleGet.class.getName());


	private static String CST_VERSION="2.13.1";


    /* -------------------------------------------------------------------------------- */
    /*																					*/
    /*	dohandle																		*/
    /*																					*/
    /* -------------------------------------------------------------------------------- */

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {


        // To retrieve query parameters use the request.getParameter(..) method.
        // Be careful, parameter values are always returned as String values
        RestContextTrackPerformance trackPerformance = new RestContextTrackPerformance();
        trackPerformance.addMarker("start");

        // Here is an example of how you can retrieve configuration parameters from a properties file
        // It is safe to remove this if no configuration is required
        Map<String,Object> rootResult = new HashMap<String,Object>();
        Map<String,Object> contextResult= new HashMap<String,Object>();

        String sourceContextData="";

        RestContextConfiguration configuration;
        RestContextCaseId contextCaseId = null;
        RestContextPilot pilot = null;
        try
        {
            configuration = new RestContextConfiguration(context);

            APIClient apiClient = context.apiClient;
            APISession apiSession = context.getApiSession();
            ProcessAPI processAPI = apiClient.processAPI;
            IdentityAPI identityAPI = apiClient.identityAPI;
            ProfileAPI profileAPI = apiClient.profileAPI;
            BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;
			
            contextCaseId = new RestContextCaseId( apiSession.getUserId(), processAPI, identityAPI,  profileAPI);
            pilot = new RestContextPilot();
            contextCaseId.setPilot(  pilot  );
            pilot.setContextCaseId(contextCaseId, trackPerformance );

            contextCaseId.decodeParametersFromHttp( request, configuration);
            sourceContextData+= contextCaseId.getAnalysisString();

            pilot.decodeParameters();
            sourceContextData+= pilot.getAnalysisString();
			sourceContextData += "BEGINPILOT="+(pilot.getPilotDataMap()==null ? null : pilot.getPilotDataMap().toString())+" ENDPILOT";

            contextCaseId.log( "=================== Start GetContext RESTAPI "+CST_VERSION);
            boolean canContinue=true;


            // check the permission to access
            if (canContinue)
            {

                // specific case : contentStorageId
                if (contextCaseId.contentStorageId!=null)
                {
                    RestContextCheckDocumentDownload checkDocumentDownload = new RestContextCheckDocumentDownload(apiSession.getUserId(), processAPI, identityAPI,  profileAPI);

                    if (checkDocumentDownload.isAllowed( contextCaseId ))
                    {
                        rootResult.putAt("documentdownload", true);
                    }
                    else
                    {
                        rootResult.putAt("documentdownload", false);
                        canContinue=false;
                    }
                }

                if  (! contextCaseId.isAllowContext() )
                {
                    contextCaseId.log("No Permission to access this item (process definition, task, case id) : check the user/actor mapping");
                    canContinue=false;
                }
            }

            String version = request.getParameter("version");
            if (version!=null)
                contextResult.put("version", CST_VERSION);


            // ------------------ retrieve correct information
            // if the processinstance exist ? The task Id ?

            trackPerformance.addMarker("Detectparameter");

            //----------------------- get the pilot (contextDataOrigin and contextDataSt)
            if (canContinue)
            {

                // not : at this moment, the contextDataOrigin may be NOT NULL and the contextDataSt is null : then the parseText will failed

                trackPerformance.addMarker("getPilot");

                contextCaseId.log( "SourceContextData = "+sourceContextData);
                if (contextCaseId.isLog)
                {
                    contextResult.putAt("sourcecontextdata", sourceContextData);
                }
                //--------------- return the content
                if (pilot.getErrorMessage() != null) {
                    contextCaseId.logError( rootResult,   pilot.getErrorMessage() );
                    canContinue=false;
                }
            }

            if (canContinue)
            {
                trackPerformance.addMarker("JsonParse");


                // decode the Log
                if (contextCaseId.isLogFromParameter==null) {
                    try
                    {
                        contextCaseId.isLog = Boolean.valueOf( pilotDataMap.get("RESTCONTEXTISLOG" ) );
                    }
                    catch( Exception e)
                    // Ok, if the value is not given or it's not a Boolean, no worry
                    {}
                }

                // get the content now
                getContent( rootResult,  contextCaseId, trackPerformance, apiClient );

            }

            if (canContinue)
            {
                contextCaseId.completeResult( contextResult );
            }

			contextResult.put("contextPath", request.getContextPath());
			
            trackPerformance.addMarker("getFinalResult");
            if (contextCaseId.isLog)
            {
                contextCaseId.log( "Final rootResult "+rootResult.toString())

                contextResult.put( "performanceDetail", trackPerformance.trace() );
                contextResult.put( "performanceTotalMs", trackPerformance.getTotalTime() );
            }

            // and now put the context in the result (nota : we may overwride by this way the context variable)
            rootResult.put("context", contextResult);

        } catch(DataNotFoundException dnte )
        {
            contextCaseId.logError( rootResult, "Expect [context] or [globalcontext] variable to pilot what to search");
        }
        catch (JsonException je)
        {
			
			StringWriter sw = new StringWriter();
			 e.printStackTrace(new PrintWriter(sw));
			 String exceptionDetails = sw.toString();
		   
            contextCaseId.logError( rootResult,"Bad JSON "+sourceContextData+" : "+je.toString()+" at "+exceptionDetails);
        }
        catch(Exception e )
        {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionDetails = sw.toString();
            contextCaseId.logError( rootResult, e.toString()+" at "+exceptionDetails);
        }
        finally {

            /*
             try
             {
             contextCaseId.log( "=================== Before jsonResult");
             traceResult( rootResult, "", contextCaseId);
             }
             catch (Exception e)
             {
             StringWriter sw = new StringWriter();
             e.printStackTrace(new PrintWriter(sw));
             String exceptionDetails = sw.toString();
             contextCaseId.logError( rootResult, e.toString()+" at "+exceptionDetails);
             }
             */

            contextCaseId.log( "=================== End GetContext RESTAPI");

            // Send the result as a JSON representation
            // You may use buildPagedResponse if your result is multiple
            return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(rootResult).toPrettyString())
        }
    }


    private void  traceResult(Map<String,Object> map, String indentation, RestContextCaseId contextCaseId)
    {
        for (String key: map.keySet())
        {
            Object value=map.get( key );
            if (value instanceof Map)
            {
                contextCaseId.log( indentation+"["+key+"] = MAP");
                traceResult( (Map) value, indentation+"    ", contextCaseId);
            }
            else
                contextCaseId.log( indentation+"["+key+"] = ["+ (value==null ? "null": value.getClass().getName()+"/"+value) +"]");


        }
    }
    /**
     * get the content of the REST API
     * @param contextCaseId
     * @param context
     */
    private void getContent(Map<String,Object> rootResult,
                            RestContextCaseId contextCaseId,
                            RestContextTrackPerformance trackPerformance,
                            APIClient apiClient)
    {
        // get the list of all Business Data
        ProcessAPI processAPI = apiClient.processAPI;
        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

        Map<String,BusinessDataReference> listBusinessData= new HashMap<String,BusinessDataReference>();


        if (contextCaseId.processInstanceId!=null)
        {
            contextCaseId.log( "Collect BusinessData from: processinstanceid["+contextCaseId.processInstanceId+"]");

            Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessBusinessDataReferences");
            List<BusinessDataReference>  tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.processInstanceId, 0,1000);
            trackPerformance.endSubOperation( trackSubOperation);

            if (tempList!=null && tempList.size()>0)
            {
                for (BusinessDataReference bde : tempList)
                { 
                   contextCaseId.log( "Collect BusinessData ["+bde.getName()+"]");                
                   listBusinessData.put( bde.getName(), bde );
                }

            }
        }
        // from the archivedProcessInstance now
        if (contextCaseId.archivedProcessInstance!=null)
        {

            // logger.info(">>> *BEGIN* ArchivedProcessInstanceExecutionContext<<");
            Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getArchivedProcessInstanceExecutionContext");
            Map<String,Serializable> map = processAPI.getArchivedProcessInstanceExecutionContext(contextCaseId.archivedProcessInstance.getId());
            trackPerformance.endSubOperation( trackSubOperation);

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
            contextCaseId.log( "Collect BusinessData from: getArchivedProcessInstanceExecutionContext :archivedProcessInstance.getId() ["+listBusinessData.size()+"]");
            // logger.info(">>> *END* ArchivedProcessInstanceExecutionContext<<");
            trackSubOperation = trackPerformance.startSubOperation("getProcessBusinessDataReferences archivedSOURCEProcessInstance["+contextCaseId.archivedProcessInstance.getSourceObjectId()+"]");
            List<BusinessDataReference>  tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
            trackPerformance.endSubOperation( trackSubOperation);

            if (tempList!=null)
            {
                contextCaseId.log( "Collect BusinessData from: archivedActivityInstance.getSourceObjectId() ["+tempList.size()+"]");
                for (BusinessDataReference bde : tempList)
                    listBusinessData.put( bde.getName(), bde );

            }
            trackSubOperation = trackPerformance.startSubOperation("getProcessBusinessDataReferences archiveProcessInstance["+contextCaseId.archivedProcessInstance.getId()+"]");
            tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getId(), 0,1000);
            trackPerformance.endSubOperation( trackSubOperation);
            if (tempList!=null)
            {
                contextCaseId.log( "Collect BusinessData from: archivedActivityInstance.getId() ["+tempList.size()+"]");
                for (BusinessDataReference bde : tempList)
                    listBusinessData.put( bde.getName(), bde );
            }
        }
        trackPerformance.addMarker("collectListBusinessData");

		// documents
       List<Document> listDocuments = new ArrayList();
       List<String> listDocumentsName = new ArrayList<String>();
	   if (contextCaseId.processInstanceId!=null)
        {
            Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getLastVersionOfDocuments");
			List listLastVersionDoc = processAPI.getLastVersionOfDocuments(contextCaseId.processInstanceId, 0,1000, DocumentCriterion.NAME_ASC);
            if (listLastVersionDoc==null)
            	listDocuments = new ArrayList<Document>();
			else
				listDocuments.addAll( listLastVersionDoc );
			// you know what ? When the case is archive, the way to access the document is different... :-(
			if (contextCaseId.activeProcessInstanceId !=null)
			{
				SearchOptionsBuilder searchDoc= new SearchOptionsBuilder(0,1000);
				searchDoc.filter(ArchivedDocumentsSearchDescriptor.PROCESSINSTANCE_ID,contextCaseId.activeProcessInstanceId );
				SearchResult<ArchivedDocument> searchArchDocuments = processAPI.searchArchivedDocuments(searchDoc.done())
				for (ArchivedDocument archDoc : searchArchDocuments.getResult())
				{
					listDocuments.add( (Document) archDoc );
				}
			}	
				
            for (Document doc : listDocuments)
            	listDocumentsName.add( doc.getName() );
            		
            trackPerformance.endSubOperation( trackSubOperation);
		}
	  	Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("detectDocument");
		List<DocumentDefinition> listDocumentDefinition =new ArrayList();
		List<DocumentListDefinition> listDocumentListDefinition=new ArrayList();
		if (contextCaseId.processDefinitionId !=null)
		{
			DesignProcessDefinition designProcessDefinition = processAPI.getDesignProcessDefinition( contextCaseId.processDefinitionId );
			FlowElementContainerDefinition flowElementContainerDefinition = designProcessDefinition.getFlowElementContainer();
			listDocumentDefinition = flowElementContainerDefinition.getDocumentDefinitions();
			listDocumentListDefinition =	flowElementContainerDefinition.getDocumentListDefinitions();
		}
        trackPerformance.endSubOperation( trackSubOperation);
		
		
		// parameters
		trackSubOperation = trackPerformance.startSubOperation("getParameterInstances");
		List<ParameterInstance>	listParameters = contextCaseId.processDefinitionId==null ? null:  processAPI.getParameterInstances(contextCaseId.processDefinitionId, 0, 100, ParameterCriterion.NAME_ASC);
		if (listParameters==null)
			listParameters = new ArrayList<ParameterInstance>();
			
		trackPerformance.endSubOperation( trackSubOperation);
		
		
        //----------------------------- now, process the list
        for (Object varName : contextCaseId.getPilot().getPilotDataMap().keySet())
        {
            String varAction = contextCaseId.getPilot().getActionFromVariable( varName );
            
            // if the varName is a document, ignore it
            
            
            contextCaseId.log( "Loop Get variable["+varName+"]");

            if (varName.equals("*"))
            {
                Long instanceForBdm = null;
                //------------ active part
                contextCaseId.log( "   get[*] processInstance/activity["+contextCaseId.processInstance+"/"+contextCaseId.activityInstance+"]");

                if (contextCaseId.processInstance!=null)
                {
                    instanceForBdm = contextCaseId.processInstance.getId();
					trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstances");
                    List<DataInstance> listDataInstance = processAPI.getProcessDataInstances(contextCaseId.processInstance.getId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (DataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "DataInstance["+data.getName()+"]");
                        completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance );
                    }
                    trackPerformance.addMarker("getAllProcessData");
                }

                if (contextCaseId.activityInstance!=null)
                {
					trackSubOperation = trackPerformance.startSubOperation("getActivityDataInstances");
                    List<DataInstance> listDataInstance = processAPI.getActivityDataInstances(contextCaseId.activityInstance.getId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (DataInstance data : listDataInstance)
                    {
                        // contextCaseId.log("LocalDataInstance detected ["+data.getName()+"] contentType["+data.getContainerType()+"]");
                        if (! "PROCESS_INSTANCE".equals( data.getContainerType() ))
                            completeValueProcessVariable(rootResult, data.getName(), varAction, contextCaseId, apiClient,contextCaseId.getPilot().getPilotDataMap(), trackPerformance );
                    }
                    trackPerformance.addMarker("getAllActivityData");

                }
                // ----- archived part
                if (contextCaseId.archivedProcessInstance!=null)
                {
                    instanceForBdm = contextCaseId.archivedProcessInstance.getId();
					trackSubOperation = trackPerformance.startSubOperation("getArchivedProcessDataInstances");
                    List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedProcessDataInstances(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (ArchivedDataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "ArchivedDataInstance detected ["+data.getName()+"] contentType["+data.getContainerType()+"]");
                        if ( "PROCESS_INSTANCE".equals( data.getContainerType() ))
                            completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance  );
                    }
                    trackPerformance.addMarker("getAllArchivedProcessData");

                }
                if (contextCaseId.archivedActivityInstance!=null)
                {
					trackSubOperation = trackPerformance.startSubOperation("getArchivedActivityDataInstances");
                    List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedActivityDataInstances(contextCaseId.archivedActivityInstance.getSourceObjectId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (ArchivedDataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "ArchivedActivityDataInstance detected ["+data.getName()+"]");
                        completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(),trackPerformance );
                    }
                    trackPerformance.addMarker("getAllArchivedActivityData");

                }



                // ---------------------- business Data
                // logRest(contextCaseId.isLog, "Search BDM with processInstanceId=["+contextCaseId.processInstanceId+"] instanceForBdm="+instanceForBdm);
                // logRest(contextCaseId.isLog, "contextCaseId.trace() =["+contextCaseId.trace()+"]");
                // logRest(contextCaseId.isLog, "archivedProcessInstance =["+contextCaseId.archivedProcessInstance+"]");

                for (BusinessDataReference businessData : listBusinessData.values())
                {
                    contextCaseId.log( "BDM["+businessData.getName()+"] / type["+businessData.getType()+"]");
                    completeValueBdmData( rootResult, businessData, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(),trackPerformance  );
                }
                trackPerformance.addMarker("getBusinessData");

				//--------------------- Documents
				// First the real document
				// we are in the "*" so all should be return. But maybe we have a access variable ? Like '*': 'actor:Supervisor';
				Set<String> docWithContent= new HashSet<String>();
					
				for (Document document : listDocuments)
				{ 
					docWithContent.add( document.getName());
					completeValueDocumentData(rootResult,
                                      document,
                                      contextCaseId,
                                      apiClient,
                                      contextCaseId.getPilot().getPilotDataMap(),
                                      trackPerformance);
                 }
                    
				for (DocumentDefinition documentDefinition : listDocumentDefinition )
				{
					if (docWithContent.contains( documentDefinition.getName()))
						continue;
					if ( contextCaseId.isAllowVariableName(	documentDefinition.getName() ))
						rootResult.put( documentDefinition.getName(), new HashMap());
				}
				for (DocumentListDefinition documentListDefinition : listDocumentListDefinition )
				{
					if (docWithContent.contains( documentListDefinition.getName()))
						continue;
					if ( contextCaseId.isAllowVariableName(	documentListDefinition.getName() ))
						rootResult.put( documentListDefinition.getName(), new ArrayList());
				}
                //--------------------- parameters
                for (ParameterInstance parameter : listParameters)
              	{
	                contextCaseId.log( "Parameters["+parameter.getName()+"]");
                    if ( contextCaseId.isAllowVariableName(	parameter.getName() ))
                        rootResult.put( parameter.getName(), parameter.getValue());
    	   		 }
            } // end ALL 
            else if (RestContextCaseId.cstActionCaseId.equals(varAction) && (contextCaseId.processInstanceId != null)) {
                rootResult.put(varName, contextCaseId.processInstanceId);
                // contextCaseId.log( "cstActionCaseId :  new Result["+rootResult+"]");
            }

            else if (RestContextCaseId.cstActionProcessDefinitionId.equals(varAction))
                rootResult.put( varName, contextCaseId.getProcessDefinitionId());


            else if (RestContextCaseId.cstActionIsCaseArchived.equals(varAction))
                rootResult.put( varName, contextCaseId.archivedProcessInstance!=null );

            else if (RestContextCaseId.cstActionTaskId.equals(varAction) && contextCaseId.taskInstanceId !=null)
                rootResult.put( varName, contextCaseId.taskInstanceId);

            else if (RestContextCaseId.cstActionisTaskArchived.equals(varAction) )
                rootResult.put( varName, contextCaseId.archivedActivityInstance != null);
            else
            {
				boolean foundName=false;
				
				// is that a "explicit variable" ? 
				// "
				if (contextCaseId.getPilot().getExpliciteVariable().isExplicitVariable( varName ))
				{
					// yes, it is
					foundName=true;
					completeValueProcessVariable( rootResult, varName, varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance );
				}
				
                // We want to load the data varName : is that a business Data ?
                if (! foundName && listBusinessData.containsKey( varName ))
                {
                	foundName=true;                    
                    completeValueBdmData( rootResult, listBusinessData.get( varName ), contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance  );
                    trackPerformance.addMarker("getBdmData (completeValueBdmData) ["+varName+"]");
                }
                // document ? 
                if ( ! foundName)
                { 
                	for (Document document : listDocuments)
                	{ 
                		if (document!=null && document.getName().equals( varName))
                		{ 
	                		foundName=true;
							contextCaseId.log( "Doc["+document.getName()+"]");							
                			completeValueDocumentData(rootResult, document, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance);
	                     }
	                 }
					if ( ! foundName)
						 for (DocumentDefinition documentDefinition : listDocumentDefinition )
		                 { 
		                 	if (documentDefinition!=null && documentDefinition.getName().equals( varName))
	                		{ 
		                		foundName=true;
								contextCaseId.log( "DocDefinition["+documentDefinition.getName()+"]");							
		                		if ( contextCaseId.isAllowVariableName(	documentDefinition.getName() ))
				            		rootResult.put( documentDefinition.getName(), new HashMap());
				            }
				         }
					 if ( ! foundName)
					 	for (DocumentListDefinition documentListDefinition : listDocumentListDefinition )
						 { 
		                 	if (documentListDefinition!=null && documentListDefinition.getName().equals( varName))
	                		{ 
		                		foundName=true;
								contextCaseId.log( "DocListDefinition["+documentListDefinition.getName()+"]");							
		                		if ( contextCaseId.isAllowVariableName(	documentListDefinition.getName() ))
	            					rootResult.put( documentListDefinition.getName(), new ArrayList());
	              			}
	              		}
	            }
                // a parameters ? 
                if ( ! foundName)
                { 
              	   for (ParameterInstance parameter : listParameters)
				   {
					   if (parameter.getName().equals( varName ))
					   { 
						   foundName=true;
						   contextCaseId.log( "Parameter["+parameter.getName()+"]");
						   if ( contextCaseId.isAllowVariableName(	parameter.getName() ))
                    	 		rootResult.put( parameter.getName(), parameter.getValue());
					   }
                }
                // a variable ? 
                if (!foundName)
                {                    
                    completeValueProcessVariable( rootResult, varName, varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance );
                    trackPerformance.addMarker("getData (completeValueProcessVariable)["+varName+"]");
                }
             }



            }

        } // end of list


        

       
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
    private void completeValueProcessVariable(Map<String,Object> rootResult,
                                              String varName,
                                              String varAction,
                                              RestContextCaseId contextCaseId,
                                              APIClient apiClient,
                                              Map<String,Object> pilotDataMap,
                                              RestContextTrackPerformance trackPerformance)
    {
    	try
    	{
	        ProcessAPI processAPI = apiClient.processAPI;
	        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;
			String logVarAction = varAction;
			if (logVarAction!=null && logVarAction.length()>20)
				logVarAction= logVarAction.substring(0,20)+"...";
	        contextCaseId.logWithPrefix( "=== completeValueProcessVariable.begin: Get variable["+varName+"] ", "varAction["+logVarAction+"]");
	
	        RestContextTransformData restContextTransformData = new RestContextTransformData( contextCaseId );
	        
	        if (contextCaseId.getPilot().getExpliciteVariable().isExplicitVariable( varName ))
	        {
	            Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getExplicitVariable["+varName+"]");
	        	Object value = contextCaseId.getPilot().getExpliciteVariable().getValue(rootResult, varName, contextCaseId);
	        	restContextTransformData.transform( rootResult, varName, varName, value, pilotDataMap,0);
	            trackPerformance.endSubOperation( trackSubOperation);
	            return;
	        }
	        
	        if (contextCaseId.processInstance != null)
	            try
	            {
	                Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName+"] / getProcessData");
	                DataInstance dataInstance = processAPI.getProcessDataInstance(varName.toString(), contextCaseId.processInstance.getId() );
	                trackPerformance.endSubOperation( trackSubOperation);
	                contextCaseId.logWithPrefix( "completeValueProcessVariable: Get variable["+varName+"]", "processData:["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
	
	                trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / transform");
	               	restContextTransformData.transform( rootResult, dataInstance.getName(), dataInstance.getName(), dataInstance.getValue(), pilotDataMap,0);
	                trackPerformance.endSubOperation( trackSubOperation);
	
	                return;
	            } catch (DataNotFoundException dnte) {};
			// not a ELSE : if we don't found the process Variable, try now with the activity Instance
	        if (contextCaseId.activityInstance != null)
	            try
	            {
	                // logger.info("Try get localvariable["+varName+"]");
	                Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / getActivityData");
	                DataInstance dataInstance = processAPI.getActivityDataInstance(varName.toString(), contextCaseId.activityInstance.getId() );
	                trackPerformance.endSubOperation( trackSubOperation);
					contextCaseId.logWithPrefix( "completeValueProcessVariable: Get variable["+varName+"]", "activitydata:["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
	
	                trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / transform");
	                restContextTransformData.transform( rootResult, dataInstance.getName(), dataInstance.getName(), dataInstance.getValue(),  pilotDataMap,0 );
	                trackPerformance.endSubOperation( trackSubOperation);
	                return;
	            } catch (DataNotFoundException dnte) {};
	
	         if (contextCaseId.archivedProcessInstance != null)
	            try
	            {
	                contextCaseId.log( "completeValueProcessVariable: search variable["+varName+"] in getArchivedProcessDataInstance");
	                Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / getrchivedProcessData");
	                ArchivedDataInstance archivedDataInstance = processAPI.getArchivedProcessDataInstance (varName.toString(), contextCaseId.archivedProcessInstance.getSourceObjectId() );
	                trackPerformance.endSubOperation( trackSubOperation);
					contextCaseId.logWithPrefix( "completeValueProcessVariable: Get variable["+varName+"]", "archivedData:["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");
	
	                trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / transform");
	                restContextTransformData.transform( rootResult, archivedDataInstance.getName(), archivedDataInstance.getName(), archivedDataInstance.getValue(), pilotDataMap,0 );
	                trackPerformance.endSubOperation( trackSubOperation);
	                return;
	            } catch (ArchivedDataNotFoundException dnte) {};
	
	        if (contextCaseId.archivedActivityInstance != null)
	        {
	            try
	            {
	                contextCaseId.log( "completeValueProcessVariable: search variable["+varName+"] in getArchivedActivityDataInstance");
	                Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / getArchivedActivityProcessData");
	                ArchivedDataInstance archivedDataInstance = processAPI. getArchivedActivityDataInstance( varName.toString(), contextCaseId.archivedActivityInstance.getSourceObjectId() );
	                trackPerformance.endSubOperation( trackSubOperation);
				    contextCaseId.logWithPrefix( "completeValueProcessVariable: Get variable["+varName+"]", "archiveProcessData: ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");
	
	                trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / transform");
	                restContextTransformData.transform( rootResult, archivedDataInstance.getName(),  archivedDataInstance.getName(),archivedDataInstance.getValue(),  pilotDataMap, 0);
	                trackPerformance.endSubOperation( trackSubOperation);
	                return;
	            } catch (ArchivedDataNotFoundException dnte) {};
	        }
			// maybe a default value ? 
			// process instanciation : maybe a defaut value ? 
		    contextCaseId.logWithPrefix( "completeValueProcessVariable: Get variable["+varName+"]", "DefaultValue");
			restContextTransformData.transform( rootResult, varName, varName,null,  pilotDataMap, 0);
			if (rootResult.containsKey( varName))
			{ 
				Object value=rootResult.get(varName);
	        	contextCaseId.logWithPrefix( "=== completeValueProcessVariable.end: Get variable["+varName+"] ", " value=["+(value==null ? null : value.toString())+"]");
			}
			else
				contextCaseId.logWithPrefix( "=== completeValueProcessVariable.end: Get variable["+varName+"] ", " HIDDEN");
	        return;
    	}
    	catch(Exception e)
    	{    		
			analysis+="Exception "+e.toString();			
    		final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            contextCaseId.logError(rootResult, "Error during get completeValueProcessVariable: "+e.toString()+" at "+exceptionDetails);

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
    private void completeValueBdmData(Map<String,Object> rootResult,
                                      BusinessDataReference businessData,
                                      RestContextCaseId contextCaseId,
                                      APIClient apiClient,
                                      Map<String,Object> pilotDataMap,
                                      RestContextTrackPerformance trackPerformance)
    {

        ProcessAPI processAPI = apiClient.processAPI;
        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

        // contextCaseId.log("completeValueBdmData: Get variable["+businessData.getName()+"]");

        try
        {

            // contextCaseId.log("completeValueBdmData.2: Get Business Reference ["+businessData.getName()+"]");
            Map<String,Object> trackSubOperation = null;

            // the result is maybe a HASHMAP or a LIST<HASMAP>
            Object resultBdm = null;
            boolean isMultiple=false;
            List<Long> listStorageIds = new ArrayList<Long>();
            if (businessData instanceof MultipleBusinessDataReference)
            {
                // this is a multiple data
                trackSubOperation = trackPerformance.startSubOperation("completeValueBdmData["+businessData.getName()+"] MULTIPLE/ storageId");

                isMultiple=true;
                contextCaseId.log( "completeValueBdmData.3 Get MULTIPLE Business Reference ["+businessData.getName()+"] : type["+businessData.getType()+"]");
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
                trackSubOperation = trackPerformance.startSubOperation("completeValueBdmData["+businessData.getName()+"] SINGLE / storageId");

                resultBdm = new HashMap<String,Object>();
                isMultiple=false;
                contextCaseId.log( "completeValueBdmData.3: Get SIMPLE Business Reference ["+businessData.getName()+"] : type["+businessData.getType()+"]");
                // if null, add it even to have a result (bdm name + null)
                listStorageIds.add( ((SimpleBusinessDataReference) businessData).getStorageId());
            }
            // logger.info("completeValueBdmData.3bis : Set ["+resultBdm+"] in result");

            if (trackSubOperation!=null)
                trackPerformance.endSubOperation( trackSubOperation);

            trackSubOperation = trackPerformance.startSubOperation("getProcessBusinessDataReferences["+businessData.getName()+"] / getClass");

            String classDAOName = businessData.getType()+"DAO";
            // logger.info("completeValueBdmData.4: Get Business Reference ["+businessData.getName()+"] it's a BDM-type["+businessData.getType()+"] classDao=["+classDAOName+"]");

            Class classDao = Class.forName( classDAOName );
            if (classDao==null)
            {
                // a problem here...
                contextCaseId.logError( rootResult, "completeValueBdmData.5:>>>>>>>>>>>>>>>>>>>>  DaoClass ["+classDAOName+"] not Found");

                return;
            }
            //logger.info("completeValueBdmData.5:classDao Loaded ["+classDao.getName()+"]");


            BusinessObjectDAO dao = apiClient.getDAO( classDao );
            trackPerformance.endSubOperation( trackSubOperation);

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

                trackSubOperation = trackPerformance.startSubOperation("getProcessBusinessDataReferences["+businessData.getName()+"] / findByPersistenceId");
                Entity dataBdmEntity = dao.findByPersistenceId(storageId);
                trackPerformance.endSubOperation( trackSubOperation);

                if (dataBdmEntity==null)
                {
                    contextCaseId.logError( rootResult, "The BDM variable["+businessData.getName()+"] storageId["+storageId+"] does not exist anymore " );
                    return;
                }
                // logger.info("completeValueBdmData.8: Got the BdmEntity");


                Class classBdmEntity= dataBdmEntity.getClass();
                contextCaseId.log("completeValueBdmData.9: got the class["+classBdmEntity.getName()+"]");

                // Start the recursive
                // example: variable is summerOrder : {}
                // Bdm is summerOrder -> Lines -> Ticket
                // saveOneBdm : this is the local level at this moment
                // pilotDataMap.get("summerorder") give the context to work (example
                // "summerOrder" : {
                //              "name": "data",
                //              " ticket": "*",
                //              "lines" : {  "linename" : "data",
                //                           "ticket" : { "solicitante" : "data" },
                //				         	 "price":"data"
                //	  		    }
                // }
                trackSubOperation = trackPerformance.startSubOperation("loadBdmVariableOneLevel");
				
				Object contextInfo = pilotDataMap.get( businessData.getName() );
				if (contextInfo instanceof String)
					contextInfo=null;
				if (! contextInfo instanceof Map)
					contextInfo=null;
				
                loadBdmVariableOneLevel(businessData.getName(), rootResult, saveOneBdm, dataBdmEntity, contextInfo,  contextCaseId );
                trackPerformance.endSubOperation( trackSubOperation);


            }

            // save the result now
            contextCaseId.log( "completeValueBdmData.6: Final result ["+businessData.getName()+"] : value["+resultBdm+"]");
            rootResult.put( businessData.getName(), resultBdm);


            // def dao = context.apiClient.getDAO(MyDao.class)
            // def data= dao.findByPersistenceId(ref.storageId)
        } catch (Exception e) {
            contextCaseId.logError(rootResult, "Error during get Business Data Information: "+e.toString());
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
     *    "name": "initiator;actor:TeacherActor",
     *    "ticket": "*",
     *    "lines" : {  "linename" : "data",
     *    "ticket" : { "solicitante" : "data" },
     *    "price":"data"
     }
     * We run all the different method.
     * getName() ==> name is reference, save it (check permission - pathNameVariable is then very usefull for the analysis point of view - variableName = pathNameVariable+nameAttribut)
     * getVendor() ==> vendor is not referenced, skip it
     * getTicket() is a BDM so get ticket : referenced, then call recursively. Context say "*" then the sub context is NULL (all accepted)
     * getLines() is a BDM : call recursively given { "linename..." as the sub context}
     */
    private void loadBdmVariableOneLevel(String pathNameVariable,
										Map<String,Object> rootResult,
                                         Map<String,Object> saveLocalLevel,
                                         Entity dataBdmEntity,
                                         Map<String,Object> contextLocalLevel,
                                         RestContextCaseId contextCaseId)
    {
        RestContextTransformData restContextTransformData= new RestContextTransformData(contextCaseId);

        Class classBdmEntity= dataBdmEntity.getClass();
        // contextCaseId.log( "loadBdmVariableOneLevel.10a ---------loadBdmVariableOneLevel class["+classBdmEntity.getName()+"] contextLocalLevel["+contextLocalLevel.toString()+"]");


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
					Object contextInfo=null;
                    if (contextLocalLevel!=null)
                    {
                        contextInfo = contextLocalLevel.get(nameAttribute);
                        keepIt = contextInfo != null;
                    }
					if (keepIt && contextInfo!=null)
					{
						// check the permission now if there are a context info as a STRING
						logger.info("loadBdmVariableOneLevel.10b name pathName+nameAttribut["+pathNameVariable+"."+nameAttribute+"] ContextInfo=["+contextInfo.getClass().getName()+"]");
						if (contextInfo instanceof String)
						{
							boolean permissionAccepted = contextCaseId.getPilot().checkPermissionString(pathNameVariable+"."+nameAttribute, contextInfo);						
							// logger.info("loadBdmVariableOneLevel.10b name pathName+nameAttribut["+pathNameVariable+"."+nameAttribute+"] ContextInfo=["+contextInfo+"] permission["+permissionAccepted+"]");
							keepIt = permissionAccepted;					
						}
					}
                   // logger.info("loadBdmVariableOneLevel.10c method["+method.getName()+"] nameAttribut["+nameAttribute+"] Result=["+value+"] keepIt="+keepIt+" Entity ? "+(value!=null && value instanceof Entity)+" classValue=["+(value !=null ? value.getClass().getName() : "null")+"]");
					  
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

                        if (contextInfo instanceof String && "*".equals(contextInfo))
                            contextInfo=null;
						if (!contextInfo instanceof Map)
						{
							// We don't have the localisation, so log is hard... contextCaseId.logError("Expected a MAP for the contextinfo");
							contextInfo=null;
						}
                        // logger.info("loadBdmVariableOneLevel.10e SubChild contextInfo["+contextInfo+"]");

                        // Ok, this is a Entity or a list of Entity. So, we have to create a Map or a List of Map
                        if (value instanceof Entity)
                        {
                            Map<String,Object> bdmChild = new HashMap<String,Object>();
                            saveLocalLevel.put(nameAttribute, bdmChild);
                            loadBdmVariableOneLevel(pathNameVariable+"."+nameAttribute, rootResult, bdmChild, value, contextInfo, contextCaseId);
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
                                loadBdmVariableOneLevel(pathNameVariable+"."+nameAttribute, rootResult, bdmChild, valueInList, contextInfo, contextCaseId);
                            }

                        }
                    }
                    else
                        saveLocalLevel.put(nameAttribute, restContextTransformData.transformSingleValue( nameAttribute, value, contextLocalLevel==null ? null : contextLocalLevel.get( nameAttribute) ));

                    // logger.info("loadBdmVariableOneLevel.10c saveOneBdm ="+saveLocalLevel.toString());

                }
            }
            catch( Exception e)
            {
                contextCaseId.logError( rootResult, "Error during exploring the Bdm variable ["+dataBdmEntity.getClass().getName()+"] : "+e.toString() );
            }
        }

    }

    protected boolean shouldSkipField(Class<?> fieldType) {
        return fieldType.equals(javassist.util.proxy.MethodHandler.MethodHandler.class);
    }

	
	
    


    /* -------------------------------------------------------------------------------- */
    /*																					*/
    /*	completeValueDocumentData															*/
    /*																					*/
    /* -------------------------------------------------------------------------------- */
	  private void completeValueDocumentData(Map<String,Object> rootResult,
                                      Document document,
                                      RestContextCaseId contextCaseId,
                                      APIClient apiClient,
                                      Map<String,Object> pilotDataMap,
                                      RestContextTrackPerformance trackPerformance)
	{ 
		
	 	if (document == null)
         	return;
         	
	    contextCaseId.logWithPrefix( "************************** Doc detected", "Doc["+document.getName()+" index="+document.getIndex()+"]");
			 
        if (! contextCaseId.isAllowVariableName(document.getName()))
        { 
			return; // already logged
        }
		
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

        Map<String,String> oneDocumentMap = new HashMap<String,Object>();
        oneDocumentMap.put("id", document.getId());
        oneDocumentMap.put("processInstanceId", contextCaseId.processInstanceId);
        oneDocumentMap.put("name", document.getName());
        oneDocumentMap.put("author", document.getAuthor());
        oneDocumentMap.put("creationDate", document.getCreationDate().getTime());
        oneDocumentMap.put("fileName", document.getContentFileName());
        oneDocumentMap.put("contentMimeType", document.getContentMimeType());
        oneDocumentMap.put("contentStorageId", document.getContentStorageId());
        oneDocumentMap.put("url", document.getUrl());
        oneDocumentMap.put("description", document.getDescription() );
        oneDocumentMap.put("version", document.getVersion());
        oneDocumentMap.put("index", document.getIndex());
        oneDocumentMap.put("contentFileName", document.getContentFileName());
        oneDocumentMap.put("hasContent", document.hasContent());

        Map<String,String> ctxDocumentMap = new HashMap<String,Object>();
        ctxDocumentMap.put("src", oneDocumentMap );

        if (document.getIndex() ==-1)
        {
            // not a multiple
            rootResult.put( document.getName(), ctxDocumentMap);
        }
        else
        {
            List listDocs= rootResult.get( document.getName());
            if (listDocs==null)
                listDocs = new ArrayList();
            // we may have the index 5 when at this moment the list contains only 2 item : extends the list
            while (listDocs.size()<= document.getIndex())
                listDocs.add( null );
            listDocs.set( document.getIndex(),ctxDocumentMap);
            rootResult.put( document.getName(), listDocs);
        }
    }
    /* -------------------------------------------------------------------------------- */
    /*																					*/
    /*	logRest																			*/
    /*																					*/
    /* -------------------------------------------------------------------------------- */
    /*
     private void logRest( boolean isLog, String logExplanation)
     {
     if (isLog)
     logger.info("com.bonitasoft.rest.context: "+logExplanation);
     }
     */




}
