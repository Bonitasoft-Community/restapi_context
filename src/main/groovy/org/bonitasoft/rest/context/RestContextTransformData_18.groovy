package org.bonitasoft.rest.context
// package org.bonitasoft.rest.context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
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
/* RestContextTransformData    for 1.8                                                                                                                                    */
/*                                                                                                                                                                  */
/*  For an Data and the pilot, calcul the result to send.         */
/*                                                                                                                                                                  */
/* ******************************************************************************** */

/* IMPORTANT : you got a COMPILATION ERROR HERE ? That's because you don't use a JDK1.8
 * so just remove this file (or rename it to a .txt) and it's will be enougth 
 */

/* -------------------------------------------------------------------------------- */
/*                                                                                  */
/*  class ContextCaseId                                                             */
/*                                                                                  */
/* -------------------------------------------------------------------------------- */

public class RestContextTransformData_18
{
    private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextTransformData_18");

    static DateTimeFormatter dtfDay = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static DateTimeFormatter dtfHour = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static DateTimeFormatter dtfHourAbsolute = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static String getTimeFromJDK18( String varName, Object data, String translatorAction )
    {
        logger.info("Transform ["+varName+"] class["+data.getClass().getName()+"] action["+translatorAction+"]");
        if (data.getClass().getName().equals("java.time.LocalDate"))
        {
            java.time.LocalDate dataLocalDate = (java.time.LocalDate) data;
            return dtfDay.format( dataLocalDate );
        }
        if (data.getClass().getName().equals("java.time.OffsetDateTime"))
        {
            java.time.OffsetDateTime dataLocalDate = (java.time.OffsetDateTime) data;

            if (RestContextPilot.cstActionFormatDateAbsolute.equals(translatorAction))
                return dtfHourAbsolute.format(dataLocalDate);
            return dtfHour.format(dataLocalDate);
        }
        if (data.getClass().getName().equals("java.time.LocalDateTime"))
        {
            java.time.LocalDateTime dataLocalDate = (java.time.LocalDateTime) data;
            if (RestContextPilot.cstActionFormatDateAbsolute.equals(translatorAction))
                return dtfHourAbsolute.format(dataLocalDate);
            return dtfHour.format(dataLocalDate);
        }
        return null;
    }
}