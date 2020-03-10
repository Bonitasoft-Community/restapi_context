package com.bonitasoft.users;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.identity.UserCriterion;

public class UsersOperation {

    static Logger logger = Logger.getLogger(UsersOperation.class.getName());

    public List<Map<String, Object>> getUsersList(final IdentityAPI identityApi)
    {

        logger.info("GetUsersList");
        final List<Map<String, Object>> listMapUsers = new ArrayList<Map<String, Object>>();
        try
        {
            logger.info("getNumberOfUsers [" + identityApi.getNumberOfUsers() + "] ");

            final List<User> listIdentityUsers = identityApi.getUsers(0, 1000, UserCriterion.USER_NAME_ASC);
            for (final User user : listIdentityUsers)
            {
                logger.info("userId[" + user.getId() + "] userName [" + user.getUserName() + "] ");
                final HashMap<String, Object> oneUserMap = new HashMap<String, Object>();
                oneUserMap.put("username", user.getUserName());
                oneUserMap.put("firstname", user.getFirstName());
                oneUserMap.put("lastname", user.getLastName());
                oneUserMap.put("enable", user.isEnabled() ? "ACTIF" : "INACTIF");

                listMapUsers.add(oneUserMap);
            }

        } catch (final Exception e)
        {
            logger.severe("GetUserList : " + e.toString());
        }

        return listMapUsers;
    }
}
