import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.Runtime;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;
import org.codehaus.groovy.tools.shell.CommandAlias;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;


import javax.naming.Context;
import javax.naming.InitialContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;

import org.apache.commons.lang3.StringEscapeUtils

import org.bonitasoft.engine.identity.User;
import org.bonitasoft.console.common.server.page.PageContext
import org.bonitasoft.console.common.server.page.PageController
import org.bonitasoft.console.common.server.page.PageResourceProvider
import org.bonitasoft.engine.exception.AlreadyExistsException;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.CreationException;
import org.bonitasoft.engine.exception.DeletionException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;

import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.api.CommandAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.IdentityAPI;


import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.engine.bpm.flownode.ActivityInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedFlowNodeInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.search.SearchOptions;
import org.bonitasoft.engine.search.SearchResult;

import org.bonitasoft.engine.command.CommandDescriptor;
import org.bonitasoft.engine.command.CommandCriterion;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;

import org.bonitasoft.engine.identity.UserSearchDescriptor;
import org.bonitasoft.engine.search.Order;


import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.log.event.BEventFactory;

import org.bonitasoft.ext.properties.BonitaProperties;
	
import com.bonitasoft.users.UsersOperation;


 
public class Index implements PageController {

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response, PageResourceProvider pageResourceProvider, PageContext pageContext) {
	
		Logger logger= Logger.getLogger("org.bonitasoft");
		
		
		try {
			def String indexContent;
			pageResourceProvider.getResourceAsStream("Index.groovy").withStream { InputStream s-> indexContent = s.getText() };
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter()

			String action=request.getParameter("action");
			 
			String jsonParamEncode = request.getParameter("jsonparam");
            String jsonParamSt = (jsonParamEncode==null ? null : java.net.URLDecoder.decode(jsonParamEncode, "UTF-8"));
            Object jsonParam = (jsonParamSt==null ? null : JSONValue.parse(jsonParamSt));
           
			
			logger.info("###################################### action is["+action+"] 2.0!");
			if (action==null || action.length()==0 )
			{
				// logger.info("RUN Default !");
				
				runTheBonitaIndexDoGet( request, response,pageResourceProvider,pageContext);
				return;
			}
			
			APISession session = pageContext.getApiSession()
			ProcessAPI processAPI = TenantAPIAccessor.getProcessAPI(session);

			IdentityAPI identityAPI = TenantAPIAccessor.getIdentityAPI(session);
			
			HashMap<String,Object> answer = null;
			List<BEvent> listEvents = new ArrayList<BEvent>();
			
			if ("ping".equals(action))
			{
				answer = new HashMap<String,Object>();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				answer.put("pingcurrentdate", sdf.format( new Date() ) );
				answer.put("pingserverinfo", "server is up 2.1");
				
				List<ActivityTimeLine> listActivities = new  ArrayList<ActivityTimeLine>();
				listActivities.add( ActivityTimeLine.getActivityTimeLine("Choose beverage", 9, 11));
				listActivities.add( ActivityTimeLine.getActivityTimeLine("Place Tea bag", 11, 12));
				listActivities.add( ActivityTimeLine.getActivityTimeLine("Fill Hot Water", 12, 14));
				listActivities.add( ActivityTimeLine.getActivityTimeLine("Insert Capsule", 11, 13));
				listActivities.add( ActivityTimeLine.getActivityTimeLine("Inject hot water", 13, 14));
				listActivities.add( ActivityTimeLine.getActivityTimeLine("Have fun with Bonita", 14, 17));
				
				answer.put("chartObject", getChartTimeLine("Chart Example", listActivities));
				
				
				// list of process
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
			
				SearchOptionsBuilder searchOptionsBuilder = new SearchOptionsBuilder(0,100);
				SearchResult<ProcessDeploymentInfo> searchResult = processAPI.searchProcessDeploymentInfos( searchOptionsBuilder.done());
				ArrayList<HashMap<String,Object>> listProcesses = new ArrayList<HashMap<String,Object>>();
				for (ProcessDeploymentInfo processDeployment : searchResult.getResult())
				{
					HashMap<String,Object> processMap = new HashMap<String,Object>();
					processMap.put("name", processDeployment.getName() );
					processMap.put("version", processDeployment.getVersion() );
					processMap.put("state", processDeployment.getConfigurationState().toString() );
					processMap.put("deployeddate", simpleDateFormat.format( processDeployment.getDeploymentDate() ) );
					listProcesses.add( processMap);
				}
				answer.put("listprocesses", listProcesses);
	
                final UsersOperation userOperations = new UsersOperation();
                final List<Map<String, Object>> listMapUsers = userOperations.getUsersList(identityAPI);
                answer.put("listusers", listMapUsers);
                
				
				listEvents.add( new BEvent("com.bonitasoft.ping", 1, Level.INFO, listMapUsers.size()+" Users found", "Number of users found in the system"));
				listEvents.add( new BEvent("com.bonitasoft.ping", 1, Level.APPLICATIONERROR, "Fake error", "This is not a real error", "No consequence", "don't call anybody"));
				
				
						
			}
			else if ("queryusers".equals(action))
            {
                answer = new HashMap<String,Object>();
				
				List listUsers = new ArrayList();
				final SearchOptionsBuilder searchOptionBuilder = new SearchOptionsBuilder(0, 100000);
           		// http://documentation.bonitasoft.com/?page=using-list-and-search-methods
            	searchOptionBuilder.filter(UserSearchDescriptor.ENABLED, Boolean.TRUE);
            	searchOptionBuilder.searchTerm( jsonParam.get("userfilter") );

            	searchOptionBuilder.sort(UserSearchDescriptor.LAST_NAME, Order.ASC);
            	searchOptionBuilder.sort(UserSearchDescriptor.FIRST_NAME, Order.ASC);
            	final SearchResult<User> searchResult = identityAPI.searchUsers(searchOptionBuilder.done());
            	for (final User user : searchResult.getResult())
            	{
                	final Map<String, Object> oneRecord = new HashMap<String, Object>();
                // oneRecord.put("display", user.getFirstName()+" " + user.getLastName()  + " (" + user.getUserName() + ")");
	                oneRecord.put("display", user.getLastName() + "," + user.getFirstName() + " (" + user.getUserName() + ")");
    	            oneRecord.put("id", user.getId());
    	            listUsers.add( oneRecord );
    	        }
                answer.put("listUsers", listUsers);

            }	
			else if ("saveprops".equals(action))	{
				
				final HashMap<String, Object>  jsonHash=null;
				
				if (jsonparam != null && jsonparam.length() > 0 ) {
					jsonHash = (HashMap<String, Object>) JSONValue.parse( jsonparam );
				}
				answer = new HashMap<String,Object>();
				if (jsonHash!=null)
				{
					try
					{
						BonitaProperties bonitaProperties = new BonitaProperties( pageResourceProvider );

						listEvents.addAll( bonitaProperties.load() );
						bonitaProperties.setProperty( session.getUserId()+"_firstname", jsonHash.get("firstname") );
						listEvents.addAll(  bonitaProperties.store());
					}
					catch( Exception e )
					{
						logger.severe("Exception "+e.toString());
						listEvents.add( new BEvent("com.bonitasoft.ping", 10, Level.APPLICATIONERROR, "Error using BonitaProperties", "Error :"+e.toString(), "Properties is not saved", "Check exception"));
					}
				}
				else
					listEvents.add( new BEvent("com.bonitasoft.ping", 11, Level.APPLICATIONERROR, "JsonHash can't be decode", "the parameters in Json can't be decode", "Properties is not saved", "Check page"));

			}
			if ("loadprops".equals(action)) {
				answer = new HashMap<String,Object>();
				try
				{
					logger.info("Load properties");

					BonitaProperties bonitaProperties = new BonitaProperties( pageResourceProvider );
					listEvents.addAll( bonitaProperties.load() );
					logger.info("Load done, events = "+listEvents.size() );

					String firstName = bonitaProperties.getProperty( session.getUserId()+"_firstname" );
					logger.info("Load done, firstName["+firstName+"]" );
					answer.put("firstname", (firstName==null ? "" : firstName) );
		
				}
				catch( Exception e )
				{
					logger.severe("Exception "+e.toString());
					listEvents.add( new BEvent("com.bonitasoft.ping", 10, Level.APPLICATIONERROR, "Error using BonitaProperties", "Error :"+e.toString(), "Properties is not saved", "Check exception"));

				}

			}
			
			
			// save the result
			if (answer!=null)
			{
				answer.put("listevents", BEventFactory.getHtml(listEvents) );

				String jsonDetailsSt = JSONValue.toJSONString( answer );
	   
				out.write( jsonDetailsSt );
				out.flush();
				out.close();				
				return;		
			}
			out.write( "Unknow command" );
			out.flush();
			out.close();
			return;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionDetails = sw.toString();
			logger.severe("Exception ["+e.toString()+"] at "+exceptionDetails);
		}
	}

	
	/** -------------------------------------------------------------------------
	 *
	 *runTheBonitaIndexDoGet
	 * 
	 */
	private void runTheBonitaIndexDoGet(HttpServletRequest request, HttpServletResponse response, PageResourceProvider pageResourceProvider, PageContext pageContext) {
				try {
						def String indexContent;
						pageResourceProvider.getResourceAsStream("index.html").withStream { InputStream s->
								indexContent = s.getText()
						}
						
						// def String pageResource="pageResource?&page="+ request.getParameter("page")+"&location=";
						// indexContent= indexContent.replace("@_USER_LOCALE_@", request.getParameter("locale"));
						// indexContent= indexContent.replace("@_PAGE_RESOURCE_@", pageResource);
						
						response.setCharacterEncoding("UTF-8");
						PrintWriter out = response.getWriter();
						out.print(indexContent);
						out.flush();
						out.close();
				} catch (Exception e) {
						e.printStackTrace();
				}
		}
		
		/**
		to create a simple chart
		*/
		public static class ActivityTimeLine
		{
				public String activityName;
				public Date dateBegin;
				public Date dateEnd;
				
				public static ActivityTimeLine getActivityTimeLine(String activityName, int timeBegin, int timeEnd)
				{
					Calendar calBegin = Calendar.getInstance();
					calBegin.set(Calendar.HOUR_OF_DAY , timeBegin);
					Calendar calEnd = Calendar.getInstance();
					calEnd.set(Calendar.HOUR_OF_DAY , timeEnd);
					
						ActivityTimeLine oneSample = new ActivityTimeLine();
						oneSample.activityName = activityName;
						oneSample.dateBegin		= calBegin.getTime();
						oneSample.dateEnd 		= calEnd.getTime();
						
						return oneSample;
				}
				public long getDateLong()
				{ return dateBegin == null ? 0 : dateBegin.getTime(); }
		}
		
		
		/** create a simple chart 
		*/
		public static String getChartTimeLine(String title, List<ActivityTimeLine> listSamples){
				Logger logger = Logger.getLogger("org.bonitasoft");
				
				/** structure 
				 * "rows": [
           {
        		 c: [
        		      { "v": "January" },"
                  { "v": 19,"f": "42 items" },
                  { "v": 12,"f": "Ony 12 items" },
                ]
           },
           {
        		 c: [
        		      { "v": "January" },"
                  { "v": 19,"f": "42 items" },
                  { "v": 12,"f": "Ony 12 items" },
                ]
           },

				 */
				String resultValue="";
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss,SSS");
				
				for (int i=0;i<listSamples.size();i++)
				{
					logger.info("sample [i] : "+listSamples.get( i ).activityName+"] dateBegin["+simpleDateFormat.format( listSamples.get( i ).dateBegin)+"] dateEnd["+simpleDateFormat.format( listSamples.get( i ).dateEnd) +"]");
						if (listSamples.get( i ).dateBegin!=null &&  listSamples.get( i ).dateEnd != null)
								resultValue+= "{ \"c\": [ { \"v\": \""+listSamples.get( i ).activityName+"\" }," ;
								resultValue+= " { \"v\": \""+listSamples.get( i ).activityName +"\" }, " ;
								resultValue+= " { \"v\": \"Date("+ simpleDateFormat.format( listSamples.get( i ).dateBegin) +")\" }, " ;
								resultValue+= " { \"v\": \"Date("+ simpleDateFormat.format( listSamples.get( i ).dateEnd) +")\" } " ;
								resultValue+= "] },";
				}
				if (resultValue.length()>0)
						resultValue = resultValue.substring(0,resultValue.length()-1);
				
				String resultLabel = "{ \"type\": \"string\", \"id\": \"Role\" },{ \"type\": \"string\", \"id\": \"Name\"},{ \"type\": \"datetime\", \"id\": \"Start\"},{ \"type\": \"datetime\", \"id\": \"End\"}";
				
				String valueChart = "	{"
					   valueChart += "\"type\": \"Timeline\", ";
					  valueChart += "\"displayed\": true, ";
					  valueChart += "\"data\": {";
					  valueChart +=   "\"cols\": ["+resultLabel+"], ";
					  valueChart +=   "\"rows\": ["+resultValue+"] ";
					  /*
					  +   "\"options\": { "
					  +         "\"bars\": \"horizontal\","
					  +         "\"title\": \""+title+"\", \"fill\": 20, \"displayExactValues\": true,"
					  +         "\"vAxis\": { \"title\": \"ms\", \"gridlines\": { \"count\": 100 } }"
					  */
					  valueChart +=  "}";
					  valueChart +="}";
// 				+"\"isStacked\": \"true\","
 	          
//		    +"\"displayExactValues\": true,"
//		    
//		    +"\"hAxis\": { \"title\": \"Date\" }"
//		    +"},"
				logger.info("Value1 >"+valueChart+"<");

				
				return valueChart;		
		}	
}
