package org.bonitasoft.rest.context
// package org.bonitasoft.rest.context;



/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* Configuration                                                                                                                                        */
/*                                                                                                                                                                  */
/*  get the configuration information (default date format for example)                                           */
/*                                                                                                                                                                  */
/* ******************************************************************************** */

import org.bonitasoft.web.extension.ResourceProvider
import org.bonitasoft.web.extension.rest.RestAPIContext;

class RestContextConfiguration {
    protected final static String DEFAULT_DATE_FORMAT = "DEFAULT_DATE_FORMAT"

    private final String CONFIGURATION_FILE_NAME = "configuration.properties"

    private Properties properties

    public RestContextConfiguration(RestAPIContext context){
        properties = loadProperties(CONFIGURATION_FILE_NAME, context.resourceProvider)
    }

    private String get(String data) {
        return properties[data]
    }

    /**
     * return the DateFormat parameters. Return a String, to let the main class manage all error (bad definition for example)
     * Expected value are  DATELONG, DATETIME, DATEJSON
     * @return
     */
    public String getDefaultDateFormat() {
        String value= get(DEFAULT_DATE_FORMAT);
        if (value!=null)
            return value.trim()
        return value;
    }

    private static Properties loadProperties(String fileName, ResourceProvider resourceProvider) {
        Properties props = new Properties()
        resourceProvider.getResourceAsStream(fileName).withStream { InputStream s ->
            props.load s
        }
        return props
    }
}