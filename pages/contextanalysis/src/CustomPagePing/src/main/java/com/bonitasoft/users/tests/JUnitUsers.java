package com.bonitasoft.users.tests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.ApiAccessType;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.LoginAPI;
import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.util.APITypeManager;
import org.junit.Test;

import com.bonitasoft.users.UsersOperation;

public class JUnitUsers {

    static Logger logger = Logger.getLogger(JUnitUsers.class.getName());

    @Test
    public void getUsers() {
        final UsersOperation userOperations = new UsersOperation();

        final APISession apiSession = login();
        IdentityAPI identityAPI;
        try {
            identityAPI = TenantAPIAccessor.getIdentityAPI(apiSession);

            final List<Map<String, Object>> listMapUsers = userOperations.getUsersList(identityAPI);
            System.out.println("ResultGetuserList:" + listMapUsers);
        } catch (final BonitaHomeNotSetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final ServerAPIException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final UnknownAPITypeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public APISession login()
    {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("server.url", "http://localhost:8080");
        map.put("application.name", "bonita");
        APITypeManager.setAPITypeAndParams(ApiAccessType.HTTP, map);

        // Set the username and password
        // final String username = "helen.kelly";
        final String username = "walter.bates";
        final String password = "bpm";
        logger.info("userName[" + username + "]");
        logger.setLevel(Level.FINEST);
        final ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINEST);
        logger.addHandler(handler);

        logger.fine("Debug level is visible");

        // get the LoginAPI using the TenantAPIAccessor
        LoginAPI loginAPI;
        try {
            loginAPI = TenantAPIAccessor.getLoginAPI();
            // log in to the tenant to create a session
            final APISession session = loginAPI.login(username, password);
            return session;
        } catch (final Exception e)
        {
            logger.severe("during login " + e.toString());
        }
        return null;
    }
}
