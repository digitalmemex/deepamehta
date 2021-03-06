package de.deepamehta.core.impl;

import de.deepamehta.core.AssociationDefinition;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Type;
import de.deepamehta.core.ViewConfiguration;
import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TypeModel;
import de.deepamehta.core.service.Directive;
import de.deepamehta.core.service.Directives;

import org.codehaus.jettison.json.JSONObject;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



abstract class AttachedType extends AttachedTopic implements Type {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Map<String, AssociationDefinition> assocDefs;   // Attached object cache
    private ViewConfiguration viewConfig;                   // Attached object cache

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    AttachedType(TypeModel model, EmbeddedService dms) {
        super(model, dms);
        // init attached object cache
        initAssocDefs();
        initViewConfig();
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ******************************************
    // *** AttachedDeepaMehtaObject Overrides ***
    // ******************************************



    // === Updating ===

    @Override
    public void update(TypeModel model) {
        boolean uriChanged = hasUriChanged(model.getUri());
        if (uriChanged) {
            _removeFromTypeCache();
        }
        //
        super.update(model);
        //
        if (uriChanged) {
            putInTypeCache();   // abstract
        }
        //
        updateDataTypeUri(model.getDataTypeUri());
        updateAssocDefs(model.getAssocDefs());
        updateSequence(model.getAssocDefs());
        updateLabelConfig(model.getLabelConfig());
    }



    // === Deletion ===

    @Override
    public void delete() {
        try {
            logger.info("Deleting " + className() + " \"" + getUri() + "\"");
            //
            super.delete();   // delete type topic
            //
            _removeFromTypeCache();
        } catch (Exception e) {
            throw new RuntimeException("Deleting " + className() + " \"" + getUri() + "\" failed", e);
        }
    }



    // ***************************
    // *** Type Implementation ***
    // ***************************



    // === Model ===

    // --- Data Type ---

    @Override
    public String getDataTypeUri() {
        return getModel().getDataTypeUri();
    }

    @Override
    public void setDataTypeUri(String dataTypeUri) {
        // update memory
        getModel().setDataTypeUri(dataTypeUri);
        // update DB
        storeDataTypeUri(dataTypeUri);
    }

    // --- Index Modes ---

    @Override
    public List<IndexMode> getIndexModes() {
        return getModel().getIndexModes();
    }

    @Override
    public void addIndexMode(IndexMode indexMode) {
        // update memory
        getModel().addIndexMode(indexMode);
        // update DB
        dms.typeStorage.storeIndexMode(getUri(), indexMode);
        indexAllInstances(indexMode);
    }

    // --- Association Definitions ---

    @Override
    public Collection<AssociationDefinition> getAssocDefs() {
        return assocDefs.values();
    }

    @Override
    public AssociationDefinition getAssocDef(String childTypeUri) {
        AssociationDefinition assocDef = assocDefs.get(childTypeUri);
        if (assocDef == null) {
            throw new RuntimeException("Schema violation: association definition \"" +
                childTypeUri + "\" not found in " + this);
        }
        return assocDef;
    }

    @Override
    public boolean hasAssocDef(String childTypeUri) {
        return assocDefs.get(childTypeUri) != null;
    }

    @Override
    public void addAssocDef(AssociationDefinitionModel model) {
        // Note: the predecessor must be determined *before* the memory is updated
        AssociationDefinitionModel predecessor = lastAssocDef();
        // update memory
        getModel().addAssocDef(model);      // update model
        _addAssocDef(model);                // update attached object cache
        // update DB
        dms.typeStorage.storeAssociationDefinition(model);
        dms.typeStorage.appendToSequence(getUri(), model, predecessor);
    }

    @Override
    public void updateAssocDef(AssociationDefinitionModel model) {
        // update memory
        getModel().updateAssocDef(model);   // update model
        _addAssocDef(model);                // update attached object cache
        // update DB
        // ### Note: the DB is not updated here! In case of interactive assoc type change the association is
        // already updated in DB. => See interface comment.
    }

    @Override
    public void removeAssocDef(String childTypeUri) {
        // We trigger deleting an association definition by deleting the underlying association. This mimics deleting an
        // association definition interactively in the webclient. Updating this type definition's memory and DB sequence
        // is triggered then by the Type Editor plugin's preDeleteAssociation() hook.
        // This way deleting an association definition works for both cases: 1) interactive deletion (when the user
        // deletes an association), and 2) programmatical deletion (e.g. from a migration).
        getAssocDef(childTypeUri).delete();
    }

    // --- Label Configuration ---

    @Override
    public List<String> getLabelConfig() {
        return getModel().getLabelConfig();
    }

    @Override
    public void setLabelConfig(List<String> labelConfig) {
        // update memory
        getModel().setLabelConfig(labelConfig);
        // update DB
        dms.typeStorage.storeLabelConfig(labelConfig, getModel().getAssocDefs());
    }

    // --- View Configuration ---

    @Override
    public ViewConfiguration getViewConfig() {
        return viewConfig;
    }

    // FIXME: to be dropped
    @Override
    public Object getViewConfig(String typeUri, String settingUri) {
        return getModel().getViewConfig(typeUri, settingUri);
    }

    // ---

    @Override
    public TypeModel getModel() {
        return (TypeModel) super.getModel();
    }



    // ----------------------------------------------------------------------------------------- Package Private Methods

    abstract void putInTypeCache();

    abstract void removeFromTypeCache();

    // ---

    abstract Directive getDeleteTypeDirective();

    abstract List<? extends DeepaMehtaObject> getAllInstances();

    // ---

    void removeAssocDefFromMemoryAndRebuildSequence(String childTypeUri) {
        // update memory
        getModel().removeAssocDef(childTypeUri);    // update model
        _removeAssocDef(childTypeUri);              // update attached object cache
        // update DB
        dms.typeStorage.rebuildSequence(this);
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    // === Update ===

    private boolean hasUriChanged(String newUri) {
        return newUri != null && !getUri().equals(newUri);
    }

    // ---

    private void updateDataTypeUri(String newDataTypeUri) {
        if (newDataTypeUri != null) {
            String dataTypeUri = getDataTypeUri();
            if (!dataTypeUri.equals(newDataTypeUri)) {
                logger.info("### Changing data type URI from \"" + dataTypeUri + "\" -> \"" + newDataTypeUri + "\"");
                setDataTypeUri(newDataTypeUri);
            }
        }
    }

    private void storeDataTypeUri(String dataTypeUri) {
        // remove current assignment
        getRelatedTopic("dm4.core.aggregation", "dm4.core.type", "dm4.core.default", "dm4.core.data_type")
            .getRelatingAssociation().delete();
        // create new assignment
        dms.typeStorage.storeDataType(getUri(), dataTypeUri);
    }

    // ---

    private void indexAllInstances(IndexMode indexMode) {
        List<? extends DeepaMehtaObject> objects = getAllInstances();
        //
        String str = "\"" + getSimpleValue() + "\" (" + getUri() + ") instances";
        if (getIndexModes().size() > 0) {
            if (objects.size() > 0) {
                logger.info("### Indexing " + objects.size() + " " + str + " (indexMode=" + indexMode + ")");
            } else {
                logger.info("### Indexing " + str + " ABORTED -- no instances in DB");
            }
        } else {
            logger.info("### Indexing " + str + " ABORTED -- no index mode set");
        }
        //
        for (DeepaMehtaObject obj : objects) {
            dms.valueStorage.indexSimpleValue(obj.getModel(), indexMode);
        }
    }

    // ---

    private void updateAssocDefs(Collection<AssociationDefinitionModel> newAssocDefs) {
        for (AssociationDefinitionModel assocDef : newAssocDefs) {
            getAssocDef(assocDef.getChildTypeUri()).update(assocDef);
        }
    }

    // ---

    private void updateSequence(Collection<AssociationDefinitionModel> newAssocDefs) {
        if (!hasSequenceChanged(newAssocDefs)) {
            return;
        }
        logger.info("### Changing assoc def sequence");
        // update memory
        getModel().removeAllAssocDefs();
        for (AssociationDefinitionModel assocDef : newAssocDefs) {
            getModel().addAssocDef(assocDef);
        }
        initAssocDefs();    // attached object cache
        // update DB
        dms.typeStorage.rebuildSequence(this);
    }

    private boolean hasSequenceChanged(Collection<AssociationDefinitionModel> newAssocDefs) {
        Collection<AssociationDefinition> assocDefs = getAssocDefs();
        if (assocDefs.size() != newAssocDefs.size()) {
            throw new RuntimeException("adding/removing of assoc defs not yet supported via updateTopicType() call");
        }
        //
        Iterator<AssociationDefinitionModel> i = newAssocDefs.iterator();
        for (AssociationDefinition assocDef : assocDefs) {
            AssociationDefinitionModel newAssocDef = i.next();
            if (!assocDef.getChildTypeUri().equals(newAssocDef.getChildTypeUri())) {
                return true;
            }
        }
        //
        return false;
    }

    // ---

    private void updateLabelConfig(List<String> newLabelConfig) {
        if (!getLabelConfig().equals(newLabelConfig)) {
            logger.info("### Changing label configuration");
            setLabelConfig(newLabelConfig);
        }
    }



    // === Helper ===

    /**
     * Returns the last association definition of this type or
     * <code>null</code> if there are no association definitions.
     *
     * ### TODO: move to class TypeModel?
     */
    private AssociationDefinitionModel lastAssocDef() {
        AssociationDefinitionModel lastAssocDef = null;
        for (AssociationDefinitionModel assocDef : getModel().getAssocDefs()) {
            lastAssocDef = assocDef;
        }
        return lastAssocDef;
    }

    // --- Attached Object Cache ---

    // ### FIXME: make it private
    protected void initAssocDefs() {
        this.assocDefs = new LinkedHashMap();
        for (AssociationDefinitionModel model : getModel().getAssocDefs()) {
            _addAssocDef(model);
        }
    }

    /**
     * @param   model   the new association definition.
     *                  Note: all fields must be initialized.
     */
    private void _addAssocDef(AssociationDefinitionModel model) {
        AttachedAssociationDefinition assocDef = new AttachedAssociationDefinition(model, dms);
        assocDefs.put(assocDef.getChildTypeUri(), assocDef);
    }

    private void _removeAssocDef(String childTypeUri) {
        // error check
        getAssocDef(childTypeUri);
        //
        assocDefs.remove(childTypeUri);
    }

    // ---

    private void initViewConfig() {
        RoleModel configurable = dms.typeStorage.createConfigurableType(getId());   // ### type ID is uninitialized
        this.viewConfig = new AttachedViewConfiguration(configurable, getModel().getViewConfigModel(), dms);
    }



    // ===

    /**
     * Removes this type from type cache and adds a DELETE TYPE directive to the given set of directives.
     */
    private void _removeFromTypeCache() {
        removeFromTypeCache();                      // abstract
        addDeleteTypeDirective();
    }

    private void addDeleteTypeDirective() {
        Directive dir = getDeleteTypeDirective();   // abstract
        Directives.get().add(dir, new JSONWrapper("uri", getUri()));
    }

    // ------------------------------------------------------------------------------------------------- Private Classes

    private class JSONWrapper implements JSONEnabled {

        private JSONObject wrapped;

        private JSONWrapper(String key, Object value) {
            try {
                wrapped = new JSONObject();
                wrapped.put(key, value);
            } catch (Exception e) {
                throw new RuntimeException("Constructing a JSONWrapper failed", e);
            }
        }

        @Override
        public JSONObject toJSON() {
            return wrapped;
        }
    }
}
