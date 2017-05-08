package org.bonitasoft.rest.context;


import java.util.logging.Logger

import org.bonitasoft.web.extension.rest.RestAPIContext;
import org.bonitasoft.web.extension.rest.RestApiController
import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.text.SimpleDateFormat
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
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.bpm.flownode.FlowElementContainerDefinition
import org.bonitasoft.engine.bpm.parameter.ParameterCriterion
import org.bonitasoft.engine.bpm.parameter.ParameterInstance
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.business.data.BusinessDataReference
import org.bonitasoft.engine.business.data.MultipleBusinessDataReference
import org.bonitasoft.engine.business.data.SimpleBusinessDataReference
import org.bonitasoft.engine.identity.User
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
    /*	class PerformanceTrace															*/
    /*																					*/
    /* -------------------------------------------------------------------------------- */
    public static class PerformanceTrace {
        private List<Map<String,Object>> listOperations = new ArrayList<Map<String,Object>>();
        public void addMarker(String operation) {
            long currentTime= System.currentTimeMillis();
            Map<String,Object> oneOperation = new HashMap<String,Object>();
            oneOperation.put("t",System.currentTimeMillis());
            oneOperation.putAt("n",operation);
            listOperations.add( oneOperation );
        }

        public String trace() {
            String result="";
            for (int i=1;i<listOperations.size();i++) {
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
            pilot.setContextCaseId(contextCaseId );

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

            performanceTrace.addMarker("Detectparameter");

            //----------------------- get the pilot (contextDataOrigin and contextDataSt)
            if (canContinue)
            {

                // not : at this moment, the contextDataOrigin may be NOT NULL and the contextDataSt is null : then the parseText will failed

                performanceTrace.addMarker("getPilot");

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
                performanceTrace.addMarker("JsonParse");


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
                getContent( rootResult,  contextCaseId, performanceTrace, apiClient );

            }

            if (canContinue)
            {
                contextCaseId.completeResult( contextResult );
            }


            performanceTrace.addMarker("getFinalResult");
            if (contextCaseId.isLog)
            {
                contextCaseId.log( "Final rootResult "+rootResult.toString())
                contextCaseId.log( "Performance :"+performanceTrace.trace() );
                contextResult.put( "performanceRestContextCall", performanceTrace.trace() );
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
            contextCaseId.log( "=================== End GetContext RESTAPI");

            // Send the result as a JSON representation
            // You may use buildPagedResponse if your result is multiple
            return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(rootResult).toPrettyString())
        }
    }



    /**
     * get the content of the REST API
     * @param contextCaseId
     * @param context
     */
    private void getContent( Map<String,Object> rootResult,
            RestContextCaseId contextCaseId,
            PerformanceTrace performanceTrace2,
            APIClient apiClient)
    {
        // get the list of all Business Data
        ProcessAPI processAPI = apiClient.processAPI;
        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

        Map<String,BusinessDataReference> listBusinessData= new HashMap<String,BusinessDataReference>();


        if (contextCaseId.processInstanceId!=null)
        {
            contextCaseId.log( "Collect BusinessData from: processinstanceid["+contextCaseId.processInstanceId+"]");

            List<BusinessDataReference>  tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.processInstanceId, 0,1000);
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
            contextCaseId.log( "Collect BusinessData from: getArchivedProcessInstanceExecutionContext :archivedProcessInstance.getId() ["+listBusinessData.size()+"]");
            // logger.info(">>> *END* ArchivedProcessInstanceExecutionContext<<");

            List<BusinessDataReference>  tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getSourceObjectId(), 0,1000);
            if (tempList!=null)
            {
                contextCaseId.log( "Collect BusinessData from: archivedActivityInstance.getSourceObjectId() ["+tempList.size()+"]");
                for (BusinessDataReference bde : tempList)
                    listBusinessData.put( bde.getName(), bde );

            }
            tempList = businessDataAPI.getProcessBusinessDataReferences(contextCaseId.archivedProcessInstance.getId(), 0,1000);
            if (tempList!=null)
            {
                contextCaseId.log( "Collect BusinessData from: archivedActivityInstance.getId() ["+tempList.size()+"]");
                for (BusinessDataReference bde : tempList)
                    listBusinessData.put( bde.getName(), bde );
            }
        }
        performanceTrace2.addMarker("collectListBusinessData");


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
                    List<DataInstance> listDataInstance = processAPI.getProcessDataInstances(contextCaseId.processInstance.getId(), 0,1000);
                    for (DataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** DataInstance detected ["+data.getName()+"]");
                        completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap() );
                    }
                    performanceTrace2.addMarker("getAllProcessData");
                }

                if (contextCaseId.activityInstance!=null)
                {
                    List<DataInstance> listDataInstance = processAPI.getActivityDataInstances(contextCaseId.activityInstance.getId(), 0,1000);
                    for (DataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** LocalDataInstance detected ["+data.getName()+"]");
                        completeValueProcessVariable(rootResult, data.getName(), varAction, contextCaseId, apiClient,contextCaseId.getPilot().getPilotDataMap() );
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
                        contextCaseId.log( "************************** ArchivedDataInstance detected ["+data.getName()+"]");
                        completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap()  );
                    }
                    performanceTrace2.addMarker("getAllArchivedProcessData");

                }
                if (contextCaseId.archivedActivityInstance!=null)
                {
                    List<ArchivedDataInstance> listDataInstance = processAPI.getArchivedActivityDataInstances(contextCaseId.archivedActivityInstance.getSourceObjectId(), 0,1000);
                    for (ArchivedDataInstance data : listDataInstance)
                    {
                        contextCaseId.log( "************************** ArchivedActivityDataInstance detected ["+data.getName()+"]");
                        completeValueProcessVariable( rootResult, data.getName(), varAction, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap() );
                    }
                    performanceTrace2.addMarker("getAllArchivedActivityData");

                }



                // ---------------------- business Data
                // logRest(contextCaseId.isLog, "Search BDM with processInstanceId=["+contextCaseId.processInstanceId+"] instanceForBdm="+instanceForBdm);
                // logRest(contextCaseId.isLog, "contextCaseId.trace() =["+contextCaseId.trace()+"]");
                // logRest(contextCaseId.isLog, "archivedProcessInstance =["+contextCaseId.archivedProcessInstance+"]");

                for (BusinessDataReference businessData : listBusinessData.values())
                {
                    contextCaseId.log( "Loop Get BDM["+businessData.getName()+"] / type["+businessData.getType()+"]");
                    completeValueBdmData( rootResult, businessData, contextCaseId, apiClient, contextCaseId.getPilot().getPilotDataMap()  );
                }
                performanceTrace2.addMarker("getBusinessData");

                //--------------------- parameters
                contextCaseId.log( "Check parameters");
                if (contextCaseId.processDefinitionId!=null)
                {
                    List<ParameterInstance>	listParameters = processAPI.getParameterInstances(contextCaseId.processDefinitionId, 0, 100, ParameterCriterion.NAME_ASC);
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

                    completeValueBdmData( rootResult, listBusinessData.get( varName ), contextCaseId, apiClient, mapPilot  );
                    performanceTrace2.addMarker("getBdmData["+varName+"]");
                }
                else
                {
                    Map<String,Object> mapPilot = contextCaseId.getPilot().getPilotDataMap() ;

                    completeValueProcessVariable( rootResult, varName, varAction, contextCaseId, apiClient, mapPilot );
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
            RestContextCaseId contextCaseId,
            APIClient apiClient,
            Map<String,Object> pilotDataMap)
    {

        ProcessAPI processAPI = apiClient.processAPI;
        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

        contextCaseId.log( "=== completeValueProcessVariable.begin: Get variable["+varName+"] varAction["+varAction+"] pilotDataMap["+pilotDataMap+"]");

        RestContextTransformData restContextTransformData = new RestContextTransformData( contextCaseId );

        if (contextCaseId.processInstance != null)
            try
            {
                DataInstance dataInstance = processAPI.getProcessDataInstance(varName.toString(), contextCaseId.processInstance.getId() );
                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a PROCESSDATA.a : ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");

                restContextTransformData.transform( rootResult, "", dataInstance.getName(), dataInstance.getValue(), pilotDataMap,0);

                return;
            } catch (DataNotFoundException dnte) {};

        if (contextCaseId.activityInstance != null)
            try
            {
                // logger.info("Try get localvariable["+varName+"]");
                DataInstance dataInstance = processAPI.getActivityDataInstance(varName.toString(), contextCaseId.activityInstance.getId() );
                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a ACTIVITYDATA: ["+dataInstance.getValue()+"] class["+dataInstance.getClassName()+"]");
                restContextTransformData.transform( rootResult, "", dataInstance.getName(), dataInstance.getValue(),  pilotDataMap,0 );
                return;
            } catch (DataNotFoundException dnte) {};

        if (contextCaseId.archivedProcessInstance != null)
            try
            {
                contextCaseId.log( "completeValueProcessVariable: search variable["+varName+"] in getArchivedProcessDataInstance");
                ArchivedDataInstance archivedDataInstance = processAPI.getArchivedProcessDataInstance (varName.toString(), contextCaseId.archivedProcessInstance.getSourceObjectId() );
                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

                restContextTransformData.transform( rootResult, "", archivedDataInstance.getName(), archivedDataInstance.getValue(), pilotDataMap,0 );
                return;
            } catch (ArchivedDataNotFoundException dnte) {};

        if (contextCaseId.archivedActivityInstance != null)
        {

            try
            {
                contextCaseId.log( "completeValueProcessVariable: search variable["+varName+"] in getArchivedActivityDataInstance");
                ArchivedDataInstance archivedDataInstance = processAPI. getArchivedActivityDataInstance( varName.toString(), contextCaseId.archivedActivityInstance.getSourceObjectId() );
                contextCaseId.log( "completeValueProcessVariable: Get variable["+varName+"] is a ARCHIVEDPROCESSDATA : ["+archivedDataInstance.getValue()+"] class["+archivedDataInstance.getClassName()+"]");

                restContextTransformData.transform( rootResult,"",  archivedDataInstance.getName(),archivedDataInstance.getValue(),  pilotDataMap, 0);
                return;
            } catch (ArchivedDataNotFoundException dnte) {};
        }


        contextCaseId.log( "=== completeValueProcessVariable.end: Get variable["+varName+"]");

        return;
    }

    /**
     * save the value in the rootResult. If the value is a Object
     * @deprecated
     *
     private void completeValueFromDataDeprecated( Map<String,Object> rootResult,   String varName,    Object varValue,    Map<String,Object> pilotDataMap, RestContextCaseId contextCaseId )
     {
     if (pilotDataMap==null)
     return;
     Object pilotAction = pilotDataMap.get( varName );
     contextCaseId.log( "========================= completeValueFromData.begin: Get variable["+varName+"] action["+pilotAction+"] pilotAction["+pilotAction+"] pilotDataMap["+pilotDataMap+"] value["+varValue+"] ");
     if (pilotAction instanceof Map)
     {
     contextCaseId.log( " completeValueFromData.: Action is MAP : "+pilotAction);
     // attention, we may have at this point an object : it's time to transform it in MAP, LIST, ...
     Object varValueTransformed = varValue;
     if (varValueTransformed!= null)
     if (! ( ( varValueTransformed instanceof Map) || (varValueTransformed instanceof List)))
     {
     contextCaseId.log( " completeValueFromData.: Transform the object to MAP : "+varValueTransformed);
     String jsonSt = new JsonBuilder( varValueTransformed ).toPrettyString();
     varValueTransformed = new JsonSlurper().parseText(jsonSt);
     contextCaseId.log( " completeValueFromData.: Transform the objecin MAP : "+varValueTransformed);
     }
     // we have a Map like { Attribut1: xxx}
     if (varValueTransformed instanceof Map)
     {
     contextCaseId.log( " completeValueFromData.: Value is MAP : "+varValueTransformed);
     Map<String,Object> subResult = new HashMap<String,Object>();
     rootResult.put(varName, subResult);
     for (String key : pilotAction.keySet())
     {
     contextCaseId.log( " completeValueFromData. recursiv call : key["+key+"] varValueTransformed.get( key )["+varValueTransformed.get( key )+"] pilotAction["+pilotAction+"]");
     if (pilotAction.get( key ) instanceof Map)
     completeValueFromData( subResult, key, varValueTransformed.get( key ),   pilotAction.get( key ), contextCaseId);
     else
     rootResult.put(varName,  transformValue( varValueTransformed, pilotAction.get( key )==null ? null : pilotAction.get( key ).toString(),  contextCaseId ));
     }
     }
     else if (varValueTransformed instanceof List)
     {
     contextCaseId.log( " completeValueFromData.: Value is LIST : "+varValueTransformed);
     // Ok, apply the action on each element of the list
     List<Map<String,Object>> subResult = new ArrayList<Map<String,Object>>();
     rootResult.put(varName, subResult);
     for (int i=0; i< ((List) varValueTransformed).size();i++)
     {
     Map<String,Object> subResultIterator = new HashMap<String,Object>();
     subResult.add(subResultIterator);
     for (String key : pilotAction.keySet())
     {
     completeValueFromData( subResultIterator, key, ((List) varValueTransformed).getAt( i ).get( key ), pilotAction.get( key ), contextCaseId);
     }
     }
     }
     else if (varValue == null)
     {
     rootResult.put(varName,null);
     }
     else
     {
     contextCaseId.log( " completeValueFromData.: Value is not a MAP and not a LIST do nothing : "+varValue.getClass().getName());
     // action is a MAP and the value is not... not, do nothing here
     }
     } // end of varcontext as a Map
     else
     {
     contextCaseId.log( " completeValueFromData.: Direct transformation ("+(pilotAction==null ? "data" : pilotAction.toString())+")");
     rootResult.put(varName, transformValue( varValue, pilotAction==null ? "data" : pilotAction.toString(), contextCaseId) );
     if (contextCaseId.isLog)
     {
     contextCaseId.log( " completeValueFromData. Set in["+varName+"] : ["+rootResult.get(varName)+"]");
     if (rootResult.get(varName)!=null)
     {
     String jsonSt = new JsonBuilder( rootResult.get(varName) ).toPrettyString();
     // contextCaseId.log( " completeValueFromData. JSON="+jsonSt);
     }
     }
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
     contextCaseId.log( "========================= completeValueFromData.end: Get variable["+varName+"]");
     }
     */
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
            RestContextCaseId contextCaseId,
            APIClient apiClient,
            Map<String,Object> pilotDataMap)
    {

        ProcessAPI processAPI = apiClient.processAPI;
        BusinessDataAPI businessDataAPI = apiClient.businessDataAPI;

        // contextCaseId.log("completeValueBdmData: Get variable["+businessData.getName()+"]");

        try
        {

            // contextCaseId.log("completeValueBdmData.2: Get Business Reference ["+businessData.getName()+"]");

            // the result is maybe a HASHMAP or a LIST<HASMAP>
            Object resultBdm = null;
            boolean isMultiple=false;
            List<Long> listStorageIds = new ArrayList<Long>();
            if (businessData instanceof MultipleBusinessDataReference)
            {
                // this is a multiple data

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
                resultBdm = new HashMap<String,Object>();
                isMultiple=false;
                contextCaseId.log( "completeValueBdmData.3: Get SIMPLE Business Reference ["+businessData.getName()+"] : type["+businessData.getType()+"]");
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
                contextCaseId.logError( rootResult, "completeValueBdmData.5:>>>>>>>>>>>>>>>>>>>>  DaoClass ["+classDAOName+"] not Found");

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

                loadBdmVariableOneLevel(rootResult, saveOneBdm, dataBdmEntity, pilotDataMap.get( businessData.getName() ) );


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
    private void loadBdmVariableOneLevel( Map<String,Object> rootResult,
            Map<String,Object> saveLocalLevel,
            Entity dataBdmEntity,
            Map<String,Object> contextLocalLevel)
    {
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
                            loadBdmVariableOneLevel(rootResult, bdmChild, value, contextInfo )
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
                                loadBdmVariableOneLevel(rootResult, bdmChild, valueInList, contextInfo)
                            }

                        }
                    }
                    else
                        saveLocalLevel.put(nameAttribute, transformValue( value, contextLocalLevel==null ? null : contextLocalLevel.get( nameAttribute), contextCaseId ));

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
    /*	transformValue																	*/
    /*																					*/
    /* -------------------------------------------------------------------------------- */

    /**
     * Transform the value accord
     */
    private static SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    private Object transformValue( Object data, String varAction, RestContextCaseId contextCaseId )
    {
        if (data==null)
            return null;
        if (data instanceof Date)
        {
            contextCaseId.log("========= transformValue["+data+"] <date> varAction["+varAction+"]")
            if ("date".equals(varAction))
                return sdfDate.format( (Date) data);
            else if ("datetime".equals(varAction))
                return sdfDateTime.format( (Date) data);
            else if ("datelong".equals(varAction))
                return ((Date) data).getTime();

            // use the default
            if (contextCaseId.isDateFormatLong() )
                return ((Date) data).getTime();
            if (contextCaseId.isDateFormatTime())
                return sdfDateTime.format( (Date) data);
            if (contextCaseId.isDateFormatJson() )
                return sdfDate.format( (Date) data);

            // default : be compatible with the UIDesigner which wait for a timestamp.
            return ((Date) data).getTime();

        }
        if (data instanceof List)
        {
            contextCaseId.log("========= transformValue["+data+"] <list> varAction["+varAction+"]")
            List<Object> listTransformed= new ArrayList<Object>();
            for (Object onItem : ((List) data))
            {
                // propagate the varAction to transform the date
                listTransformed.add( transformValue( onItem, varAction, contextCaseId ));
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
    /*
     private void logRest( boolean isLog, String logExplanation)
     {
     if (isLog)
     logger.info("com.bonitasoft.rest.context: "+logExplanation);
     }
     */




}
