package de.deepamehta.plugins.accesscontrol.migrations;

import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;

import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;



/**
 * Sets "admin" as the owner of the "DeepaMehta" workspace.
 * Runs ALWAYS.
 * <p>
 * Part of DM 4.5
 */
public class Migration7 extends Migration {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    @Inject
    private AccessControlService acService;

    @Inject
    private WorkspacesService wsService;

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public void run() {
        acService.setWorkspaceOwner(wsService.getWorkspace(WorkspacesService.DEEPAMEHTA_WORKSPACE_URI),
            AccessControlService.ADMIN_USERNAME);
        // Note: we don't set a particular creator/modifier here as we don't want suggest that the DeepaMehta
        // workspace has been created by the "admin" user. Instead the creator/modifier of the DeepaMehhta
        // workspace remain undefined as the DeepaMehta workspace is actually created by the system itself.
    }
}
