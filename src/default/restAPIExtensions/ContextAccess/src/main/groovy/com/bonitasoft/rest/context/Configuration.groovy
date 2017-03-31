package com.bonitasoft.rest.context

import org.bonitasoft.web.extension.rest.RestAPIContext

import org.bonitasoft.web.extension.ResourceProvider

class Configuration {
    protected final static String DEFAULT_DATE_FORMAT = "DEFAULT_DATE_FORMAT"

    private final String CONFIGURATION_FILE_NAME = "configuration.properties"

    private Properties properties

    public Configuration(RestAPIContext context){
        properties = loadProperties(CONFIGURATION_FILE_NAME, context.resourceProvider)
    }

    private String get(String data) {
        return properties[data]
    }

    public GetContext.DateFormat getDefaultDateFormat() {
		return GetContext.DateFormat.valueOf(get(DEFAULT_DATE_FORMAT))
    }

    private static Properties loadProperties(String fileName, ResourceProvider resourceProvider) {
        Properties props = new Properties()
        resourceProvider.getResourceAsStream(fileName).withStream { InputStream s ->
            props.load s
        }
        return props
    }
}
