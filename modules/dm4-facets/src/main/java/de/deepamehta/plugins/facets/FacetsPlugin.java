package de.deepamehta.plugins.facets;

import de.deepamehta.plugins.facets.model.FacetValue;
import de.deepamehta.plugins.facets.service.FacetsService;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationDefinition;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.core.util.DeepaMehtaUtils;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.List;
import java.util.logging.Logger;



@Path("/facet")
@Consumes("application/json")
@Produces("application/json")
public class FacetsPlugin extends PluginActivator implements FacetsService {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ************************************
    // *** FacetsService Implementation ***
    // ************************************



    @GET
    @Path("/{facet_type_uri}/topic/{id}")
    @Override
    public Topic getFacet(@PathParam("id") long topicId, @PathParam("facet_type_uri") String facetTypeUri) {
        return getFacet(dms.getTopic(topicId), facetTypeUri);
    }

    @Override
    public Topic getFacet(DeepaMehtaObject object, String facetTypeUri) {
        // ### TODO: integrity check: is the object an instance of that facet type?
        return fetchChildTopic(object, getAssocDef(facetTypeUri));
    }

    // ---

    @GET
    @Path("/multi/{facet_type_uri}/topic/{id}")
    @Override
    public ResultList<RelatedTopic> getFacets(@PathParam("id") long topicId,
                                              @PathParam("facet_type_uri") String facetTypeUri)
                                                                                                                {
        return getFacets(dms.getTopic(topicId), facetTypeUri);
    }

    @Override
    public ResultList<RelatedTopic> getFacets(DeepaMehtaObject object, String facetTypeUri) {
        // ### TODO: integrity check: is the object an instance of that facet type?
        return fetchChildTopics(object, getAssocDef(facetTypeUri));
    }

    // ---

    @GET
    @Path("/topic/{id}")
    @Override
    public Topic getFacettedTopic(@PathParam("id") long topicId,
                                  @QueryParam("facet_type_uri") List<String> facetTypeUris) {
        Topic topic = dms.getTopic(topicId);
        ChildTopicsModel childTopics = topic.getChildTopics().getModel();
        for (String facetTypeUri : facetTypeUris) {
            String childTypeUri = getChildTypeUri(facetTypeUri);
            if (!isMultiFacet(facetTypeUri)) {
                Topic value = getFacet(topic, facetTypeUri);
                if (value != null) {
                    childTopics.put(childTypeUri, value.getModel());
                }
            } else {
                ResultList<RelatedTopic> values = getFacets(topic, facetTypeUri);
                childTopics.put(childTypeUri, DeepaMehtaUtils.toTopicModels(values));
            }
        }
        return topic;
    }

    @POST
    @Path("/{facet_type_uri}/topic/{id}")
    @Transactional
    @Override
    public void addFacetTypeToTopic(@PathParam("id") long topicId, @PathParam("facet_type_uri") String facetTypeUri) {
        dms.createAssociation(new AssociationModel("dm4.core.instantiation",
            new TopicRoleModel(topicId,      "dm4.core.instance"),
            new TopicRoleModel(facetTypeUri, "dm4.facets.facet")
        ));
    }

    // ---

    @PUT
    @Path("/{facet_type_uri}/topic/{id}")
    @Transactional
    @Override
    public void updateFacet(@PathParam("id") long topicId, @PathParam("facet_type_uri") String facetTypeUri,
                                                                                        FacetValue value) {
        try {
            updateFacet(dms.getTopic(topicId), facetTypeUri, value);
        } catch (Exception e) {
            throw new RuntimeException("Updating facet \"" + facetTypeUri + "\" of topic " + topicId +
                " failed (value=" + value + ")", e);
        }
    }

    @Override
    public void updateFacet(DeepaMehtaObject object, String facetTypeUri, FacetValue value) {
        AssociationDefinition assocDef = getAssocDef(facetTypeUri);
        if (!isMultiFacet(facetTypeUri)) {
            object.updateChildTopic(value.getTopic(), assocDef);
        } else {
            object.updateChildTopics(value.getTopics(), assocDef);
        }
    }

    // ---

    // Note: there is a similar private method in AttachedDeepaMehtaObject:
    // fetchChildTopic(AssociationDefinition assocDef, long childTopicId)
    // ### TODO: Extend DeepaMehtaObject interface by hasChildTopic()?
    @Override
    public boolean hasFacet(long topicId, String facetTypeUri, long facetTopicId) {
        String assocTypeUri = getAssocDef(facetTypeUri).getInstanceLevelAssocTypeUri();
        Association assoc = dms.getAssociation(assocTypeUri, topicId, facetTopicId,
            "dm4.core.parent", "dm4.core.child");
        return assoc != null;
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    /**
     * Fetches and returns a child topic or <code>null</code> if no such topic extists.
     * <p>
     * Note: There is a principal copy in AttachedDeepaMehtaObject but here the precondition is different:
     * The given association definition must not necessarily originate from the given object's type definition.
     * ### TODO: meanwhile we have the ValueStorage. Can we use its method instead?
     */
    private RelatedTopic fetchChildTopic(DeepaMehtaObject object, AssociationDefinition assocDef) {
        String assocTypeUri  = assocDef.getInstanceLevelAssocTypeUri();
        String othersTypeUri = assocDef.getChildTypeUri();
        return object.getRelatedTopic(assocTypeUri, "dm4.core.parent", "dm4.core.child", othersTypeUri);
    }

    /**
     * Fetches and returns child topics.
     * <p>
     * Note: There is a principal copy in AttachedDeepaMehtaObject but here the precondition is different:
     * The given association definition must not necessarily originate from the given object's type definition.
     * ### TODO: meanwhile we have the ValueStorage. Can we use its method instead?
     */
    private ResultList<RelatedTopic> fetchChildTopics(DeepaMehtaObject object, AssociationDefinition assocDef) {
        String assocTypeUri  = assocDef.getInstanceLevelAssocTypeUri();
        String othersTypeUri = assocDef.getChildTypeUri();
        return object.getRelatedTopics(assocTypeUri, "dm4.core.parent", "dm4.core.child", othersTypeUri, 0);
    }

    // ---

    private boolean isMultiFacet(String facetTypeUri) {
        return getAssocDef(facetTypeUri).getChildCardinalityUri().equals("dm4.core.many");
    }

    private String getChildTypeUri(String facetTypeUri) {
        return getAssocDef(facetTypeUri).getChildTypeUri();
    }

    private AssociationDefinition getAssocDef(String facetTypeUri) {
        // Note: a facet type has exactly *one* association definition
        return dms.getTopicType(facetTypeUri).getAssocDefs().iterator().next();
    }
}
