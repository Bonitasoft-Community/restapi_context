package org.bonitasoft.rest.context
// package org.bonitasoft.rest.context;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.ProfileAPI;
import org.bonitasoft.engine.bpm.document.Document

/* ******************************************************************************** */
/*                                                                                                                                                                  */
/* CheckDocumentDownload                                                                                                                                                         */
/*                                                                                                                                                                  */
/*  Method who can be called from the DocumentPermissionContextRule                                                        */
/*                                                                                                                                                                  */
/* ******************************************************************************** */

public class RestContextCheckDocumentDownload {
    private static Logger logger = Logger.getLogger("org.bonitasoft.rest.context.RestContextCheckDocumentDownload");
    Long userId = null;
    ProcessAPI processAPI;
    IdentityAPI identityAPI;
    ProfileAPI profileAPI;

    public Long contentStorageId;
    public Document document;
    public Long processInstanceId;

    public RestContextCheckDocumentDownload( final Long userId, final ProcessAPI processAPI, final IdentityAPI identityAPI,  final ProfileAPI profileAPI) {
        this.userId = userId;
        this.processAPI= processAPI;
        this.identityAPI = identityAPI;
        this.profileAPI = profileAPI;
    }

    /**
     * return null : no pilot is detected, can't give a status
     * BOOLEAN.TRUE : is isAllowed
     * BOOLEAN.FALSE : it's not allowed
     */
    public Boolean isAllowedFromPermission( String queryString  ) {
        RestContextCaseId contextCaseId;
        RestContextPilot pilot = new RestContextPilot();
        contextCaseId = new RestContextCaseId( userId, processAPI, identityAPI,  profileAPI);
        contextCaseId.setPilot(  pilot  );

        contextCaseId.decodeParametersFromQueryString( queryString );
        pilot.decodeParameters( contextCaseId );

        if (!pilot.isPilotDetected()) {
            return null;
        }
        return Boolean.valueOf(  isAllowed( contextCaseId  ))
    }



    /**
     *
     */
    public boolean isAllowed( final RestContextCaseId contextCaseId  ) {

        try {
            if ((contextCaseId.contentStorageId !=null) && (contextCaseId.document  == null)) {
                contextCaseId.logRest("contentStorageId["+contextCaseId.contentStorageId+"] detected but no document :  NOT ALLOWED");
                return false;
            }
            if (contextCaseId.document!=null) {

                String docName = contextCaseId.document.getName();
                boolean isAllowed= contextCaseId.isAllowVariableName( docName );
                contextCaseId.logRest("contentStorageId["+contextCaseId.contentStorageId+"] docName["+ docName+"] allow on this variable ? "+isAllowed+"]");
                return isAllowed;
            }
        }
        catch (Exception e) {
            logger.severe("CheckDocumentDownload.checkFromContentStorageId Exeption "+e.toString());
            return false; // by default
        }
        return true;
    }
}
