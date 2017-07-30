package org.bonitasoft.rest.context;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

// http://localhost:8080/bonita/API/extension/context?taskId=1320046&log=true
// http://localhost:8080/bonita/API/extension/context?caseId=62001

public class RestContextAccessorJava {
	private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextPilot");

	private String analysis;

	Long processDefinitionId;
	Long caseId;
	Long taskId;

	public RestContextAccessorJava() {
	}

	public void setContext(Long processDefinitionId, Long caseId, Long taskId) {
		this.processDefinitionId = processDefinitionId;
		this.caseId = caseId;
		this.taskId = taskId;

	}

	public Object execute(String parameterCall) {
		Object result = null;
		try {
			analysis = "";
			String className = "";
			String methodName = "";
			String parameter = "";

			int posParenthesis = parameterCall.indexOf("(");
			if (posParenthesis == -1) {
				analysis += ",format is package.classname.method(parameter)";
			} else {
				String completeClassName = parameterCall.substring(0, posParenthesis);
				int posLastDot = completeClassName.lastIndexOf(".");
				if (posLastDot == -1) {
					analysis += ",format is package.classname.method(parameter)";
				} else {
					className = completeClassName.substring(0, posLastDot);
					methodName = completeClassName.substring(posLastDot + 1);
				}
				parameter = parameterCall.substring(posParenthesis + 1);
				// remove the last parenthesis
				if (parameter.length() > 0)
					parameter = parameter.substring(0, parameter.length() - 1);

			}

			parameter = parameter.replaceAll("\\{\\{processid\\}\\}", processDefinitionId == null ? "" : processDefinitionId.toString());
			parameter = parameter.replaceAll("\\{\\{caseid\\}\\}", caseId == null ? "" : caseId.toString());
			parameter = parameter.replaceAll("\\{\\{taskid\\}\\}", taskId == null ? "" : taskId.toString());

			className = "nz.ac.auckland.aegrotat.ApplicationAccessAPI";
			methodName = "loadJsonFromSql";

			if (className.length() > 0 && methodName.length() > 0) {
				analysis += ",java class[" + className + "] method[" + methodName + "] param[" + parameter + "]";

				Class callClass = Class.forName(className);
				Method[] methods = callClass.getDeclaredMethods();
				boolean isExecuted = false;
				for (Method method : methods) {
					analysis += ",[" + method.getName() + "-" + method.getTypeParameters().length;

					if (method.getName().equals(methodName)) {
						result = method.invoke(null, parameter);
						isExecuted = true;
						break;
					}
				}
				if (!isExecuted) {
					analysis += "No method found (search for a static method with a STRING parameter).";
				}
			}
		} catch (Exception e) {
			logger.severe("org.bonitasoft.rest.context: exception=" + e.toString());
			analysis += "Exception " + e.toString();
			final StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			final String exceptionDetails = sw.toString();
			analysis += "Exception " + e.toString() + " at " + exceptionDetails;
		} catch (Error er) {
			logger.severe("org.bonitasoft.rest.context: error=" + er.toString());

			analysis += "Error " + er.toString();
		}
		return result;

	}

	public String getAnalysis() {
		return analysis;
	}

	public static Map<String, Object> ping(String parameters) {
		HashMap<String, Object> pingValue = new HashMap<String, Object>();
		pingValue.put("preferredFirstName", "hello the word");
		return pingValue;
	}
}
