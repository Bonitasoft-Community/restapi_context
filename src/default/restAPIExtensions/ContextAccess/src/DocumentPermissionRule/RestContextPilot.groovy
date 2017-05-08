// package org.bonitasoft.rest.context;

import java.util.logging.Logger

import org.bonitasoft.engine.api.ProcessAPI
import org.bonitasoft.engine.bpm.data.ArchivedDataNotFoundException
import org.bonitasoft.engine.bpm.data.DataNotFoundException
import org.bonitasoft.engine.bpm.flownode.ActivityInstance
import org.bonitasoft.engine.bpm.flownode.ActivityInstanceSearchDescriptor
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstanceSearchDescriptor
import org.bonitasoft.engine.bpm.parameter.ParameterCriterion
import org.bonitasoft.engine.bpm.parameter.ParameterInstance
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance
import org.bonitasoft.engine.bpm.process.ProcessInstance
import org.bonitasoft.engine.exception.RetrieveException
import org.bonitasoft.engine.search.SearchOptionsBuilder
import org.bonitasoft.engine.search.SearchResult
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser


/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* RestContextPilot                                                                                                                                                         */
/*                                                                                                                                                                  */
/*  return the pilot to control what the REST API has to return                                                           */
/*                                                                                                                                                                  */
/* ******************************************************************************** */

public class RestContextPilot {

    private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextPilot");

    /** describe the current analysis */
    String analysisString;

    String errorMessage;

    Map<String,Object> pilotDataMap;

    RestContextCaseId contextCaseId;

    boolean isAllowAllVariables;
    boolean isPilotDetected=false;

    /**
     * decode the pilot
     * @param contextCaseId
     */
    public void decodeParameters( RestContextCaseId contextCaseId) {
        analysisString="";
        errorMessage=null;
        Object contextDataOrigin =null;
        String contextDataSt = null;
        pilotDataMap=null;
        this.contextCaseId = contextCaseId;

        this.isPilotDetected= false;

        if (contextCaseId.taskInstanceId !=null) {
            try {
                contextDataOrigin = contextCaseId.processAPI.getActivityDataInstance("context", contextCaseId.taskInstanceId);
                contextDataSt = contextDataOrigin.getValue();
                this.isPilotDetected= true;
                analysisString+="ActivityDataInstance[context] value="+contextDataOrigin.getValue();
            } catch(DataNotFoundException dnte )
            // Ok, no worry, let's try different options
            {}
        }
        if (contextDataOrigin == null && contextCaseId.processInstanceId !=null)
        {
            try
            {
                contextDataOrigin = contextCaseId.processAPI.getProcessDataInstance("globalcontext", contextCaseId.processInstanceId);
                this.isPilotDetected= true;

                contextDataSt = contextDataOrigin.getValue();
                analysisString+="ProcessDataInstance[globalcontext] value="+contextDataOrigin.getValue();
            } catch(DataNotFoundException dnte )
            // ok, maybe no context where given ?
            {}
        }
        if (contextDataOrigin == null && contextCaseId.archivedProcessInstance!=null)
        {
            try
            {
                contextDataOrigin =  contextCaseId.processAPI.getArchivedProcessDataInstance("globalcontext", contextCaseId.archivedProcessInstance.getSourceObjectId());
                contextDataSt = contextDataOrigin.getValue();
                this.isPilotDetected= true;

                analysisString+="ArchivedProcessDataInstance[globalcontext] value="+contextDataOrigin.getValue();
            } catch(ArchivedDataNotFoundException dnte )
            // still Ok, search after
            {}
        }


        // Still null ? maybe a parameters exist
        // bobparameters
        if (contextDataOrigin == null && contextCaseId.processDefinitionId!=null)
        {
            List<ParameterInstance> listParameters = contextCaseId.processAPI.getParameterInstances(contextCaseId.processDefinitionId, 0, 500, ParameterCriterion.NAME_ASC);
            if (listParameters!=null)
                for (ParameterInstance parameter : listParameters)
            {
                if ( "paramcontext".equalsIgnoreCase( parameter.getName() ))
                {
                    contextDataOrigin = parameter;
                    contextDataSt = parameter.getValue();
                    this.isPilotDetected= true;

                    analysisString+= "Parameters [paramcontext]";
                }
            }
        }

        // no data were given : create a default one
        if (contextDataOrigin == null)
        {
            contextDataSt = "{ \"*\" : \"all\",";
            contextDataSt += ", \""+RestContextCaseId.cstActionCaseId+"\" : \""+RestContextCaseId.cstActionCaseId+"\"";
            contextDataSt += ", \""+RestContextCaseId.cstActionProcessDefinitionId+"\" : \""+RestContextCaseId.cstActionProcessDefinitionId+"\"";
            contextDataSt += ", \""+RestContextCaseId.cstActionIsCaseArchived+"\" : \""+RestContextCaseId.cstActionIsCaseArchived+"\"";
            contextDataSt += ", \""+RestContextCaseId.cstActionTaskId+"\" : \""+RestContextCaseId.cstActionTaskId+"\"";
            contextDataSt += ", \""+RestContextCaseId.cstActionisTaskArchived+"\" : \""+RestContextCaseId.cstActionisTaskArchived+"\"";
            contextDataSt += "}";
            analysisString+="Default value="+contextDataSt;
        }

        isAllowAllVariables=false;

        final JSONParser jsonParser = new JSONParser();
        final JSONObject pilotData = (JSONObject) jsonParser.parse(contextDataSt);

        if (pilotData == null ) {
            errorMessage= "The JSON information is missing : "+analysisString;
        }
        else if (! pilotData instanceof Map)
        {
            errorMessage=  "Local Variable [context] must be a MAP";
        }
        else {
            pilotDataMap = (Map<String,Object>) pilotData;

            // for optimisation, we calculated now the isAllowAllVariables
            for (Object varNameIt : pilotDataMap.keySet())
            {
                if ("*".equals(varNameIt))
                {
                    isAllowAllVariables=true;
                }
            }
        }
        contextCaseId.logRest( analysisString);
    }


    public boolean isPilotDetected()
    { return this.isPilotDetected; }


    /**
     * different getter
     * @return
     */
    public  Map<String,Object> getPilotDataMap()
    {
        return pilotDataMap;
    }

    public String getActionFromVariable( String varName )
    {
        Object varObj = pilotDataMap.get( varName );
        if (varObj==null)
            return varObj;
        return varObj.toString();
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }


    /* ********************************************************************************* */
    /*                                                                                                                                                                   */
    /* Access                                                                                                                                                      */
    /*                                                                                                                                                                  */
    /*  the pilot control the access to any variable (processvariable, BDM, document, parameters)   */
    /* from the name, the control is done.                                                                                                   */
    /*                                                                                                                                                                  */
    /* ******************************************************************************** */

    /** Access */
    public boolean isAllowVariableName(String varName )
    {
        contextCaseId.logRest("context.isAllowVariableName["+varName+"] ? AllowAllVariables="+isAllowAllVariables);
        if (isAllowAllVariables)
            return true;

        // call a static method because this method is copy/paste in the GroovySecurity
        return checkPermission( varName, pilotDataMap, contextCaseId.userId,  contextCaseId.processInstance, contextCaseId.activityInstance, contextCaseId.archivedProcessInstance, contextCaseId.archivedActivityInstance, contextCaseId.processAPI);
    }
    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  CheckPermission                                                                 */
    /*                                                                                  */
    /*  This methode is copy/paste in the groovy security script                        */
    /* -------------------------------------------------------------------------------- */


    private boolean checkPermission(String varName, Map<String,Object> pilotDataMap, Long userId,  ProcessInstance processInstance, ActivityInstance activityInstance, ArchivedProcessInstance archivedProcessInstance, ArchivedActivityInstance archivedActivityInstance, ProcessAPI processAPI)
    {

        String permissionControl = pilotDataMap.getAt( varName );
        if (permissionControl==null)
        {
            contextCaseId.logRest("isAllowVariableName["+varName+"] found["+permissionControl+"]");
            return false;
        }

        String analysis="isAllowVariableName["+varName+"] found["+permissionControl+"]";
        boolean allowAccess=false;

        long processInstanceId =-1;
        long processDefinitionId=-1;
        if (processInstance!=null) {
            processInstanceId   = processInstance.getId();
            processDefinitionId = processInstance.getProcessDefinitionId();
        }
        if (archivedProcessInstance!=null) {
            processInstanceId   = archivedProcessInstance.getId();
            processDefinitionId = archivedProcessInstance.getProcessDefinitionId();
        }
        analysis +="processInstanceId["+processInstanceId+"] processDefinitionId["+processDefinitionId+"]";


        // permission is a set of info separate by a ;
        StringTokenizer st = new StringTokenizer( permissionControl, ";");

        while ( st.hasMoreTokens() && ( !  allowAccess) )
        {
            String onePermission= st.nextToken();
            analysis+=";["+onePermission+"]";
            if ("data".equals(onePermission) || ("public".equals(onePermission)))
            {
                analysis+=":publicAccess";
                allowAccess=true;
            }
            if ("initiator".equals( onePermission ))
            {
                analysis+=":initiator ? ";
                if  ((processInstance != null) && (processInstance.getStartedBy() == userId))
                {
                    analysis+="yes;";
                    allowAccess=true;
                } else if ( (archivedProcessInstance != null) && (archivedProcessInstance.getStartedBy() == userId))
                {
                    analysis+="yes;";
                    allowAccess=true;
                }
                else
                    analysis+="no;";
            }
            else if (onePermission.startsWith("task:"))
            {
                String taskName = onePermission.substring("task:".length());
                analysis+=":taskName["+taskName+"] ";
                SearchOptionsBuilder searchOptions = new SearchOptionsBuilder(0,100);
                searchOptions.filter( ActivityInstanceSearchDescriptor.PARENT_PROCESS_INSTANCE_ID, processInstanceId)
                searchOptions.filter( ActivityInstanceSearchDescriptor.NAME,taskName);
                SearchResult<ActivityInstance> searchResult = processAPI.searchActivities(searchOptions.done());
                for (ActivityInstance activity : searchResult.getResult())
                {
                    if (processAPI.canExecuteTask(activity.getId(), userId))
                    {
                        analysis+=" yes taskid["+activity.getId()+"]";
                        allowAccess=true;
                    }
                }
                // same with archived task
                searchOptions = new SearchOptionsBuilder(0,100);
                searchOptions.filter( ArchivedActivityInstanceSearchDescriptor.PARENT_PROCESS_INSTANCE_ID, processInstanceId)
                searchOptions.filter( ArchivedActivityInstanceSearchDescriptor.NAME,taskName);
                SearchResult<ArchivedActivityInstance> searchResultArchived = processAPI.searchArchivedActivities(searchOptions.done());
                for (ArchivedActivityInstance archivedActivity : searchResultArchived.getResult())
                {
                    if (archivedActivity.getExecutedBy()==userId)
                    {
                        analysis+=" yesArchived taskid["+archivedActivity.getId()+"]";
                        allowAccess=true;
                    }
                }
            }
            else if (onePermission.startsWith("actor:"))
            {
                String actorName = onePermission.substring("actor:".length());
                analysis+=":actorName["+actorName+"] ";
                int startIndex=0;
                try
                {
                    List<Long> listUsersId = processAPI.getUserIdsForActor(processDefinitionId, actorName, startIndex, 1000);

                    while (! allowAccess && listUsersId.size()>0)
                    {
                        logger.info("getUserIdsForActor start at "+startIndex+" found "+listUsersId.size()+" record")
                        startIndex+= 1000;
                        if (listUsersId.contains( userId ))
                        {
                            analysis+="part of actor";
                            allowAccess=true;
                        }
                        else
                            listUsersId = processAPI.getUserIdsForActor(processDefinitionId, actorName, startIndex, 1000);
                    }
                }catch (RetrieveException e){
                    analysis+=":Actor not found;";
                }
            }


        } // end loop on permission

        contextCaseId.logRest("analysis: "+analysis+" RESULT="+allowAccess);
        return allowAccess;
    }




}