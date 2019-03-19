package org.bonitasoft.rest.context

import java.lang.reflect.Method;
import java.io.PrintWriter;
import java.io.StringWriter;


import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;



/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* RestContextExpliciteVariable                                                                                                                                    */
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

class RestContextExpliciteVariable {
	  private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextPilot");

 	 RestContextPilot pilot;
	 
	  public RestContextExpliciteVariable( RestContextPilot pilot)
	  {
		  this.pilot = pilot;
		  
	  }
	 /* ********************************************************************************* */
    /*                                                                                                                                                                   */
    /* ExplicitVariable                                                                                                                                                      */
    /*                                                                                                                                                                  */
    /*                                                                                                                                                                  */
    /* ******************************************************************************** */
	/**
	 *     An explicit Variable is a variable where the action contains "explicit:<>  
	 * @param varName
	 * @return
	 */
	public boolean isExplicitVariable( String varName )
	{
		if (getExplicitVariable( varName )!=null)
			return true;
		return false;
	}

	public String getExplicitVariable(String varName )
	{
		if (pilot.getPilotExecutionContext()==null)
			return null;
		
		RestContextCaseId contextCaseId = pilot.getContextCaseId();

		Object variableValue=null;
		

		if ( contextCaseId.isProcessInstanciation() )
			variableValue = pilot.getExecutionContextValueInContainer( RestContextPilot.cstPilotExplicitVariableInstanciation, varName);
		else if ( contextCaseId.isProcessOverview() )
			variableValue = pilot.getExecutionContextValueInContainer( RestContextPilot.cstPilotExplicitVariableOverview, varName);
		else if ( contextCaseId.isTaskExecution() )
			variableValue = pilot.getExecutionContextValueInContainer( RestContextPilot.cstPilotExplicitVariableTask, varName);
		else if ( contextCaseId.isCaseArchived() )
			variableValue = pilot.getExecutionContextValueInContainer( RestContextPilot.cstPilotExplicitVariableCaseArchive, varName);
		else if ( contextCaseId.isCaseAccess() )
			variableValue = pilot.getExecutionContextValueInContainer( RestContextPilot.cstPilotExplicitVariableCaseAccess, varName);
		
		if (variableValue==null)
			variableValue = pilot.getExecutionContextValueInContainer( RestContextPilot.cstPilotExplicitVariableDefault, varName);

		return variableValue;
	}
	
	public final static String cstCallJson = "json:";
	public final static String cstCallJava = "java:";

	/**
	 * get the value
	 */
	public Object getValue( Map<String,Object> rootResult, String varName, RestContextCaseId contextCaseId)
	{
		String analysis="";
		Object result=null;
		try
		{
			String valueSt=getExplicitVariable(varName );
			if (valueSt==null)
				return null;
			analysis+="expliciteVariable["+varName+"] by["+valueSt+"]";
			if (valueSt.startsWith(cstCallJson))
			{
				valueSt = valueSt.substring( cstCallJson.length());
				analysis+",Json";
		        final JSONParser jsonParser = new JSONParser();
		        final JSONObject valueData = (JSONObject) jsonParser.parse(valueSt);
		        result = valueData;;
			}
			/*
			if (valueSt.startsWith( cstCallJava))
			{
				// java:nz.ac.auckland.aegrotat.ApplicationAPI.loadJsonFromSql({{caseid}}#NotManagedBizDataDS)
				valueSt = valueSt.substring( cstCallJava.length() );
				
				RestContextAccessorJava restContextAccessorJava = new RestContextAccessorJava();
				restContextAccessorJava.setContext(contextCaseId.getProcessDefinitionId(),contextCaseId.getCaseId(),contextCaseId.getTaskId());
				result=restContextAccessorJava.execute( valueSt );
				contextCaseId.logWithPrefix("RestContextExpliciteVariable.getValue : ", restContextAccessorJava.getAnalysis());
			}
			*/
		}
		catch(Exception e)
		{
			analysis+="Exception "+e.toString();
    		final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            contextCaseId.logError(rootResult, "Error during get ExpliciteVariable: "+e.toString()+" at "+exceptionDetails);
		}
		catch(Error er)
		{
			analysis+="Exception "+e.toString();
    		final StringWriter sw = new StringWriter();
            er.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            contextCaseId.logError(rootResult, "Error during get ExpliciteVariable: "+er.toString()+" at "+exceptionDetails);
		}
		contextCaseId.logWithPrefix("RestContextExpliciteVaraible.getValue : ", analysis);
        return result;
		
	}
}
