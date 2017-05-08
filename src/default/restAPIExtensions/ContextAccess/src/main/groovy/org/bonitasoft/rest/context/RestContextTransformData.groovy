package org.bonitasoft.rest.context
// package org.bonitasoft.rest.context;

import java.sql.Connection;
import java.sql.ResultSet;

import java.sql.Statement;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;

import java.util.logging.Logger
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest


import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper


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




/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* RestContextTransformData                                                                                                                                        */
/*                                                                                                                                                                  */
/*  For an Data and the pilot, calcul the result to send.         */
/*                                                                                                                                                                  */
/* ******************************************************************************** */


/* -------------------------------------------------------------------------------- */
/*                                                                                  */
/*  class ContextCaseId                                                             */
/*                                                                                  */
/* -------------------------------------------------------------------------------- */
// a Case has multiple access : a ProcessInstance or a ArchiveProcessInstance, a TaskInstance or an ArchiveTaskInstance...
public class RestContextTransformData {

    private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextTransformData");

    private RestContextCaseId contextCaseId;


    public RestContextTransformData( RestContextCaseId contextCaseId)
    {
        this.contextCaseId = contextCaseId;
    }

    /**
     * transform the data
     * @param rootResult
     * @param varName
     * @param varValue
     * @param pilotDataMap
     */
    private  void transform( Map<String,Object> rootResult, String completeName,  String varName, Object varValue, Map<String,Object> pilotDataMap, int depth )
    {
        if (pilotDataMap==null)
            return;

        Object pilotAction = pilotDataMap.get( varName );

        if (depth > 50)
        {
            contextCaseId.log( " transform.: Depth ["+depth+"] is too big, protect the engine");

            return ; // too deep
        }

        String trace="                                                        ".substring(0,depth);

        trace += " transform. variable["+varName+"] pilotDataMap["+pilotDataMap.toString()+"]";


        if (pilotAction instanceof Map)
        {
            trace +="; pilotActionMap["+pilotAction.toString()+"]";
            // attention, we may have at this point an object : it's time to transform it in MAP, LIST, ...
            Object varValueTransformed = varValue;
            if (varValueTransformed!= null)
                if (! ( ( varValueTransformed instanceof Map) || (varValueTransformed instanceof List)))
            {
                trace += "; obj("+varValueTransformed.getClass().getName()+") =>MAP ";
                String jsonSt = new JsonBuilder( varValueTransformed ).toPrettyString();
                varValueTransformed = new JsonSlurper().parseText(jsonSt);
            }


            // we have a Map like { Attribut1: xxx}
            if (varValueTransformed instanceof Map)
            {
                trace +=";Data=MAP";
                Map<String,Object> subResult = new HashMap<String,Object>();
                rootResult.put(varName, subResult);
                for (String key : pilotAction.keySet())
                {
                    trace +=";Recursive on pilotKey["+key+"]";
                    //  contextCaseId.log( trace );
                    if (pilotAction.get( key ) instanceof Map)
                    {
                        //  contextCaseId.log( trace+" intermediate" );
                        transform( subResult, completeName+"."+key, key, varValueTransformed.get( key ),   pilotAction, depth+1 );
                        trace+= "; subResult="+subResult.toString();
                    }
                    else
                    {
                        boolean isAllowed = contextCaseId.getPilot().checkPermissionString(varName, pilotAction.get( key ) );
                        trace +=";IsAllow ? "+isAllowed;
                        if (isAllowed)
                        {
                            subResult.put(key,  transformSingleValue( varValueTransformed.get( key ), pilotAction.get( key )==null ? null : pilotAction.get( key ).toString()  ));
                            trace+= "; ["+varName+"]=["+subResult.get( varName )+"]";
                        }
                    }
                }
            }
            else if (varValueTransformed instanceof List)
            {
                trace+=";Data=LIST";
                // Ok, apply the action on each element of the list
                List<Map<String,Object>> subResult = new ArrayList<Map<String,Object>>();
                rootResult.put(varName, subResult);
                for (int i=0; i< ((List) varValueTransformed).size();i++)
                {
                    Map<String,Object> subResultIterator = new HashMap<String,Object>();
                    subResult.add(subResultIterator);
                    for (String key : pilotAction.keySet())
                    {
                        contextCaseId.log( trace+" intermediate" );
                        transform( subResultIterator, completeName+"."+key, key, ((List) varValueTransformed).getAt( i ).get( key ), pilotAction, depth+1 );
                    }
                    trace+= "; subResult="+subResult.toString();

                }

            }
            else if (varValue == null)
            {
                trace+= ";DATA=var ["+varName+"]=null";
                rootResult.put(varName,null);
            }
            else
            {
                trace+= ";DATA unknow ["+varValue.getClass().getName()+"]";
                contextCaseId.log( " completeValueFromData.: Value is not a MAP and not a LIST do nothing : "+varValue.getClass().getName());
                // action is a MAP and the value is not... not, do nothing here
            }
        } // end of varcontext as a Map
        else
        {
            trace +="; pilotActionString["+pilotAction.toString()+"]";

            // the pilotAction is maybe a Permission string
            boolean isAllowed = contextCaseId.getPilot().checkPermissionString(varName, pilotAction);
            trace +=";IsAllow ? "+isAllowed;
            if (isAllowed)
            {
                rootResult.put(varName, transformSingleValue( varValue, pilotAction==null ? "data" : pilotAction.toString() ) );
                trace+= "; ["+varName+"]=["+rootResult.get( varName )+"]";
            }
        }
        // special case for an enum
        if (varValue instanceof Enum)
        {
            trace+= ";ENUM Detected";

            List<String> listOptions = new ArrayList<String>();
            // get the enummeration
            Object[] options = ((Enum) varValue).values();
            for ( Object oneOption : options)
            {
                listOptions.add( oneOption.toString() );
            }
            rootResult.put(varName+"_list", listOptions );
            trace+= " add["+varName+"_list]=  ["+rootResult.get(varName+"_list")+"]";

        }
        contextCaseId.log( trace);

    }


    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  transformValue                                                                  */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */

    /**
     * Transform the value accord
     */
    private static SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    private Object transformSingleValue( Object data, String varAction)
    {
        if (data==null)
            return null;

        // varaction can contain multiple element : the access, and the transform.
        // it maybe :
        //     -  [token]*; and token maybe transform:<translator>
        //    - <translator> (ascendent compatibility
        // example :
        // "date"    "datetime"    "transform:date"   "initiator;transform:datetime";
        String translatorAction=varAction; // default, for ascendent compatibilty
        StringTokenizer st = new StringTokenizer( varAction, ";");
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            if (token.startsWith("format:"))
                translatorAction = token.substring("format:".length()+1);
        }

        if (data instanceof Date)
        {
            logger.info("========= transformValue["+data+"] <date> varAction["+varAction+"]")
            if ("date".equals(translatorAction))
                return sdfDate.format( (Date) data);
            else if ("datetime".equals(translatorAction))
                return sdfDateTime.format( (Date) data);
            else if ("datelong".equals(translatorAction))
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
            logger.info("========= transformValue["+data+"] <list> varAction["+varAction+"]")
            List<Object> listTransformed= new ArrayList<Object>();
            for (Object onItem : ((List) data))
            {
                // propagate the varAction to transform the date
                listTransformed.add( transformValue( onItem, varAction, contextCaseId ));
            }
            return listTransformed;
        }
        return data;
    } // end of transformSingleValue


}