package org.bonitasoft.rest.context;


import java.util.logging.Logger
import java.util.Collections;

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





/* -------------------------------------------------------------------------------- */
/*                                                                                  */
/*  class PerformanceTrace                                                          */
/*                                                                                  */
/* -------------------------------------------------------------------------------- */
class RestContextTrackPerformance {


    private static Logger logger = Logger.getLogger(RestContextTrackPerformance.class.getName());

    private List<Map<String,Object>> listOperations = new ArrayList<Map<String,Object>>();
    private List<Map<String,Object>> listSubOperations = new ArrayList<Map<String,Object>>();
    long totalTime=0;
    public void addMarker(String operation) {
        long currentTime= System.currentTimeMillis();
        Map<String,Object> oneOperation = new HashMap<String,Object>();
        oneOperation.put("t",System.currentTimeMillis());
        oneOperation.put("n",operation);
        listOperations.add( oneOperation );
    }


    /**
     *
     * @param subOperation
     * @param timeSubOperation
     */
    public void addSubOperation(String subOperationName, long timeSubOperation) {
        Map<String,Object> oneOperation = new HashMap<String,Object>();
        oneOperation.put("t",timeSubOperation);
        oneOperation.put("n",subOperationName);
        listSubOperations.add( oneOperation );
    }

    /**
     * start the subPperation. Return an object, which has to be given a the endSubOperation
     * @param subOperationName
     * @return
     */
    public Map<String,Object> startSubOperation(String subOperationName) {
        Map<String,Object> oneOperation = new HashMap<String,Object>();
        long currentTime= System.currentTimeMillis();
        oneOperation.put("s",currentTime);
        oneOperation.put("n",subOperationName);
        return oneOperation;
    }
    /**
     * end the siboperation. Calculate the time and register it
     * @param subOperation
     */
    public void  endSubOperation(Map<String,Object> subOperation) {
        long currentTime= System.currentTimeMillis();
        long startTime = (Long) subOperation.get( "s");

        subOperation.put("t",currentTime - startTime );
        listSubOperations.add( subOperation );
    }




    public String trace() {
        String result="";


        Collections.sort(listSubOperations, new Comparator<Map<String,Object>>() {
                    public int compare(Map<String,Object> s1,
                            Map<String,Object> s2) {
                        Long t1= Long.valueOf( s1.get("t") );
                        Long t2= Long.valueOf( s2.get("t") );
                        // in first the BIGGER time
                        return t2.compareTo( t1 );
                    }
                });

        result+=" SUB_OPERATION:";
        // first sub operation
        for (int i=1;i<listSubOperations.size();i++) {
            result+= listSubOperations.get( i ).get("n")+":"+listSubOperations.get( i ).get("t")+" ms,";
        }

        result+="### MAIN_MARKER:";
        // then MAIN
        for (int i=1;i<listOperations.size();i++) {
            String time = ((long) listOperations.get( i ).get("t")) - ((long)listOperations.get( i-1 ).get("t"));
            result+= listOperations.get( i ).get("n")+":"+time+" ms,";
        }
        totalTime = ((long) listOperations.get( listOperations.size()-1 ).get("t")) - ((long)listOperations.get( 0 ).get("t"));
        result+="Total "+totalTime+" ms";

        return result;
    }
    public long getTotalTime() {
        return totalTime;
    }
}
