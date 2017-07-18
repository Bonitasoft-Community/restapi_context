package org.bonitasoft.rest.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bonitasoft.engine.api.APIAccessor;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.ProfileAPI;
import org.bonitasoft.engine.api.permission.APICallContext;
import org.bonitasoft.engine.api.permission.PermissionRule;
import org.bonitasoft.engine.exception.BonitaException;
import org.bonitasoft.engine.exception.NotFoundException;
import org.bonitasoft.engine.session.APISession;

import java.util.logging.Logger;

/**
 * Copyright (C) 2014 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 **/
/**
 *
 * Let a user access only document on cases that he is involved in
 *
 * <ul>
 *     <li>bpm/document</li>
 *     <li>bpm/archivedDocument</li>
 *     <li>bpm/caseDocument</li>
 * </ul>
 *
 *
 *
 * @author Baptiste Mesta
 * @author Truc Nguyen
 */
class DocumentPermissionContextRule implements PermissionRule {

    public Logger loggerUtil = Logger.getLogger("org.bonitasoft.DocumentPermissionRuleRestContext");
    public static final String CASE_ID = "caseId"
    public static final String ARCHIVED_CASE_ID = "archivedCaseId"

    @Override
    public boolean isAllowed(APISession apiSession, APICallContext apiCallContext, APIAccessor apiAccessor, org.bonitasoft.engine.api.Logger  loggerBonita) {

        long currentUserId = apiSession.getUserId();

        def resourceId = apiCallContext.getResourceId()
        loggerUtil.info("DocumentPermissionContextRule.isAllowed ressourceId["+resourceId+"]");

        boolean resultIsAllowed= false;
        if (resourceId != null) {
            resultIsAllowed= checkMethodWithResourceId(resourceId, apiAccessor, currentUserId);
            return resultIsAllowed;
        }

        if (apiCallContext.isGET()) {
            resultIsAllowed= checkGetMethod(apiCallContext, apiAccessor, currentUserId)
        } else if (apiCallContext.isPOST()) {
            resultIsAllowed= checkPostMethod(apiCallContext, apiAccessor, currentUserId)
        }
        // we don't have a policity to report the attack, because here someone try to access something he dont have the rigth !
        if (resultIsAllowed)
            loggerUtil.info("DocumentPermissionContextRule.isAllowed : allowed");
        else
            loggerUtil.info("DocumentPermissionContextRule.isAllowed: NOT ALLOWED");

        return resultIsAllowed;
    }

    private boolean checkMethodWithResourceId(String resourceId, APIAccessor apiAccessor, long currentUserId) {
        loggerUtil.info("DocumentPermissionContextRule.checkMethodWithResourceId");

        def processAPI = apiAccessor.getProcessAPI()
        try {
            long documentId = Long.valueOf(resourceId)
            def processInstanceId = processAPI.getDocument(documentId).getProcessInstanceId()
            return isInvolved(processAPI, currentUserId, processInstanceId) ||
                    isSupervisor(processAPI, currentUserId, processInstanceId)
        }
        catch (NumberFormatException e) {
            return true
        }
    }

    private boolean checkPostMethod(APICallContext apiCallContext, APIAccessor apiAccessor, long currentUserId) {
        loggerUtil.info("DocumentPermissionContextRule.checkPostMethod");

        ObjectMapper mapper = new ObjectMapper();
        def map = mapper.readValue(apiCallContext.getBody(), Map.class)

        def processInstanceIdAsString = map.get(CASE_ID)
        if (processInstanceIdAsString == null || processInstanceIdAsString.toString().isEmpty()) {
            return true;
        }
        def processInstanceId = Long.valueOf(processInstanceIdAsString.toString())
        if (processInstanceId <= 0) {
            return true;
        }
        try {
            def processAPI = apiAccessor.getProcessAPI()
            def processDefinitionId = processAPI.getProcessInstance(processInstanceId).getProcessDefinitionId()
            return isInvolved(processAPI, currentUserId, processInstanceId) ||
                    processAPI.isUserProcessSupervisor(processDefinitionId, currentUserId)
        } catch (NotFoundException e) {
            return true
        }
    }

    private boolean checkGetMethod(APICallContext apiCallContext, APIAccessor apiAccessor, long currentUserId) {
        loggerUtil.severe("DocumentPermissionContextRule.checkGetMethod");

        def filters = apiCallContext.getFilters()
        ProcessAPI processAPI = apiAccessor.getProcessAPI();
        IdentityAPI identityAPI = apiAccessor.getIdentityAPI();
        ProfileAPI profileAPI = apiAccessor.getProfileAPI();

        long processInstanceId = -1
        long processDefinitionId = -1

        def archivedCaseIdAsString = filters.get(ARCHIVED_CASE_ID)
        if (archivedCaseIdAsString != null) {
            def archivedCaseId = Long.valueOf(archivedCaseIdAsString)
            processInstanceId = processAPI.getArchivedProcessInstance(archivedCaseId).getSourceObjectId()
            processDefinitionId = processAPI.getFinalArchivedProcessInstance(processInstanceId).getProcessDefinitionId()
        }
        else {
            def processInstanceIdAsString = filters.get(CASE_ID)
            if (processInstanceIdAsString != null) {
                processInstanceId = Long.valueOf(processInstanceIdAsString)
                processDefinitionId = processAPI.getProcessInstance(processInstanceId).getProcessDefinitionId()
            }
        }

        // Ok, is a Pilot exist ? If yes, then we have to check the rule according the pilot
        RestContextCheckDocumentDownload checkDocumentDownload = new RestContextCheckDocumentDownload( currentUserId,  processAPI, identityAPI,  profileAPI);

        Boolean isAllowedRestContext = checkDocumentDownload.isAllowedFromPermission( apiCallContext.getQueryString() );
        if (isAllowedRestContext !=null)
            return isAllowedRestContext;

        // Standard way

        if (processInstanceId > 0 && processDefinitionId > 0) {
            return isInvolved(processAPI, currentUserId, processInstanceId) ||
                    processAPI.isUserProcessSupervisor(processDefinitionId, currentUserId)
        }

        return false;
    }


    private boolean isInvolved(ProcessAPI processAPI, long currentUserId, long processInstanceId) {
        loggerUtil.info("DocumentPermissionContextRule.isInvolved");

        try {
            // DocumentPermissionContextRule.isAllowed : NOPE : it's not because the user is my manager that he can access to my document ! No kidding !
            return processAPI.isInvolvedInProcessInstance(currentUserId, processInstanceId) ;
        } catch (BonitaException e) {
            return true
        }
    }

    private boolean isSupervisor(ProcessAPI processAPI, long currentUserId, long processInstanceId) {
        loggerUtil.info("DocumentPermissionContextRule.isSupervisor");

        def processDefinitionId
        try {
            processDefinitionId = processAPI.getProcessInstance(processInstanceId).getProcessDefinitionId()
        } catch (BonitaException e) {
            try {
                processDefinitionId = processAPI.getFinalArchivedProcessInstance(processInstanceId).getProcessDefinitionId()
            } catch (NotFoundException e1) {
                return true
            }
        }
        return processAPI.isUserProcessSupervisor(processDefinitionId, currentUserId)
    }
}
