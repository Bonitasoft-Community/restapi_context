package org.bonitasoft.rest.context;

import java.text.SimpleDateFormat;

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;

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


    private RestContextCaseId contextCaseId;


    public RestContextTransformData(RestContextCaseId contextCaseId)
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
    protected void transform( Map<String,Object> rootResult, String completeName,  String varName, Object varValue, Map<String,Object> pilotDataMap, int depth )
    {
        if (pilotDataMap==null)
            return;

        Object pilotAction = pilotDataMap.get( varName );

        if (depth > 50)
        {
            contextCaseId.log( " transform.: Depth ["+depth+"] is too big, protect the engine");

            return ; // too deep
        }

		// syntheticTrace to be in the buffer 
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
						Object valuePilot = pilotAction.get( key );
                        boolean isAllowed = valuePilot==null ? false : contextCaseId.getPilot().checkPermissionString( key, valuePilot.toString() );
                        trace +="-pilotAction["+valuePilot+"];IsAllow ? "+isAllowed;
                        if (isAllowed)
                        {
                            subResult.put(key,  transformSingleValue( key, varValueTransformed.get( key ), pilotAction.get( key )==null ? null : pilotAction.get( key ).toString()  ));
                            trace+= "; ["+key+"]=["+subResult.get( varName )+"]";
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
                        // contextCaseId.log( trace+" intermediate" );
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
                contextCaseId.logWithPrefix( " completeValueFromData.: ","Value is not a MAP and not a LIST do nothing : "+varValue.getClass().getName());
                // action is a MAP and the value is not... not, do nothing here
            }
        } // end of varcontext as a Map
        else
        {
			
            trace +="; pilotAction["+(pilotAction==null ? null : pilotAction.toString())+"]";

            // the pilotAction is maybe a Permission string
            boolean isAllowed = pilotAction==null? false : contextCaseId.getPilot().checkPermissionString(varName, pilotAction.toString());
            trace +=";IsAllow ? "+isAllowed;
            if (isAllowed)
            {
                rootResult.put(varName, transformSingleValue( varName, varValue, pilotAction==null ? "data" : pilotAction.toString() ) );
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
        contextCaseId.logNoBuffer( trace );

    }


    /* -------------------------------------------------------------------------------- */
    /*                                                                                  */
    /*  transformValue                                                                  */
    /*                                                                                  */
    /* -------------------------------------------------------------------------------- */

    /**
     * Transform the value accord
     */
    private static SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat sdfHourAbsolute = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private Object transformSingleValue( String varName, Object data, String varAction)
    {
		// if data is null, accept : maybe the action contains a default ?
          if (varAction==null)
            varAction="";

        // varaction can contain multiple element : the access, and the transform.
        // it maybe :
        //     -  [token]*; and token maybe transform:<translator>
        //    - <translator> (ascendent compatibility
        // example :
        // "date"    "datetime"    "transform:date"   "initiator;transform:datetime";
        String translatorAction=varAction; // default, for ascendent compatibilty
		String defaultAction=null;
        StringTokenizer st = new StringTokenizer( varAction, ";");
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            if (token.startsWith(RestContextPilot.cstActionFormat))
                translatorAction = token.substring(RestContextPilot.cstActionFormat.length());
            if (token.startsWith(RestContextPilot.cstActionDefault))
                defaultAction = token.substring(RestContextPilot.cstActionDefault.length());
        }

        Date dataDate=null;
        if (data instanceof Date)
        {
            dataDate= (Date) data;
        }
		if (data==null)
		{
			data= defaultAction;
		}
        // JDK 1.8 data type
        if (data!=null && 
			(data.getClass().getName().equals("java.time.LocalDate")
				|| data.getClass().getName().equals("java.time.OffsetDateTime")
				|| data.getClass().getName().equals("java.time.LocalDateTime"))
			)
        {
            return  RestContextTransformData_18.getTimeFromJDK18( varName, data, translatorAction );
        }
	    if (dataDate!=null)
        {
            contextCaseId.logWithPrefix( "========= transformSingleValue Name ["+varName+"]", " Date["+dataDate+"] <date> varAction["+varAction+"]")
            if (RestContextPilot.cstActionFormatDate.equals(translatorAction))
                return sdfDate.format( dataDate );
            else if (RestContextPilot.cstActionFormatDateTime.equals(translatorAction))
                return sdfDateTime.format( dataDate );
            else if (RestContextPilot.cstActionFormatDateLong.equals(translatorAction))
                return dataDate.getTime();
            else if (RestContextPilot.cstActionFormatDateAbsolute.equals(translatorAction))
                return sdfHourAbsolute.format(dataDate);

            // use the default
            if (contextCaseId.isDateFormatLong() )
                return dataDate.getTime();
            if (contextCaseId.isDateFormatTime())
                return sdfDateTime.format( dataDate);
            if (contextCaseId.isDateFormatJson() )
                return sdfDate.format( dataDate);

            // default : be compatible with the UIDesigner which wait for a timestamp.
            return dataDate.getTime();

        }


        if (data!=null && data instanceof List)
        {
            contextCaseId.log( "========= transformSingleValue["+data+"] <list> varAction["+varAction+"]")
            List<Object> listTransformed= new ArrayList<Object>();
            for (Object onItem : ((List) data))
            {
                // propagate the varAction to transform the date
                listTransformed.add( transformSingleValue( varName, onItem, varAction ));
            }
            return listTransformed;
        }
        return data;
    } // end of transformSingleValue


}