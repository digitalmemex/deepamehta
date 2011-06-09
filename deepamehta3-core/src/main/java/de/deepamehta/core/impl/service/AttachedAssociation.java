package de.deepamehta.core.impl.service;

import de.deepamehta.core.Association;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Role;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicRole;
import de.deepamehta.core.impl.model.AssociationBase;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.AssociationRoleModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicRoleModel;

import java.util.HashSet;
import java.util.Set;

import java.util.logging.Logger;



/**
 * An association that is attached to the {@link DeepaMehtaService}.
 */
class AttachedAssociation extends AssociationBase {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Role role1;
    private Role role2;

    private EmbeddedService dms;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    AttachedAssociation(Association assoc, EmbeddedService dms) {
        super(getModel(assoc));
        this.dms = dms;     // Note: dms must be initialized *before* the attached roles are created
        this.role1 = createAttachedRole(getModel(assoc).getRoleModel1());
        this.role2 = createAttachedRole(getModel(assoc).getRoleModel2());
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // *****************************
    // *** Association Overrides ***
    // *****************************



    @Override
    public void setTypeUri(String assocTypeUri) {
        // 1) update memory
        super.setTypeUri(assocTypeUri);
        // 2) update DB
        storeTypeUri();
    }

    // ---

    @Override
    public Role getRole1() {
        return role1;
    }

    @Override
    public Role getRole2() {
        return role2;
    }

    // ---

    // compare to Neo4jHyperEdge.getHyperObject()
    @Override
    public Role getRole(long objectId) {
        long id1 = getObjectId(((AttachedRole) getRole1()).getModel());
        long id2 = getObjectId(((AttachedRole) getRole2()).getModel());
        //
        if (id1 == objectId && id2 == objectId) {
            throw new RuntimeException("Self-connected hyper objects are not supported (" + this + ")");
        }
        //
        if (id1 == objectId) {
            return getRole1();
        } else if (id2 == objectId) {
            return getRole2();
        } else {
            throw new RuntimeException("Topic/Association " + objectId + " plays no role in " + this);
        }
    }



    // === Traversal ===

    @Override
    public Topic getTopic(String roleTypeUri) {
        Set<Topic> topics = getTopics(roleTypeUri);
        switch (topics.size()) {
        case 0:
            return null;
        case 1:
            return topics.iterator().next();
        default:
            throw new RuntimeException("Ambiguity in association: " + topics.size() + " topics have role type \"" +
                roleTypeUri + "\" (" + this + ")");
        }
    }

    @Override
    public Set<Topic> getTopics(String roleTypeUri) {
        Set<Topic> topics = new HashSet();
        filterTopic(getRole1(), roleTypeUri, topics);
        filterTopic(getRole2(), roleTypeUri, topics);
        return topics;
    }

    // ---

    @Override
    public AttachedRelatedTopic getRelatedTopic(String assocTypeUri, String myRoleTypeUri, String othersRoleTypeUri,
                                                                                           String othersTopicTypeUri,
                                                                                           boolean fetchComposite) {
        RelatedTopic topic = dms.storage.getAssociationRelatedTopic(getId(), assocTypeUri, myRoleTypeUri,
            othersRoleTypeUri, othersTopicTypeUri);
        return topic != null ? dms.attach(topic, fetchComposite) : null;
    }

    @Override
    public Set<RelatedTopic> getRelatedTopics(String assocTypeUri, String myRoleTypeUri, String othersRoleTypeUri,
                                                                                         String othersTopicTypeUri,
                                                                                         boolean fetchComposite) {
        return dms.attach(dms.storage.getAssociationRelatedTopics(getId(), assocTypeUri, myRoleTypeUri,
            othersRoleTypeUri, othersTopicTypeUri), fetchComposite);
    }



    // ----------------------------------------------------------------------------------------- Package Private Methods

    void update(AssociationModel assocModel) {
        logger.info("Updating association " + getId() + " (new " + assocModel + ")");
        //
        // Note: We must lookup the roles individually.
        // The role order (getRole1(), getRole2()) is undeterministic and not fix.
        Role role1 = getRole(getObjectId(assocModel.getRoleModel1()));
        Role role2 = getRole(getObjectId(assocModel.getRoleModel2()));
        //
        String newTypeUri = assocModel.getTypeUri();
        String newRoleTypeUri1 = assocModel.getRoleModel1().getRoleTypeUri();
        String newRoleTypeUri2 = assocModel.getRoleModel2().getRoleTypeUri();
        //
        String roleTypeUri1 = role1.getRoleTypeUri();
        String roleTypeUri2 = role2.getRoleTypeUri();
        //
        boolean typeUriChanged = !getTypeUri().equals(newTypeUri);
        boolean roleType1Changed = !roleTypeUri1.equals(newRoleTypeUri1);
        boolean roleType2Changed = !roleTypeUri2.equals(newRoleTypeUri2);
        //
        if (typeUriChanged) {
            logger.info("Changing type from \"" + getTypeUri() + "\" -> \"" + newTypeUri + "\"");
            setTypeUri(newTypeUri);
        }
        if (roleType1Changed) {
            logger.info("Changing role type 1 from \"" + roleTypeUri1 + "\" -> \"" + newRoleTypeUri1 + "\"");
            role1.setRoleTypeUri(newRoleTypeUri1);
        }
        if (roleType2Changed) {
            logger.info("Changing role type 2 from \"" + roleTypeUri2 + "\" -> \"" + newRoleTypeUri2 + "\"");
            role2.setRoleTypeUri(newRoleTypeUri2);
        }
        //
        if (!typeUriChanged && !roleType1Changed && !roleType2Changed) {
            logger.info("Updating association " + getId() + " ABORTED -- no changes made by user");
        }
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private RelatedTopic fetchTypeTopic() {
        // assocTypeUri=null (supposed to be "dm3.core.instantiation" but not possible ### explain)
        return getRelatedTopic(null, "dm3.core.instance", "dm3.core.type", "dm3.core.assoc_type",
            false);     // fetchComposite=false
    }

    // ---

    private void filterTopic(Role role, String roleTypeUri, Set<Topic> topics) {
        if (role instanceof TopicRole && role.getRoleTypeUri().equals(roleTypeUri)) {
            topics.add(fetchRoleTopic((TopicRole) role));
        }
    }

    private Topic fetchRoleTopic(TopicRole role) {
        return dms.getTopic(role.getTopicId(), false, null);    // fetchComposite=false, clientContext=null
    }



    // === Store ===

    private void storeTypeUri() {
        // remove current assignment
        long assocId = fetchTypeTopic().getAssociation().getId();
        dms.deleteAssociation(assocId, null);  // clientContext=null
        // create new assignment
        dms.associateWithAssociationType(this);
    }    



    // === Helper ===

    private Role createAttachedRole(RoleModel model) {
        if (model instanceof TopicRoleModel) {
            return new AttachedTopicRole((TopicRoleModel) model, this, dms);
        } else if (model instanceof AssociationRoleModel) {
            return new AttachedAssociationRole((AssociationRoleModel) model, this, dms);
        } else {
            throw new RuntimeException("Unexpected RoleModel object (" + model + ")");
        }
    }

    // ### TODO: probably a generic getId() should be added to the Role interface.
    private long getObjectId(RoleModel model) {
        if (model instanceof TopicRoleModel) {
            return ((TopicRoleModel) model).getTopicId();
        } else if (model instanceof AssociationRoleModel) {
            return ((AssociationRoleModel) model).getAssociationId();
        } else {
            throw new RuntimeException("Unexpected RoleModel object (" + model + ")");
        }
    }

    // ### TODO: probably getModel() should be added to the Association (and all other) interface.
    private static AssociationModel getModel(Association assoc) {
        return ((AssociationBase) assoc).getModel();
    }
}