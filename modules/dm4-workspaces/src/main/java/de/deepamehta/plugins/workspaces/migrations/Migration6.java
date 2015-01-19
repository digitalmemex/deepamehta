package de.deepamehta.plugins.workspaces.migrations;

import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.Migration;



/**
 * Sets sharing mode of "DeepaMehta" workspace to "Public".
 * Runs only in UPDATE mode.
 * <p>
 * Part of DM 4.5
 */
public class Migration6 extends Migration {

    @Override
    public void run() {
        dms.getTopic("uri", new SimpleValue("dm4.workspaces.deepamehta")).update(
            new TopicModel(null, new ChildTopicsModel().putRef("dm4.workspaces.sharing_mode", "dm4.workspaces.public"))
        );
        // Note: instead of calling update(...) on the entire topic object we could update the childs selectively:
        //     topic.getChildTopics().setRef("dm4.workspaces.sharing_mode", "dm4.workspaces.public")
        // However in this case the topic will loose its label. This is an error in the labeling mechanism.
    }
}