package org.bonitasoft.rest.context

import groovy.json.JsonBuilder
import groovy.json.JsonException

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.logging.Logger

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.http.HttpHeaders
import org.bonitasoft.engine.api.APIClient
import org.bonitasoft.engine.api.BusinessDataAPI
import org.bonitasoft.engine.api.IdentityAPI
import org.bonitasoft.engine.api.ProcessAPI
import org.bonitasoft.engine.api.ProfileAPI
import org.bonitasoft.engine.bdm.Entity
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO
import org.bonitasoft.engine.bpm.data.ArchivedDataInstance
import org.bonitasoft.engine.bpm.data.ArchivedDataNotFoundException
import org.bonitasoft.engine.bpm.data.DataInstance
import org.bonitasoft.engine.bpm.data.DataNotFoundException
import org.bonitasoft.engine.bpm.document.Document
import org.bonitasoft.engine.bpm.document.DocumentCriterion
import org.bonitasoft.engine.bpm.document.DocumentDefinition
import org.bonitasoft.engine.bpm.document.DocumentListDefinition
import org.bonitasoft.engine.bpm.flownode.FlowElementContainerDefinition
import org.bonitasoft.engine.bpm.parameter.ParameterCriterion
import org.bonitasoft.engine.bpm.parameter.ParameterInstance
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition
import org.bonitasoft.engine.business.data.BusinessDataReference
import org.bonitasoft.engine.business.data.MultipleBusinessDataReference
import org.bonitasoft.engine.business.data.SimpleBusinessDataReference
import org.bonitasoft.engine.session.APISession
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder


/**
 * See page.properties for list of version
 */
class RestContextHandleGet implements RestApiController {


    private static Logger logger = Logger.getLogger(RestContextHandleGet.class.getName());





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


            contextCaseId.log( "=================== Start GetContext RESTAPI 7.2.5 TB");
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
                    contextCaseId.log("No Permission");
                    canContinue=false;
                }
            }

            String version = request.getParameter("version");
            if (version!=null)
                contextResult.put("version", "2.7");


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
            contextCaseId.logError( rootResult,"Bad JSON "+sourceContextData+" : "+je.toString());
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

            if (tempList!=null)
            {
                contextCaseId.log( "Collect BusinessData from: processinstanceid["+tempList.size()+"]");
                for (BusinessDataReference bde : tempList)
                    listBusinessData.put( bde.getName(), bde );

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


        // now, process the list
        for (Object varName : contextCaseId.getPilot().getPilotDataMap().keySet())
        {
            String varAction = contextCaseId.getPilot().getActionFromVariable( varName );
            contextCaseId.log( "Loop Get variable["+varName+"] / action["+varAction+"]");

            if (varName.equals("*"))
            {
                Long instanceForBdm = null;
                //------------ active part
                contextCaseId.log( "   get[*] processInstance/activity["+contextCaseId.processInstance+"/"+contextCaseId.activityInstance+"]");

                if (contextCaseId.processInstance!=null)
                {
                    instanceForBdm = contextCaseId.processInstance.getId();
                    Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstances");
                    List<DataInstance> listDataInstance = processAPI.getProcessDataInstances(contextCaseId.processInstance.getId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (DataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** DataInstance detected ["+data.getName()+"]");
                        completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance );
                    }
                    trackPerformance.addMarker("getAllProcessData");
                }

                if (contextCaseId.activityInstance!=null)
                {
                    Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getActivityDataInstances");
                    List<DataInstance> listDataInstance = processAPI.getActivityDataInstances(contextCaseId.activityInstance.getId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (DataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** LocalDataInstance detected ["+data.getName()+"] contentType["+data.getContainerType()+"]");
                        if (! "PROCESS_INSTANCE".equals( data.getContainerType() ))
                            completeValueProcessVariable(rootResult, data.getName(), varAction, contextCaseId, apiClient,contextCaseId.getPilot().getPilotDataMap(), trackPerformance );
                    }
                    trackPerformance.addMarker("getAllActivityData");

                }
                // ----- archived part
                if (contextCaseId.archivedProcessInstance!=null)
                {
                    instanceForBdm = contextCaseId.archivedProcessInstance.getId();
                    Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getArchivedProcessDataInstances");
                    List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedProcessDataInstances(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (ArchivedDataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** ArchivedDataInstance detected ["+data.getName()+"] contentType["+data.getContainerType()+"]");
                        if (! "PROCESS_INSTANCE".equals( data.getContainerType() ))
                            completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(), trackPerformance  );
                    }
                    trackPerformance.addMarker("getAllArchivedProcessData");

                }
                if (contextCaseId.archivedActivityInstance!=null)
                {
                    Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getArchivedActivityDataInstances");
                    List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedActivityDataInstances(contextCaseId.archivedActivityInstance.getSourceObjectId(), 0,1000);
                    trackPerformance.endSubOperation( trackSubOperation);

                    for (ArchivedDataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** ArchivedActivityDataInstance detected ["+data.getName()+"]");
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
                    contextCaseId.log( "Loop Get BDM["+businessData.getName()+"] / type["+businessData.getType()+"]");
                    completeValueBdmData( rootResult, businessData, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap(),trackPerformance  );
                }
                trackPerformance.addMarker("getBusinessData");

                //--------------------- parameters
                contextCaseId.log( "Check parameters");
                if (contextCaseId.processDefinitionId!=null)
                {
                    Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getParameterInstances");
                    List<ParameterInstance>	listParameters = processAPI.getParameterInstances(contextCaseId.processDefinitionId, 0, 100, ParameterCriterion.NAME_ASC);
                    trackPerformance.endSubOperation( trackSubOperation);

                    if (listParameters!=null)
                        for (ParameterInstance parameter : listParameters)
                    {
                        if ( contextCaseId.isAllowVariableName(	parameter.getName() ))
                            rootResult.put( parameter.getName(), parameter.getValue());
                    }
                }
            }
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

                // We want to load the data varName : is that a business Data ?
                if (listBusinessData.containsKey( varName ))
                {
                    Map<String,Object> mapPilot = contextCaseId.getPilot().getPilotDataMap() ;

                    completeValueBdmData( rootResult, listBusinessData.get( varName ), contextCaseId, apiClient, mapPilot, trackPerformance  );
                    trackPerformance.addMarker("getBdmData (completeValueBdmData) ["+varName+"]");
                }
                else
                {
                    Map<String,Object> mapPilot = contextCaseId.getPilot().getPilotDataMap() ;

                    completeValueProcessVariable( rootResult, varName, varAction, contextCaseId, apiClient, mapPilot, trackPerformance );
                    trackPerformance.addMarker("getData (completeValueProcessVariable)["+varName+"]");
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
            Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getLastVersionOfDocuments");
            List<Document> listDocuments = processAPI.getLastVersionOfDocuments(contextCaseId.processInstanceId, 0,100, DocumentCriterion.NAME_ASC);
            trackPerformance.endSubOperation( trackSubOperation);

            for (Document oneDoc : listDocuments)
            {
                if (oneDoc == null)
                    continue;

                if (! contextCaseId.isAllowVariableName(oneDoc.getName()))
                    continue;

                contextCaseId.log( "************************** Doc detected ["+(oneDoc==null ? null : (oneDoc.getName()+" index="+oneDoc.getIndex()))+"]");

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
        } else if (contextCaseId.processDefinitionId !=null)
        {
            // detect all document and create some empty variable
            Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("detectDocument");
            DesignProcessDefinition designProcessDefinition = processAPI.getDesignProcessDefinition( contextCaseId.processDefinitionId );
            FlowElementContainerDefinition flowElementContainerDefinition = designProcessDefinition.getFlowElementContainer();
            List<DocumentDefinition> listDocumentDefinition =	flowElementContainerDefinition.getDocumentDefinitions();
            for (DocumentDefinition documentDefinition : listDocumentDefinition )
                rootResult.put( documentDefinition.getName(), new HashMap());

            List<DocumentListDefinition> listDocumentListDefinition =	flowElementContainerDefinition.getDocumentListDefinitions();
            for (DocumentListDefinition documentListDefinition : listDocumentListDefinition )
                rootResult.put( documentListDefinition.getName(), new ArrayList());

            trackPerformance.endSubOperation( trackSubOperation);

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
    private void completeValueProcessVariable(Map<String,Object> rootResult,
                                              String varName,
                                              String varAction,
                                              RestContextCaseId contextCaseId,
                                              APIClient apiClient,
                                              Map<String,Object> pilotDataMap,
                                              RestContextTrackPerformance trackPerformance)
    {

        ProcessAPI processAPI = apiClient.processAPI;
        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

        contextCaseId.log( "=== completeValueProcessVariable.begin: Get variable["+varName+"] varAction["+varAction+"] pilotDataMap["+pilotDataMap+"]");

        RestContextTransformData restContextTransformData = new RestContextTransformData( contextCaseId );

        if (contextCaseId.processInstance != null)
            try
            {
                Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / getProcessData");
                DataInstance dataInstance = processAPI.getProcessDataInstance(varName.toString(), contextCaseId.processInstance.getId() );
                trackPerformance.endSubOperation( trackSubOperation);

                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a PROCESSDATA.a : ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");

                trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / transform");
                restContextTransformData.transform( rootResult, dataInstance.getName(), dataInstance.getName(), dataInstance.getValue(), pilotDataMap,0);
                trackPerformance.endSubOperation( trackSubOperation);

                return;
            } catch (DataNotFoundException dnte) {};

        if (contextCaseId.activityInstance != null)
            try
            {
                // logger.info("Try get localvariable["+varName+"]");
                Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / getActivityData");
                DataInstance dataInstance = processAPI.getActivityDataInstance(varName.toString(), contextCaseId.activityInstance.getId() );
                trackPerformance.endSubOperation( trackSubOperation);

                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a ACTIVITYDATA: ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
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

                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

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
                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

                trackSubOperation = trackPerformance.startSubOperation("getProcessDataInstance["+varName.toString()+"] / transform");
                restContextTransformData.transform( rootResult, archivedDataInstance.getName(),  archivedDataInstance.getName(),archivedDataInstance.getValue(),  pilotDataMap, 0);
                trackPerformance.endSubOperation( trackSubOperation);
                return;
            } catch (ArchivedDataNotFoundException dnte) {};
        }


        contextCaseId.log( "=== completeValueProcessVariable.end: Get variable["+varName+"]");

        return;
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
                loadBdmVariableOneLevel(rootResult, saveOneBdm, dataBdmEntity, pilotDataMap.get( businessData.getName() ),  contextCaseId );
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
    private void loadBdmVariableOneLevel(Map<String,Object> rootResult,
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
                            loadBdmVariableOneLevel(rootResult, bdmChild, value, contextInfo, contextCaseId);
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
                                loadBdmVariableOneLevel(rootResult, bdmChild, valueInList, contextInfo, contextCaseId);
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
