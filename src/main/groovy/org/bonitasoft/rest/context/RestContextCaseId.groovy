package org.bonitasoft.rest.context;

import javax.servlet.http.HttpServletRequest




import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.ProfileAPI;
import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.identity.UserSearchDescriptor;
import org.bonitasoft.engine.bpm.document.DocumentsSearchDescriptor;
import org.bonitasoft.engine.profile.Profile;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;


/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* RestContextCaseId                                                                                                                                */
/*                                                                                                                                                                 */
/*  retrieve all information about the caseId, or from a taskId, a processDefinitionId                    */
/* or a ContentStorageId                                                                                                                         */
/*                                                                                                                                                                 */
/* Pilot And ContextCaseId                                                                                                                      */
/*  theses two class must know each other, but their role is different :                                            */
/*     PILOT control the final result. The pilot is initialized from a String, and to find this string     */
/*       (it could be save in a local / global variable name, a parameters) then the Pilot need         */
/*      to get information from the ContextRestCaseid                                                                          */
/*   CONTEXTCASEID : The context Case Id keep all information about the caseId. It retrieve         */
/*      these information from the URL, from a caseId, a taskId or a contentStorageid                     */
/*                                                                                                                                                                  */
/* ******************************************************************************** */


/* -------------------------------------------------------------------------------- */
/*                                                                                  */
/*  class ContextCaseId                                                             */
/*                                                                                  */
/* -------------------------------------------------------------------------------- */
// a Case has multiple access : a ProcessInstance or a ArchiveProcessInstance, a TaskInstance or an ArchiveTaskInstance...
public class RestContextCaseId {

    private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextCaseId");


    public static final String cstActionCaseId = "caseId";
    public static final String cstActionProcessDefinitionId = "processDefinitionId";
    public static final String cstActionIsCaseArchived = "isCaseArchived";
    public static final String cstActionTaskId = "taskId";
    public static final String cstActionisTaskArchived = "isTaskArchived";



    public Long taskInstanceId = null;
    public Long processInstanceId = null;
    public Long processDefinitionId = null;
    public Long contentStorageId=null;
    public Long documentId=null;
    public Document document=null;




    // different item loaded
    public ProcessDefinition processDefinition=null;
    public ProcessInstance processInstance=null;
    public ActivityInstance activityInstance = null;
    public ArchivedProcessInstance archivedProcessInstance = null;
    public ArchivedActivityInstance archivedActivityInstance=null;

    /**
     * when we have a callmap, the task may be a task in the subprocess. In this moment, the scope may be use to decide were the value has to be retrieve.
     * We save the local information processInstanceParent (of the task) and the processInstanceRoot
     */
    ProcessInstance  processInstanceParent = null;
    ProcessInstance  processInstanceRoot = null;


    /**
     * it's one of this possibility
     */
    public enum TypeUrl {
        PROCESSINSTANCIATION, CASEOVERVIEW, TASKEXECUTION, UNKNOW
    };

    TypeUrl detectTypeUrl = TypeUrl.UNKNOW;

    /*
     * specify the scope of the case to search.
     * - DEFAULT : if this is a caseId, then result is the caseId given. A task ? caseId= case( task ) so maybe the subCaseId
     * - ROOT : the caseId is force to the root parent
     */
    public enum ScopeSearch {  ROOT, DEFAULT};
    public ScopeSearch scopeSearch = ScopeSearch.DEFAULT;



    Long userId = null;
    ProcessAPI processAPI;
    IdentityAPI identityAPI;
    ProfileAPI profileAPI;


    /** describe the current analysis */
    String analysisString;

    public enum DateFormat { DATELONG, DATETIME, DATEJSON };
    private DateFormat defaultDateFormat= DateFormat.DATELONG;



    boolean isLog=false;
    Boolean isLogFromParameter=null;


    public RestContextCaseId( Long userId, ProcessAPI processAPI, IdentityAPI identityAPI,  ProfileAPI profileAPI)
    {
        this.userId = userId;
        this.processAPI= processAPI;
        this.identityAPI = identityAPI;
        this.profileAPI = profileAPI;
    }

    /**
     * getter
     */
    public Long getProcessDefinitionId()
    {
        if ( processDefinition!=null)
            return processDefinition.getId();
    }



    public isDateFormatLong()
    {
        return defaultDateFormat==DateFormat.DATELONG;
    }
    public boolean isDateFormatTime()
    {
        return defaultDateFormat==DateFormat.DATETIME;
    }
    public isDateFormatJson()
    {
        return defaultDateFormat==DateFormat.DATEJSON;
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
    RestContextPilot pilot;

    public void setPilot(RestContextPilot pilotParam )
    {
        pilot = pilotParam;
    }

    public RestContextPilot getPilot()
    {
        return pilot;

    }

    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  Getter                                                                        */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */

    /** check if the context allow the access of this variable
     * 2.7 : only for Document
     * @param varName
     * @return
     */
    public boolean isAllowVariableName(String varName )
    {
        return pilot.isAllowVariableName(varName);
    }

    /**
     * check if user can access the perimter requested
     */
    public boolean isAllowContext()
    {
        if (isAdministratorUser())
        {
            return true;
        }

        boolean access=false;
        User user = identityAPI.getUser( userId );

        if ( (activityInstance!=null) && (activityInstance instanceof HumanTaskInstance))
        {
            if ( (activityInstance.assigneeId > 0) && (activityInstance.assigneeId == userId)) {
                return true;
            }
            // still possible if user is a candidate
            SearchOptionsBuilder builder = new SearchOptionsBuilder(0, 1);
            builder.filter(UserSearchDescriptor.USER_NAME, user.getUserName() );
            SearchResult searchResult = processAPI.searchUsersWhoCanExecutePendingHumanTask( activityInstance.getId(), builder.done())
            if (searchResult.getCount() == 1) {
                return true;
            }
            // sorry, you are not involved in this task
            return false;
        }

        if (processInstance !=null)
        {
            boolean isInvolved = processAPI.isInvolvedInProcessInstance(userId, processInstance.getId())
            return isInvolved;
        }
        if (archivedProcessInstance !=null)
        {
            boolean isInvolved = processAPI.isInvolvedInProcessInstance(userId, archivedProcessInstance.getId())
            return isInvolved;

        }

        // should manage the archived process instance
        // shou
        if (processDefinitionId!=null)
        {
            SearchOptionsBuilder searchOptionBuilder = new SearchOptionsBuilder(0, 10);
            searchOptionBuilder.filter(UserSearchDescriptor.USER_NAME, user.getUserName());
            SearchResult<User> listUsers = processAPI.searchUsersWhoCanStartProcessDefinition(processDefinitionId, searchOptionBuilder.done());
            return  listUsers.getCount() == 1;
        }
        // no real context, return yes
        return true;
    }


    public boolean isAdministratorUser(){
        List<Profile> listProfiles=profileAPI.getProfilesForUser(   userId );
        for (Profile profile : listProfiles)
        {
            if (profile.getName().equals("Administrator"))
                return true;
        }
        return false;
    }

    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  Complete the result                                                                        */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */
    /**
     *  contextResult
     *
     * @param contextResult
     */
    public void completeResult(Map<String,Object> contextResult )
    {
        // process initialisation ?
        contextResult.put("isProcessInstanciation", Boolean.valueOf( detectTypeUrl == RestContextCaseId.TypeUrl.PROCESSINSTANCIATION ) );
        contextResult.put("isProcessOverview", Boolean.valueOf( detectTypeUrl == RestContextCaseId.TypeUrl.CASEOVERVIEW));
        contextResult.put("isTaskExecution", Boolean.valueOf( detectTypeUrl == RestContextCaseId.TypeUrl.TASKEXECUTION));


        // is this user is an administrator ?
        contextResult.put("isAdministrator", isAdministratorUser());
        contextResult.put("isCaseArchived", archivedProcessInstance !=null );

        User user =identityAPI.getUser( userId );
        contextResult.put("userid", user.getId());
        contextResult.put("username", user.getUserName());
        contextResult.put("processdefinitionid", processDefinitionId)
        contextResult.put("taskid", taskInstanceId)

        // Ok, here a special situation:
        // if  processInstanceRoot !=null, then we :
        //   use the root as caseId (because this is the caseId for the final user)
        // parentroot is given too, and the processInstance is display too because this is the one use to display the variable
        if (processInstanceRoot !=null)
        {
			if (processInstanceRoot != null)
            	contextResult.put("caseid", processInstanceRoot.getId());
			if (processInstanceParent!=null)
            	contextResult.put("caseidparent", processInstanceParent.getId());
				
            contextResult.put("caseiduse", processInstanceId);
        }
        else
            contextResult.put("caseid", processInstanceId);
        if (activityInstance!=null) {
            contextResult.put("taskname", activityInstance.getName());
            contextResult.put("isTaskArchived", false );

        }
        if (archivedActivityInstance!=null) {
            contextResult.put("taskname", archivedActivityInstance.getName());
            contextResult.put("isTaskArchived", true );
        }
    }


    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  Decode parameter                                                                             */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */

    private String parametersQueryString=null;
    Map<String,String> parametersMap=null;
    private HttpServletRequest parametersRequest =null;
    private RestContextConfiguration restConfiguration=null;

    public decodeParametersFromQueryString( String queryString )
    {
        this.parametersQueryString = queryString;
        this.parametersMap= new HashMap<String,String>();
        StringTokenizer st = new StringTokenizer( this.parametersQueryString , "&");
        while (st.hasMoreTokens())
        {
            String paramAndValue = st.nextToken();
            int posEqual = paramAndValue.indexOf("=");
            if (posEqual!=-1)
                this.parametersMap.put( paramAndValue.substring(0,posEqual),  paramAndValue.substring(posEqual+1));
        }
        this.restConfiguration = null;
        internalDecodeParameters();
    }
    public decodeParametersFromHttp(HttpServletRequest request, RestContextConfiguration configuration )
    {
        this.parametersQueryString=null;
        this.parametersMap=null;
        this.parametersRequest = request;
        this.restConfiguration = configuration;
        internalDecodeParameters();
    }

    public String getOneParameter( String paramName )
    {
        if (this.parametersRequest !=null)
            return this.parametersRequest.getParameter( paramName );
        if (this.parametersMap!=null)
            return this.parametersMap.get( paramName );
        return null;
    }

    /**
     * return a parameter as Long. if the transformation failed (a value is given, but not a Long) then the analysisString is populate
     */
    public Long getOneParameterLong( String paramName, String analysisInformation)
    {
        try
        {
            if (getOneParameter(paramName)!=null)
            {
                return  Long.valueOf( getOneParameter(paramName));
            }
        } catch(Exception e )
        {
            analysisString+= ";"+analysisInformation+ "["+getOneParameter("caseId")+"] : "+e.toString();
        };
        return null;
    }

    public String getAnalysisString()
    { return  analysisString; }
    ;

    /**
     * main part : the contextCaseid get all informations from the url
     */
    private void internalDecodeParameters()
    {

        analysisString="";
        // the URL can be call in the UI Designe with
        // ../API/extension/context?taskId={{taskId}}&processId={{processId}}
        // because the same form may be used in a PROCESS INSTANCTIATION or in a TASK.
        // Pb : in the URL, there are only a ID which contains the processDefinitionId (form Instantiation) or taskId (task)
        // So, how can we detect we are in fact in a PROCESS INSTANCIATION or in a TASK ?
        // the complete URL is in fact
        // http://localhost:8080/bonita/portal/resource/process/Aegrotat/1.22/API/extension/context?taskId=8994946062106177132&processId=89949460621
        // http://localhost:8080/bonita/portal/resource/taskInstance/Aegrotat/1.22/Review%20Medical/API/extension/context?taskId=66313&processId=66313
        // so we add a new control based on the URL.

        String url = getOneParameter("url");

        if (url !=null)
        {
            // /bonita/portal/resource/process/ExpenseNote/1.0/content/==> Create
            // /bonita/portal/resource/processInstance/ExpenseNote/1.0/content/==> Overview
            // /bonita/portal/resource/taskInstance/ExpenseNote/1.0/Modify/content/  ==> Modify
            if (url.indexOf("resource/process/")!=-1)
                detectTypeUrl= TypeUrl.PROCESSINSTANCIATION;
            else if (url.indexOf("resource/processInstance/")!=-1)
                detectTypeUrl= TypeUrl.CASEOVERVIEW;
            else if (url.indexOf("resource/taskInstance/")!=-1)
                detectTypeUrl= TypeUrl.TASKEXECUTION;

            log(" URL ["+url+"] isProcessInstanciation="+(detectTypeUrl == TypeUrl.PROCESSINSTANCIATION)+", isProcessOverview="+(detectTypeUrl == TypeUrl.CASEOVERVIEW)+", isTaskExecution="+(detectTypeUrl == TypeUrl.TASKEXECUTION));
        }


        //----------------------------  decode each parameter
        Long taskInstanceIdParam=null;
        Long caseInstanceIdParam=null;
        Long processDefinitionIdParam = null;
        try
        {
            if (getOneParameter("log")!=null)
            {
                isLogFromParameter = Boolean.valueOf( getOneParameter("log"));
                isLog = isLogFromParameter;
            }
        }
        catch(Exception e) {
            analysisString+= "a Boolean is expected for the parameters log ["+getOneParameter("log")+"]";
        }
        if (getOneParameter("scope")!=null)
        {
            if (ScopeSearch.ROOT.toString().equals(  getOneParameter("scope")))
                scopeSearch=ScopeSearch.ROOT;
            if (ScopeSearch.DEFAULT.toString().equals(  getOneParameter("scope")))
                scopeSearch=ScopeSearch.DEFAULT;
        }
        if  ( (detectTypeUrl == TypeUrl.TASKEXECUTION) || (detectTypeUrl == TypeUrl.UNKNOW))
            taskInstanceIdParam = getOneParameterLong("taskId", "Error with taskId");

        if  ( (detectTypeUrl == TypeUrl.CASEOVERVIEW) || (detectTypeUrl == TypeUrl.UNKNOW))
            caseInstanceIdParam = getOneParameterLong("caseId", "Error with caseId");

        contentStorageId = getOneParameterLong( "contentStorageId", "Error with contentStorageId");
        if (contentStorageId!=null)
        {
            initializeFromContentStorageId( contentStorageId );
            analysisString +=";docId["+documentId+"] processInstanceId["+processInstanceId+"]";
            caseInstanceIdParam= (document==null ? null : document.getProcessInstanceId());
        }

        if  ( (detectTypeUrl == TypeUrl.PROCESSINSTANCIATION) || (detectTypeUrl == TypeUrl.UNKNOW))
            processDefinitionIdParam =getOneParameterLong("processId", "Error with processId");

        // now, we get all the different parameters : read the object from Task
        // TASK : the parameter caseInstanceIdParam can be overridden
        try
        {
            if (taskInstanceIdParam!=null)
            {
                activityInstance = processAPI.getActivityInstance( taskInstanceIdParam );
                taskInstanceId =   activityInstance.getId();
                // 2 possibiluty ; the getParentContainerId display the local parent, where getRootContainerId display the root caseId
                // in  case of a subprocess, this is a main difference : variable are not the same ! Actor filter too are different
                Long parentCaseId= activityInstance.getParentContainerId();
                Long rootCaseId = activityInstance.getRootContainerId();
                if (parentCaseId != rootCaseId)
                {
                    processInstanceParent = processAPI.getProcessInstance( parentCaseId );
                    processInstanceRoot = processAPI.getProcessInstance( rootCaseId );

                }
                if (scopeSearch == ScopeSearch.DEFAULT)
                    caseInstanceIdParam = parentCaseId;
                if (scopeSearch == ScopeSearch.ROOT)
                    caseInstanceIdParam = rootCaseId;
            }
        } catch(Exception e )
        {
            // maybe normal : it's archived ?
        };
        try
        {
            if (taskInstanceIdParam!=null && taskInstanceId==null)
            {
                archivedActivityInstance = processAPI.getArchivedActivityInstance( taskInstanceIdParam );
                taskInstanceId =   archivedActivityInstance.getId();
                caseInstanceIdParam = archivedActivityInstance.getParentContainerId();
            }
        } catch(Exception e )
        {
            analysisString+="Error with taskId["+taskInstanceIdParam+"] : "+e.toString();
        };

        // -------------------------------- Case
        try
        {
            if (caseInstanceIdParam!=null)
            {
                processInstance = processAPI.getProcessInstance( caseInstanceIdParam );
                processInstanceId =   processInstance.getId();

                processDefinitionIdParam = processInstance.getProcessDefinitionId();
            }
        } catch(Exception e )
        {
            // maybe normal : it's archived ?
            analysisString+="Error with caseInstanceId["+caseInstanceIdParam+"] : "+e.toString();
        };
        try
        {
            if (caseInstanceIdParam!=null && processInstance==null)
            {
                archivedProcessInstance = processAPI.getFinalArchivedProcessInstance( caseInstanceIdParam );
                processInstanceId =   archivedProcessInstance.getId();
                processDefinitionIdParam = archivedProcessInstance.getProcessDefinitionId();
            }
        } catch(Exception e )
        {
            analysisString+="Error with caseId["+caseInstanceIdParam+"] : "+e.toString();
        };
        // root process instance ?
        if (processInstance!=null)
            try
            {
                processInstanceRoot= processAPI.getProcessInstance( processInstance.getRootProcessInstanceId() );
            } catch(Exception e )
        {
            analysisString+="No processRoot["+processInstance.getRootProcessInstanceId()+"] : "+e.toString();
        };

        // --------------------------------- processAPI
        try
        {

            if (processDefinitionIdParam!=null) {
                processDefinition = processAPI.getProcessDefinition( processDefinitionIdParam );
                processDefinitionId = processDefinition.getId();
            }

        } catch(Exception e )
        {
            analysisString+="Error with processId["+processDefinitionIdParam+"] : "+e.toString();
        };




        // ------------- detect the typeUrl

        if (processInstance == null && archivedProcessInstance==null && processDefinitionId!=null && (detectTypeUrl != TypeUrl.CASEOVERVIEW))
        {
            // logger.info(" processInstance/archived=["+processInstance+"/"+archivedProcessInstance+"] taskId["+taskInstanceId+"] isProcessInstanciation="+isProcessInstanciation+", isProcessOverview="+isProcessOverwiew+", isTaskExecution="+isTaskExecution);
            detectTypeUrl = TypeUrl.PROCESSINSTANCIATION;
        }
        if (activityInstance != null)
        {
            detectTypeUrl= TypeUrl.TASKEXECUTION;
        }


        // -------------------- control now
        if (processInstance == null && archivedProcessInstance == null && processDefinitionId==null)
        {
            analysisString+=";No case or process detected"
            return;
        }

        // now read the defautl date format
        String defaultDateFormatSt = getOneParameter("dateformat");
        if (defaultDateFormatSt== null || defaultDateFormatSt.trim().length()==0)
        {
            // thanks to KilianStein to propose this configuration way
            defaultDateFormatSt =restConfiguration==null ? null :  restConfiguration.getDefaultDateFormat();
            analysisString+="DateFormat from Configuration["+defaultDateFormatSt+"];";
        }
        if ("DATELONG".equalsIgnoreCase(defaultDateFormatSt))
        {
            defaultDateFormat= DateFormat.DATELONG;
            analysisString+="DateFormat[DATELONG];";
        }
        else if ("DATETIME".equalsIgnoreCase(defaultDateFormatSt))
        {
            defaultDateFormat= DateFormat.DATETIME;
            analysisString+="DateFormat[DATETIME];";
        }
        else if ("DATEJSON".equalsIgnoreCase(defaultDateFormatSt))
        {
            defaultDateFormat= DateFormat.DATEJSON;
            analysisString+="DateFormat[DATEJSON];";
        }
        else if (defaultDateFormatSt == null || defaultDateFormatSt.trim().length()==0)
        {
            defaultDateFormat=DateFormat.DATELONG;
            analysisString+="DateFormat_default[DATELONG]";
        }
        else
        {
            analysisString+="Unkown DateFormat DateFormat["+defaultDateFormatSt+"];";
            //  logError( rootResult, "DateFormat unknown:["+defaultDateFormatSt+"]  ("+analysisString+")");
            defaultDateFormat=DateFormat.DATELONG;

        }
        analysisString +=trace();
        log( analysisString );

    } // end decodesParameters



    /**
     * Initialize from a content storage ID
     * @param contentStorageId
     * @return
     */
    public boolean initializeFromContentStorageId( Long contentStorageId ) {
        this.contentStorageId= contentStorageId;

        this.documentId=null;

        boolean allIsOk = true;
        try {
            final SearchOptionsBuilder builder = new SearchOptionsBuilder(0, 2);
            builder.filter(DocumentsSearchDescriptor.CONTENT_STORAGE_ID , contentStorageId );

            final SearchResult<Document> searchResult = processAPI.searchDocuments( builder.done());
            if (searchResult.getCount() == 1) {
                documentId = searchResult.getResult().get(0);
                processInstanceId =document.getProcessInstanceId();
            }
        }
        catch (Exception e ) {
            // Bug known the search failed
            allIsOk=false;
        }
        if ( ! allIsOk) {
            String sqlRequest = "select DOCUMENT_MAPPING.NAME, DOCUMENT_MAPPING.PROCESSINSTANCEID, DOCUMENT.ID  from DOCUMENT_MAPPING, DOCUMENT  where DOCUMENT_MAPPING.DOCUMENTID = DOCUMENT.ID and   DOCUMENT.ID=?";
            Connection con=null;
            PreparedStatement pStmt=null;

            try {
                con = getFromDataSource("bonitaSequenceManagerDS");
                pStmt = con.prepareStatement(sqlRequest);
                pStmt.setObject(1, contentStorageId);
                ResultSet rs = pStmt.executeQuery( );
                while (rs.next()) {
                    // document
                    processInstanceId= rs.getLong( "PROCESSINSTANCEID");
                    documentId = rs.getLong("ID");
                }
                rs.close();
                allIsOk=true;
            }
            catch( Exception e) {
                allIsOk=false;
            }
            if (pStmt!=null)
                pStmt.close();
            if (con!=null)
                con.close();
        }
        if (documentId!=null) {
            document  =  processAPI.getDocument(documentId);
        }
        return allIsOk;
    }


    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  log                                                                             */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */

    public void log( String logExplanation)
    {
        if (isLog)
            logger.severe("com.bonitasoft.rest.context: "+logExplanation);
    }

    // report an error
    private void logError(Map<String,Object> rootResult, String logExplanation )
    {
        logger.severe("com.bonitasoft.rest.context: "+logExplanation);
        String error = rootResult.get("error");
        if (error!=null)
            error+=";"+logExplanation;
        else
            error = logExplanation;
        rootResult.put("error", error);
    }


    /* -------------------------------------------------------------------------------- */
    /*																					*/
    /*	getFromDataSource																		*/
    /*																					*/
    /* -------------------------------------------------------------------------------- */

    /**
     * -------------------------------------------------------
     *  getFromDataSource
     *
     * @param dataSourceSt
     * @return
     */
    private static Connection getFromDataSource(final String dataSourceSt) {
        try {
            final Context ctx = new InitialContext();
            final DataSource dataSource = (DataSource) ctx.lookup("java:comp/env/" + dataSourceSt);
            final Connection con = dataSource.getConnection();
            con.setAutoCommit(false);

            return con;
        } catch (final Exception e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            logger.severe("getFromDataSource: Error during access datasource [" + dataSourceSt + "] : " + e.toString() + " : " + exceptionDetails);

            return null;
        }
    }

}



