package org.bonitasoft.rest.context;

import java.util.Map;
import java.util.logging.Logger;

import org.bonitasoft.engine.bpm.data.ArchivedDataNotFoundException;
import org.bonitasoft.engine.bpm.data.DataNotFoundException;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ActivityInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.parameter.ParameterCriterion;
import org.bonitasoft.engine.bpm.parameter.ParameterInstance;
import org.bonitasoft.engine.exception.RetrieveException;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* RestContextPilot                                                                                                                                    */
/*                                                                                                                                                                  */
/*  return the pilot to control what the REST API has to return                                                           */
/*                                                                                                                                                                  */
/* Pilot And ContextCaseId                                                                                                                      */
/*  theses two class must know each other, but their role is different :                                            */
/*     PILOT control the final result. The pilot is initialized from a String, and to find this string     */
/*       (it could be save in a local / global variable name, a parameters) then the Pilot need         */
/*      to get information from the ContextRestCaseid                                                                          */
/*   CONTEXTCASEID : The context Case Id keep all information about the caseId. It retrieve         */
/*      these information from the URL, from a caseId, a taskId or a contentStorageid                     */
/*                                                                                                                                                                  */
/*                                                                                                                                                                  */
/* ******************************************************************************** */

public class RestContextPilot {

	private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextPilot");

	/** describe the current analysis */
	String analysisString;

	String errorMessage;

	Map<String, Object> pilotDataMap;

	/*
	   execution context : "__execution" : 
	  		{ "accessright" : 
	  			{ "student" : "actor:newStudent", "teacher" : "actor:teacher" } 
	 		 "explicitinstantiation" :
	 		 	{ "vacancy" :  "restexentension:../API/myRestExention?theCaseToSearch={{taskid}}"; },
	  		"explicittask": {}, 
	  		"explicitcasearchived": {}, 
	  		"explicitcase": {},
	  		"explicitoverview" : 
	  			{ "staff" : "restcall:GET,http:/mysystem/{{caseid}}", 
	  			"vacancy" : "restexentension:../API/myRestExention?theCaseToSearch={{caseid}}" },
	  		"explicit" : 
	  			{ "vacancy" : "restexentension:../API/myRestExention?theCaseToSearch={{caseid}}" }
	  
	  		}
	  
	 */
	Map<String, Object> pilotExecutionContext;
	public final static String cstPilotExecutionContext = "__execution";

	public final static String cstPilotExplicitVariableInstanciation 	="explicitinstantiation"
	public final static String cstPilotExplicitVariableOverview = "explicitoverview";
	public final static String cstPilotExplicitVariableTask = "explicittask";
	public final static String cstPilotExplicitVariableCaseArchive = "explicitcasearchived";
	public final static String cstPilotExplicitVariableCaseAccess = "explicitcase";
	public final static String cstPilotExplicitVariableDefault = "explicite";

	public final static String cstPilotAccessRightInstanciation = "accessrightinstantiation";
	public final static String cstPilotAccessRightOverview = "accessrightoverview";
	public final static String cstPilotAccessRightTask = "accessrighttask";
	public final static String cstPilotAccessRightCaseArchive = "accessrightarchive";
	public final static String cstPilotAccessRightCaseAccess = "accessrightaccess";
	public final static String cstPilotAccessRightDefault = "accessright";
	

	Object contextDataOrigin = null;
	String contextDataSt = null;

	/**
	 * after the decodeParameters, we get the cont
	 */
	RestContextCaseId contextCaseId;

	RestContextTrackPerformance trackPerformance;

	boolean isAllowAllVariables;
	boolean isPilotDetected = false;

	public final static String cstActionFormat="format:";
	public final static String cstActionDefault="default:";
	public final static String cstActionFormatDate="date";
	public final static String cstActionFormatDateTime="datetime";
	public final static String cstActionFormatDateLong="datelong";
	public final static String cstActionFormatDateAbsolute="absolute";
	
	public final static String cstPermissionInitiator="initiator";
	public final static String cstPermissionAccessRight = "accessright:";
	public final static String cstPermissionTask = "task:";
	public final static String cstPermissionActor = "actor:";
	public final static String cstPermissionData="data";
	public final static String cstPermissionDataType="datatype";
	public final static String cstPermissionStar="*";
	public final static String cstPermissionPublic="public"
	public final static String cstPermissionAdmin="admin";
	public final static String cstPermissionSupervisor="supervisor";

	RestContextExpliciteVariable restContextExpliciteVariable;

	/**keep a trace of all permission executed
	 *  
	 */
	Map<String,Object> pilotAnalysePermission = new HashMap<String,Object>();
	
	public RestContextPilot()
	{
		restContextExpliciteVariable = new RestContextExpliciteVariable(this);
	}
	
	public void setContextCaseId(RestContextCaseId contextCaseId, RestContextTrackPerformance trackPerformance) {
		this.contextCaseId = contextCaseId;
		this.trackPerformance = trackPerformance;
	}

	/* ********************************************************************* */
	/*                                                                                                                                                                   */
	/* getter */
	/*                                                                                                                                                                  */
	/*                                                                                                                                                                  */
	/*
	/* ********************************************************************* */
	public RestContextCaseId getContextCaseId() {
		return contextCaseId;
	}

	public Map<String, Object> getPilotExecutionContext() {
		return pilotExecutionContext;
	}

	/* ********************************************************************* */
	/*                                                                                                                                                                   */
	/*
	 * Decode the parameters /*
	 */
	/*                                                                                                                                                                  */
	/* ********************************************************************* */

	/**
     * decode the pilot
     * @param contextCaseId
     */
    public void decodeParameters() {
        analysisString="";
        errorMessage=null;
        pilotDataMap=null;

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

        // first level is special because the case may be archived. Then, no need to search all the hierarchy (if we have a hierary, that's mean the case is alive)
        if (contextDataOrigin == null)
		{
			Long pid = contextCaseId.processInstance==null ? null : contextCaseId.processInstance.getId();
			Long aid = contextCaseId.archivedProcessInstance==null? null : contextCaseId.archivedProcessInstance.getSourceObjectId();
            searchContextData( pid, aid, contextCaseId.processDefinitionId );
		}

        if (contextDataOrigin == null && contextCaseId.processInstanceRoot!=null)
            searchContextData(contextCaseId.processInstanceRoot.getId() , null, contextCaseId.processInstanceRoot.getProcessDefinitionId() );



        //  still null and a parent process ? Look here

        // no data were given : create a default one
        if (contextDataOrigin == null)
        {
            contextDataSt = "{ \"*\" : \"all\" }";
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
        
        // is the pilot contains a "_execution" variable ? 
        pilotExecutionContext= new HashMap<String,Object>();
        if (pilotDataMap.containsKey( cstPilotExecutionContext ))
        {
        	if (pilotDataMap.get( cstPilotExecutionContext) ==null)
        		analysisString+"Bad execution context: expected MAP found NULL;"
        	else if (pilotDataMap.get( cstPilotExecutionContext) instanceof Map )
        	{
        		pilotExecutionContext = pilotDataMap.get( cstPilotExecutionContext);
        		analysisString+"Detect execution context;"
        	}
        	else
        	{
        		analysisString+"Bad execution context: expected MAP found ["+pilotDataMap.get( cstPilotExecutionContext).getClass().toString()+"]";
        		
        	}
        	pilotDataMap.remove( cstPilotExecutionContext );
        	
        }
        
        contextCaseId.log( analysisString );
    }

	
	/* ********************************************************************* */
	/*                                                                                                                                                                   */
	/*
	 * get information from the pilot /*
	 */
	/*                                                                                                                                                                  */
	/* ********************************************************************* */
	/**
	 * gettert
	 * @return
	 */
	public String getAnalysisString() {
		return analysisString;
	};

	public String getErrorMessage() {
		return errorMessage;
	}

	
	private addPermissionAnalysis( String varName, String analysis, boolean result)
	{
		if (pilotAnalysePermission.get(varName)!=null)
			return;
		Map<String,Object> oneAnalysis= new HashMap<String,Object>();
		oneAnalysis.put("name", varName);
		oneAnalysis.put("analysis", analysis);
		oneAnalysis.put("result", result);
		pilotAnalysePermission.put(varName, oneAnalysis);
	}
		
		
	/**
	 * return the Permission analysis
	 * @return
	 */
	public Map<String,Object> getAnalysisPermission()
	{
		return pilotAnalysePermission;
	}


	public boolean isPilotDetected() {
		return this.isPilotDetected;
	}

	/**
	 * different getter
	 * 
	 * @return
	 */
	public Map<String, Object> getPilotDataMap() {
		return pilotDataMap;
	}

	public String getActionFromVariable(String varName) {
		Object varObj = pilotDataMap.get(varName);
		if (varObj == null)
			return varObj;
		return varObj.toString();
	}

	

	/**
	 *
	 */
	private void searchContextData(Long processInstanceId, Long archivedSourceProcessInstanceId, Long processDefinitionId) {
		if (processInstanceId != null) {
			try {
				contextDataOrigin = contextCaseId.processAPI.getProcessDataInstance("globalcontext", processInstanceId);
				this.isPilotDetected = true;

				contextDataSt = contextDataOrigin.getValue();
				analysisString += "ProcessDataInstance[globalcontext] value=" + contextDataOrigin.getValue();
				return;
			} catch (DataNotFoundException dnte)
			// ok, maybe no context where given ?
			{
			}
		}
		if (archivedSourceProcessInstanceId != null) {
			try {
				contextDataOrigin = contextCaseId.processAPI.getArchivedProcessDataInstance("globalcontext", archivedSourceProcessInstanceId);
				contextDataSt = contextDataOrigin.getValue();
				this.isPilotDetected = true;

				analysisString += "ArchivedProcessDataInstance[globalcontext] value=" + contextDataOrigin.getValue();
				return;
			} catch (ArchivedDataNotFoundException dnte)
			// still Ok, search after
			{
			}
		}

		// maybe a parameters exist
		if (processDefinitionId != null) {
			List<ParameterInstance> listParameters = contextCaseId.processAPI.getParameterInstances(processDefinitionId, 0, 500, ParameterCriterion.NAME_ASC);
			if (listParameters != null)
				for (ParameterInstance parameter : listParameters) {
					if ("paramcontext".equalsIgnoreCase(parameter.getName())) {
						contextDataOrigin = parameter;
						contextDataSt = parameter.getValue();
						this.isPilotDetected = true;

						analysisString += "Parameters [paramcontext]";
						return;
					}
				}
		}
	}

	/*
	 * *************************************************************************
	 * ********
	 */
	/*                                                                                                                                                                   */
	/* ExplicitVariable access */
	/*                                                                                                                                                                  */
	/*                                                                                                                                                                  */
	/*
	 * *************************************************************************
	 * *******
	 */
	/**
	 * return the expliciteVariable manager
	 */
	public RestContextExpliciteVariable getExpliciteVariable() {
		return restContextExpliciteVariable;
	}

	/*
	 * *************************************************************************
	 * ********
	 */
	/*                                                                                                                                                                   */
	/* AccessRight */
	/*                                                                                                                                                                  */
	/*                                                                                                                                                                  */
	/*
	 * *************************************************************************
	 * *******
	 */

	public String getAccessRight( accessName )
	{
		Object variableValue=null;
		if ( contextCaseId.isProcessInstanciation() )
			variableValue = getExecutionContextValueInContainer( cstPilotAccessRightInstanciation, accessName);
		else if ( contextCaseId.isProcessOverview() )
			variableValue = getExecutionContextValueInContainer( cstPilotAccessRightOverview, accessName);
		else if ( contextCaseId.isTaskExecution() )
			variableValue = getExecutionContextValueInContainer( cstPilotAccessRightTask, accessName);
		else if ( contextCaseId.isCaseArchived() )
			variableValue = getExecutionContextValueInContainer( cstPilotAccessRightCaseArchive, accessName);
		else if ( contextCaseId.isCaseAccess() )
			variableValue = getExecutionContextValueInContainer( cstPilotAccessRightCaseAccess, accessName);
		
		if (variableValue==null)
			variableValue = getExecutionContextValueInContainer( cstPilotAccessRightDefault, accessName);

		return variableValue;

	}

	/*
	 * *************************************************************************
	 * ********
	 */
	/*                                                                                                                                                                   */
	/* getExecutionContextValue */
	/*                                                                                                                                                                  */
	/*                                                                                                                                                                  */
	/*
	 * *************************************************************************
	 * *******
	 */

	/**
	 * search in context the container and then the value. Container must be a MAP
	 * Example :
	 *   "accessrigh_overview" : { "student" : "this is the value" }
	 *   getExecutionContextValueInContainer("accessrigh_overview", "student") return "this is the value" 
	 
	 */
	private String getExecutionContextValueInContainer( String nameContainer, String varName)
	{
		Object container = pilotExecutionContext.get( nameContainer );
		if (container == null)
			return null;
		
		if (! (container instanceof Map))
		{
			logger.info("RestContextPilot: ExecutionContext Container["+nameContainer+"] is not a Map :["+container.getClass().toString());
			return null;
		}
		return ( (Map) container).get( varName );
	}

	/*
	 * *************************************************************************
	 * ********
	 */
	/*                                                                                                                                                                   */
	/* Access */
	/*                                                                                                                                                                  */
	/*
	 * the pilot control the access to any variable (processvariable, BDM,
	 * document, parameters)
	 */
	/* from the name, the control is done. */
	/*                                                                                                                                                                  */
	/*
	 * *************************************************************************
	 * *******
	 */

	/** Access */
	public boolean isAllowVariableName(String varName) {
		contextCaseId.log("context.isAllowVariableName[" + varName + "] ? AllowAllVariables=" + isAllowAllVariables);
		if (isAllowAllVariables)
			return true;

		// call a static method because this method is copy/paste in the
		// GroovySecurity
		return checkPermission(varName, pilotDataMap);
	}
	/*
	 * -------------------------------------------------------------------------
	 * -------
	 */
	/*                                                                                  */
	/* CheckPermission */
	/*                                                                                  */
	/* This methode is copy/paste in the groovy security script */
	/*
	 * -------------------------------------------------------------------------
	 * -------
	 */

	private boolean checkPermission(String varName, Map<String, Object> pilotDataMap) {

		String permissionControl = pilotDataMap.getAt(varName);
		if (permissionControl == null) {
			contextCaseId.log("isAllowVariableName[" + varName + "] NoPermission(yes)");
			addPermissionAnalysis( varName, "NoPermission(Access Allowed)", true);
			
			return true;
		}

		return checkPermissionString(varName, permissionControl);
	}

	private Map<String,Boolean > cachePermissionStatus=new HashMap<String, Boolean>();
	/**
     * this method can be call from a general varName, or for a more complex structure
     * @param varName for information only
     * @param permissionControl  the permission string, a set of "actor:XXX; etc...
     * @return
     */
    public boolean checkPermissionString(String varName, String permissionControl)
    {
        String analysis="isAllowVariableName["+varName+"] found["+permissionControl+"]";
        
        // the same variable can be asked multiple time in case of a list of. So, to optimise it, keep track on the result for a permissionControl
        Boolean allowAccessInCache = cachePermissionStatus.get(permissionControl);
        if (allowAccessInCache!=null)
        {
        	analysis+=",fromCache["+allowAccessInCache+"]";
            contextCaseId.logWithPrefix("RestContextPilot.checkPermissionString : analysis:", analysis);
			addPermissionAnalysis( varName, analysis, allowAccessInCache);
			
        	return allowAccessInCache;
        }
        
        Map<String,Object> trackSubOperation = trackPerformance.startSubOperation("checkPermissionString["+varName+"]");

        boolean allowAccess=false;

        long processInstanceId =-1;
        long processDefinitionId=-1;
        if (contextCaseId.processInstance!=null) {
            processInstanceId   = contextCaseId.processInstance.getId();
            processDefinitionId = contextCaseId.processInstance.getProcessDefinitionId();
        }
        if (contextCaseId.archivedProcessInstance!=null) {
            processInstanceId   = contextCaseId.archivedProcessInstance.getId();
            processDefinitionId = contextCaseId.archivedProcessInstance.getProcessDefinitionId();
        }
        // analysis +="PID["+processInstanceId+"] ProcDef["+processDefinitionId+"]";

        if (permissionControl==null)
        {
            analysis += "; NoPermission(yes)";
            allowAccess=true;
        }
        else
        {
            // permission is a set of info separate by a ;
        	// Special: the permissionControl may contains no permission at all (example, only "" or "format:date").
        	// Is that situation, we consider that the default permission is "public"
        	// the marker "onePermissionIsDefine" contains this information
        	boolean onePermissionIsDefine=false;
            StringTokenizer st = new StringTokenizer( permissionControl, ";");

            while ( st.hasMoreTokens() && ( !  allowAccess) )
            {
                String onePermission= st.nextToken();
                analysis+=";["+onePermission+"]";
                if (cstPermissionData.equals(onePermission) || (cstPermissionPublic.equals(onePermission) || cstPermissionDataType.equals(onePermission) || cstPermissionStar.equals(onePermission)))
                {
                    analysis+=":publicAccess";
                    onePermissionIsDefine=true;
                    allowAccess=true;
                }
                else if (cstPermissionAdmin.equals( onePermission ))
                {
                    allowAccess= contextCaseId.isAdministratorUser();
                    onePermissionIsDefine=true;
                    analysis+=":admin ? "+allowAccess;
                }
				else if (cstPermissionSupervisor.equals( onePermission ))
				{
					allowAccess= contextCaseId.isSupervisorUser();
                    onePermissionIsDefine=true;
                    analysis+=":supervisor ? "+allowAccess;
				}
                else if (cstPermissionInitiator.equals( onePermission ))
                {
                    onePermissionIsDefine=true;
                    analysis+=":initiator ? ";
                    if  ((contextCaseId.processInstance != null) && (contextCaseId.processInstance.getStartedBy() == contextCaseId.userId))
                    {
                        analysis+="yes;";
                        allowAccess=true;
                    } else if ( (contextCaseId.archivedProcessInstance != null) && (contextCaseId.archivedProcessInstance.getStartedBy() == contextCaseId.userId))
                    {
                        analysis+="yes;";
                        allowAccess=true;
                    }
                    else
                        analysis+="no;";
                }
                else if (onePermission.startsWith(cstPermissionTask))
                {
                    onePermissionIsDefine=true;
                    String taskName = onePermission.substring( cstPermissionTask.length());
                    analysis+=":taskName["+taskName+"] ";
                    SearchOptionsBuilder searchOptions = new SearchOptionsBuilder(0,100);
                    searchOptions.filter( ActivityInstanceSearchDescriptor.PARENT_PROCESS_INSTANCE_ID, processInstanceId)
                    searchOptions.filter( ActivityInstanceSearchDescriptor.NAME,taskName);
                    SearchResult<ActivityInstance> searchResult = contextCaseId.processAPI.searchActivities(searchOptions.done());
                    for (ActivityInstance activity : searchResult.getResult())
                    {
                        if (contextCaseId.processAPI.canExecuteTask(activity.getId(), contextCaseId.userId))
                        {
                            analysis+="yesTaskid["+activity.getId()+"]";
                            allowAccess=true;
                        }
                    }
                    // same with archived task
                    searchOptions = new SearchOptionsBuilder(0,100);
                    searchOptions.filter( ArchivedActivityInstanceSearchDescriptor.PARENT_PROCESS_INSTANCE_ID, processInstanceId)
                    searchOptions.filter( ArchivedActivityInstanceSearchDescriptor.NAME,taskName);
                    SearchResult<ArchivedActivityInstance> searchResultArchived = contextCaseId.processAPI.searchArchivedActivities(searchOptions.done());
                    for (ArchivedActivityInstance archivedActivity : searchResultArchived.getResult())
                    {
                        if (archivedActivity.getExecutedBy()== contextCaseId.userId)
                        {
                            analysis+="yesArchived taskid["+archivedActivity.getId()+"]";
                            allowAccess=true;
                        }
                    }
                }
                else if (onePermission.startsWith(cstPermissionActor))
                {
                    onePermissionIsDefine=true;
                    String actorName = onePermission.substring(cstPermissionActor.length());
                    analysis+=":actorName["+actorName+"] ProcessDef["+processDefinitionId+"]";
                    Boolean isPart = isPartOfActor( actorName, processDefinitionId );
                    if (isPart==null)
                        analysis+=":Actor not found;"
                    else if (isPart)
                    {
                        analysis+="yesActor";
                        allowAccess=true;
                    }
                }
                else if (onePermission.startsWith( cstPermissionAccessRight ))
                {
                    onePermissionIsDefine=true;
                    String accessName = onePermission.substring(cstPermissionAccessRight.length());
                    analysis+=":accessRight["+accessName+"]";
                    Boolean isPart = isPartOfAccessRight( varName, accessName );
                    if (isPart==null)
                        analysis+=":accessRight not found;"
                    else if (isPart)
                    {
                        analysis+="yesAccessRight";
                        allowAccess=true;
                    }
                	
                }
                else if (onePermission.startsWith("format:"))
                {
                    // format : not impact the permission
                }

            } // end loop on permission
			if (! onePermissionIsDefine)
			{
				analysis+=",noPermissionDefined:True by default";
				allowAccess=true;
			}
        } // end permission==null

      
        analysis+=", ALLOW="+allowAccess;
        contextCaseId.logWithPrefix("RestContextPilot.checkPermissionString : analysis:", analysis);
        trackPerformance.endSubOperation( trackSubOperation);

		addPermissionAnalysis( varName, analysis, allowAccess);
		
		
        cachePermissionStatus.put(permissionControl, allowAccess);
        
        return allowAccess;
    }

	/**
	 * AccessRigh Cache
	 *
	 */
	private Map<String, Boolean> cacheAccessRight = new HashMap<String, Boolean>();

	public Boolean isPartOfAccessRight(String varName, String accessRight) {
		if (cacheAccessRight.containsKey(accessRight))
			return cacheAccessRight.get(accessRight);
		String permissionControl = getAccessRight(accessRight);

		if (permissionControl != null) {
			boolean isPart = checkPermissionString("accessRight("+accessRight+")", permissionControl);
			cacheAccessRight.put(accessRight, isPart);
			return isPart;
		}

		cacheAccessRight.put(accessRight, null);
		return null;
	}

	/**
	 * Actor Cache
	 *
	 */

	private Map<String, Boolean> cacheActor = new HashMap<String, Boolean>();

	/**
     * isPart : return True (part of ) False (not part of ) NULL (actor unknown
     * @param actorName
     * @return
     */
    public Boolean isPartOfActor(String actorName, long processDefinitionId)
    {
        if (cacheActor.containsKey( actorName ))
        {
            return  cacheActor.get( actorName );
        }

        // calculated and save it
        int startIndex=0;
        try
        {
            List<Long> listUsersId = contextCaseId.processAPI.getUserIdsForActor(processDefinitionId, actorName, startIndex, 1000);

            while (listUsersId.size()>0)
            {
                // logger.info("getUserIdsForActor start at "+startIndex+" found "+listUsersId.size()+" record")
                startIndex+= 1000;
                if (listUsersId.contains( contextCaseId.userId ))
                {
                    cacheActor.put(actorName, Boolean.TRUE );
                    return true;
                }
                else
                    listUsersId = contextCaseId.processAPI.getUserIdsForActor(processDefinitionId, actorName, startIndex, 1000);
            }
        }catch (RetrieveException e){
            cacheActor.put(actorName,null);
            return null;
            // analysis+=":Actor not found;";
        }
        cacheActor.put(actorName, Boolean.FALSE )
        return false;
    }

}