// package org.bonitasoft.rest.context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;

import java.util.logging.Logger
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest


import org.bonitasoft.engine.api.IdentityAPI
import org.bonitasoft.engine.api.ProcessAPI
import org.bonitasoft.engine.api.ProfileAPI
import org.bonitasoft.engine.bpm.document.Document
import org.bonitasoft.engine.bpm.flownode.ActivityInstance
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance
import org.bonitasoft.engine.bpm.process.ProcessInstance
import org.bonitasoft.engine.identity.User
import org.bonitasoft.engine.identity.UserSearchDescriptor
import org.bonitasoft.engine.bpm.document.DocumentsSearchDescriptor
import org.bonitasoft.engine.profile.Profile
import org.bonitasoft.engine.search.SearchOptionsBuilder
import org.bonitasoft.engine.search.SearchResult


import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;


/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* RestContextCaseId                                                                                                                                        */
/*                                                                                                                                                                  */
/*  retrieve all information about the caseId, or from a taskId, a processDefinitionId or a ContentStorageId                                          */
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
    public ProcessInstance processInstance=null;
    public ActivityInstance activityInstance = null;
    public ArchivedProcessInstance archivedProcessInstance = null;
    public ArchivedActivityInstance archivedActivityInstance=null;



    /**
     * it's one of this possibility
     */
    public enum TypeUrl {
        PROCESSINSTANCIATION, CASEOVERVIEW, TASKEXECUTION, UNKNOW
    };

    TypeUrl detectTypeUrl = TypeUrl.UNKNOW;

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
        if ( processInstance!=null)
            return processInstance.getProcessDefinitionId();
        if (archivedProcessInstance!=null)
            return archivedProcessInstance.getProcessDefinitionId();
        return processDefinitionId;
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

    public void setPilot( RestContextPilot pilotParam )
    {
        pilot = pilotParam;
    }

    public RestContextPilot getPilot()
    {
        return pilot;

    }

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


    private String parametersQueryString=null;
    Map<String,String> parametersMap=null;
    private HttpServletRequest pametersRequest =null;
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
        this.pametersRequest = null;
        this.restConfiguration = null;
        internalDecodeParameters();
    }
    public decodeParametersFromHttp(HttpServletRequest request,  RestContextConfiguration configuration )
    {
        this.parametersQueryString=null;
        this.parametersMap=null;
        this.pametersRequest = request;
        this.restConfiguration = configuration;
        internalDecodeParameters();
    }

    public String getOneParameter( String paramName )
    {
        if (this.pametersRequest !=null)
            return  request.getParameter( paramName );
        if (this.parametersMap!=null)
            return this.parametersMap.get( paramName );
        return null;
    }

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

            logger.info(" URL ["+url+"] isProcessInstanciation="+(detectTypeUrl == TypeUrl.PROCESSINSTANCIATION)+", isProcessOverview="+(detectTypeUrl == TypeUrl.CASEOVERVIEW)+", isTaskExecution="+(detectTypeUrl == TypeUrl.TASKEXECUTION));
        }


        //----------------------------  decode each parameters
        try
        {
            // attention, if we suppose this is a FormInstantiation task, we don't try to get the taskId
            if (getOneParameter("taskId")!=null &&  detectTypeUrl != TypeUrl.PROCESSINSTANCIATION)
                taskInstanceId = Long.valueOf( getOneParameter("taskId"));
        } catch(Exception e )
        {
            analysisString+="Error with taskId["+getOneParameter("taskId")+"] : "+e.toString();
        };

        try
        {
            if (getOneParameter("processId")!=null)
                processDefinitionId = Long.valueOf( getOneParameter("processId"));
        } catch(Exception e )
        {
            analysisString+="Error with processId["+getOneParameter("processId")+"] : "+e.toString();
        };


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

        // let's go

        //----------------- get the perimeter (taskid or processInstanceId or ProcessId)
        try
        {
            if (taskInstanceId!=null)
            {
                ActivityInstance activityInstance = processAPI.getActivityInstance( taskInstanceId);
                processInstanceId = activityInstance.getParentContainerId();
            }
        } catch(Exception e )
        {
            logger.info(" taskInstanceId=["+taskInstanceId+"] Exception ["+e.toString()+"]");

            // Actually, if the user give a ProcessId that's mean that we are in the task instantiation. Because with the UI Designer, there are no way
            // to give a taskId=xxx&processId=xxx : the only parameter in the url is "id" !
            if (processDefinitionId !=null)
            {
                taskInstanceId=null;
                processInstanceId=null;
            }
            else
                analysisString+="Error with taskId["+getOneParameter("taskId")+"] : "+e.toString();
        };



        // may be updated by the taskId
        if (processInstanceId == null) {
            try
            {
                if (getOneParameter("caseId")!=null && detectTypeUrl != TypeUrl.PROCESSINSTANCIATION)
                    processInstanceId = Long.valueOf( getOneParameter("caseId"));
            } catch(Exception e )
            // no worry if an exception arrived here : that's mean the user doesn't give a taskId, error is manage after (no taskid+noCaseId=error)
            {
                analysisString+="Error with caseId["+getOneParameter("caseId")+"] : "+e.toString();

            };
        }


        // get the contentStorageId
        if (getOneParameter("contentStorageId") != null)
        {
            analysisString +=";contentStorageId["+getOneParameter("contentStorageId")+"]";
            try
            {
                contentStorageId= Long.valueOf( getOneParameter("contentStorageId"));
                initializeFromContentStorageId( contentStorageId );
                analysisString +=";docId["+documentId+"] processInstanceId["+processInstanceId+"]";
            }
            catch (Exception e)
            {
                analysisString+="Error with search Document from contentStorageId  ["+contentStorageId+"] : "+e.toString();
            }
        }

        if (processInstanceId == null && processDefinitionId==null)
        {
            // logError( rootResult, "Parameter [taskId] or [caseId] or [processId] required ("+analysisString+")");
            return;
        }

        if (processInstance == null && archivedProcessInstance==null && processDefinitionId!=null && (detectTypeUrl != TypeUrl.CASEOVERVIEW))
        {
            // logger.info(" processInstance/archived=["+processInstance+"/"+archivedProcessInstance+"] taskId["+taskInstanceId+"] isProcessInstanciation="+isProcessInstanciation+", isProcessOverview="+isProcessOverwiew+", isTaskExecution="+isTaskExecution);
            detectTypeUrl = TypeUrl.PROCESSINSTANCIATION;
        }
        if (activityInstance != null)
        {
            detectTypeUrl= TypeUrl.TASKEXECUTION;
        }


        try
        {
            processInstance = processAPI.getProcessInstance( processInstanceId);
            if (taskInstanceId !=null)
                activityInstance = processAPI.getActivityInstance( taskInstanceId);
        }
        catch(Exception e)
        {
            // no worry if an exception arrived here : that's mean it's maybe an Archive InstanceId
            // logRest( isLog, "No processinstance found by ["+ processInstanceId+"] : "+e.toString() );
            // analysisString+="Error getting Object processInstance and ActivityInstance from processId["+processInstanceId+"] activityId["+taskInstanceId+"] : "+e.toString();
        }
        // same with archived... yes, in Bonita, class are different...
        try
        {
            if (processInstance ==null)
                archivedProcessInstance = processAPI.getFinalArchivedProcessInstance( processInstanceId );
            if (taskInstanceId !=null && activityInstance == null)
                archivedActivityInstance = processAPI.getArchivedActivityInstance( taskInstanceId );
        }
        catch(Exception e)
        {
            // no worry if an exception arrived here : that's mean it's maybe an InstanceId : so error will be managed after
            // logRest( isLog, "No ArchivedProcessinstance found by ["+processInstanceId+"] : "+e.toString() );
        }
        if (processInstance == null && archivedProcessInstance == null && processDefinitionId==null)
        {
            //  logError(rootResult, "caseId unknown");
            return;
        }
        if (taskInstanceId != null && activityInstance==null && archivedActivityInstance==null)
        {
            //  logError(rootResult, "taskId unknown");
            return;
        }


        // now read the defautl date format
        String defaultDateFormatSt = getOneParameter("dateformat");
        if (defaultDateFormatSt== null || defaultDateFormatSt.trim().length==0)
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
        logRest( analysisString );

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
            document  =     processAPI.getDocument(documentId);
        }
        return allIsOk;
    }


    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  logRest                                                                         */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */

    public void logRest( String logExplanation)
    {
        if (isLog)
            logger.severe("com.bonitasoft.rest.context: "+logExplanation);
    }

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



