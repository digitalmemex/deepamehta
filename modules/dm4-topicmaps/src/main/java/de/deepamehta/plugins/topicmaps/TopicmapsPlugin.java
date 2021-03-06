package de.deepamehta.plugins.topicmaps;

import de.deepamehta.plugins.topicmaps.model.AssociationViewmodel;
import de.deepamehta.plugins.topicmaps.model.TopicViewmodel;
import de.deepamehta.plugins.topicmaps.model.TopicmapViewmodel;
import de.deepamehta.plugins.topicmaps.model.ViewProperties;
import de.deepamehta.plugins.topicmaps.service.TopicmapsService;

import de.deepamehta.core.Association;
import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.AssociationRoleModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.Transactional;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



@Path("/topicmap")
@Consumes("application/json")
@Produces("application/json")
public class TopicmapsPlugin extends PluginActivator implements TopicmapsService {

    // ------------------------------------------------------------------------------------------------------- Constants

    // association type semantics ### TODO: to be dropped. Model-driven manipulators required.
    private static final String TOPIC_MAPCONTEXT       = "dm4.topicmaps.topic_mapcontext";
    private static final String ASSOCIATION_MAPCONTEXT = "dm4.topicmaps.association_mapcontext";
    private static final String ROLE_TYPE_TOPICMAP     = "dm4.core.default";
    private static final String ROLE_TYPE_TOPIC        = "dm4.topicmaps.topicmap_topic";
    private static final String ROLE_TYPE_ASSOCIATION  = "dm4.topicmaps.topicmap_association";

    private static final String PROP_X          = "dm4.topicmaps.x";
    private static final String PROP_Y          = "dm4.topicmaps.y";
    private static final String PROP_VISIBILITY = "dm4.topicmaps.visibility";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Map<String, TopicmapRenderer> topicmapRenderers = new HashMap();
    private List<ViewmodelCustomizer> viewmodelCustomizers = new ArrayList();

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    public TopicmapsPlugin() {
        // Note: registering the default renderer in the InitializePluginListener would be too late.
        // The renderer is already needed in the PostInstallPluginListener.
        registerTopicmapRenderer(new DefaultTopicmapRenderer());
    }



    // ***************************************
    // *** TopicmapsService Implementation ***
    // ***************************************



    @POST
    @Path("/{name}/{topicmap_renderer_uri}")
    @Transactional
    @Override
    public Topic createTopicmap(@PathParam("name") String name,
                                @PathParam("topicmap_renderer_uri") String topicmapRendererUri) {
        ChildTopicsModel topicmapState = getTopicmapRenderer(topicmapRendererUri).initialTopicmapState();
        return dms.createTopic(new TopicModel("dm4.topicmaps.topicmap", new ChildTopicsModel()
            .put("dm4.topicmaps.name", name)
            .put("dm4.topicmaps.topicmap_renderer_uri", topicmapRendererUri)
            .put("dm4.topicmaps.state", topicmapState)
        ));
    }

    // ---

    @GET
    @Path("/{id}")
    @Override
    public TopicmapViewmodel getTopicmap(@PathParam("id") long topicmapId,
                                         @QueryParam("include_childs") boolean includeChilds) {
        try {
            logger.info("Loading topicmap " + topicmapId + " (includeChilds=" + includeChilds + ")");
            // Note: a TopicmapViewmodel is not a DeepaMehtaObject. So the JerseyResponseFilter's automatic
            // child topic loading is not applied. We must load the child topics manually here.
            Topic topicmapTopic = dms.getTopic(topicmapId).loadChildTopics();
            Map<Long, TopicViewmodel> topics = fetchTopics(topicmapTopic, includeChilds);
            Map<Long, AssociationViewmodel> assocs = fetchAssociations(topicmapTopic);
            //
            return new TopicmapViewmodel(topicmapTopic.getModel(), topics, assocs);
        } catch (Exception e) {
            throw new RuntimeException("Fetching topicmap " + topicmapId + " failed", e);
        }
    }

    @Override
    public boolean isTopicInTopicmap(long topicmapId, long topicId) {
        return fetchTopicRefAssociation(topicmapId, topicId) != null;
    }

    // ---

    @POST
    @Path("/{id}/topic/{topic_id}")
    @Transactional
    @Override
    public void addTopicToTopicmap(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                   ViewProperties viewProps) {
        try {
            Association assoc = dms.createAssociation(new AssociationModel(TOPIC_MAPCONTEXT,
                new TopicRoleModel(topicmapId, ROLE_TYPE_TOPICMAP),
                new TopicRoleModel(topicId,    ROLE_TYPE_TOPIC)
            ));
            storeViewProperties(assoc, viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Adding topic " + topicId + " to topicmap " + topicmapId + " failed " +
                "(viewProps=" + viewProps + ")", e);
        }
    }

    @Override
    public void addTopicToTopicmap(long topicmapId, long topicId, int x, int y, boolean visibility) {
        addTopicToTopicmap(topicmapId, topicId, new ViewProperties(x, y, visibility));
    }

    @POST
    @Path("/{id}/association/{assoc_id}")
    @Transactional
    @Override
    public void addAssociationToTopicmap(@PathParam("id") long topicmapId, @PathParam("assoc_id") long assocId) {
        dms.createAssociation(new AssociationModel(ASSOCIATION_MAPCONTEXT,
            new TopicRoleModel(topicmapId,    ROLE_TYPE_TOPICMAP),
            new AssociationRoleModel(assocId, ROLE_TYPE_ASSOCIATION)
        ));
    }

    // ---

    @PUT
    @Path("/{id}/topic/{topic_id}")
    @Transactional
    @Override
    public void setViewProperties(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                    ViewProperties viewProps) {
        try {
            storeViewProperties(topicmapId, topicId, viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Storing view properties of topic " + topicId + " failed " +
                "(viewProps=" + viewProps + ")", e);
        }
    }


    @PUT
    @Path("/{id}/topic/{topic_id}/{x}/{y}")
    @Transactional
    @Override
    public void setTopicPosition(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                   @PathParam("x") int x, @PathParam("y") int y) {
        storeViewProperties(topicmapId, topicId, new ViewProperties(x, y));
    }

    @PUT
    @Path("/{id}/topic/{topic_id}/{visibility}")
    @Transactional
    @Override
    public void setTopicVisibility(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                     @PathParam("visibility") boolean visibility) {
        storeViewProperties(topicmapId, topicId, new ViewProperties(visibility));
    }

    @DELETE
    @Path("/{id}/association/{assoc_id}")
    @Transactional
    @Override
    public void removeAssociationFromTopicmap(@PathParam("id") long topicmapId, @PathParam("assoc_id") long assocId) {
        fetchAssociationRefAssociation(topicmapId, assocId).delete();
    }

    // ---

    @PUT
    @Path("/{id}")
    @Transactional
    @Override
    public void setClusterPosition(@PathParam("id") long topicmapId, ClusterCoords coords) {
        for (ClusterCoords.Entry entry : coords) {
            setTopicPosition(topicmapId, entry.topicId, entry.x, entry.y);
        }
    }

    @PUT
    @Path("/{id}/translation/{x}/{y}")
    @Transactional
    @Override
    public void setTopicmapTranslation(@PathParam("id") long topicmapId, @PathParam("x") int transX,
                                                                         @PathParam("y") int transY) {
        try {
            ChildTopicsModel topicmapState = new ChildTopicsModel()
                .put("dm4.topicmaps.state", new ChildTopicsModel()
                    .put("dm4.topicmaps.translation", new ChildTopicsModel()
                        .put("dm4.topicmaps.translation_x", transX)
                        .put("dm4.topicmaps.translation_y", transY)));
            dms.updateTopic(new TopicModel(topicmapId, topicmapState));
        } catch (Exception e) {
            throw new RuntimeException("Setting translation of topicmap " + topicmapId + " failed (transX=" +
                transX + ", transY=" + transY + ")", e);
        }
    }

    // ---

    @Override
    public void registerTopicmapRenderer(TopicmapRenderer renderer) {
        logger.info("### Registering topicmap renderer \"" + renderer.getClass().getName() + "\"");
        topicmapRenderers.put(renderer.getUri(), renderer);
    }

    // ---

    @Override
    public void registerViewmodelCustomizer(ViewmodelCustomizer customizer) {
        logger.info("### Registering viewmodel customizer \"" + customizer.getClass().getName() + "\"");
        viewmodelCustomizers.add(customizer);
    }

    @Override
    public void unregisterViewmodelCustomizer(ViewmodelCustomizer customizer) {
        logger.info("### Unregistering viewmodel customizer \"" + customizer.getClass().getName() + "\"");
        if (!viewmodelCustomizers.remove(customizer)) {
            throw new RuntimeException("Unregistering viewmodel customizer failed (customizer=" + customizer + ")");
        }
    }

    // ---

    // Note: not part of topicmaps service
    @GET
    @Path("/{id}")
    @Produces("text/html")
    public InputStream getTopicmapInWebclient() {
        // Note: the path parameter is evaluated at client-side
        return invokeWebclient();
    }

    // Note: not part of topicmaps service
    @GET
    @Path("/{id}/topic/{topic_id}")
    @Produces("text/html")
    public InputStream getTopicmapAndTopicInWebclient() {
        // Note: the path parameters are evaluated at client-side
        return invokeWebclient();
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    // --- Fetch ---

    private Map<Long, TopicViewmodel> fetchTopics(Topic topicmapTopic, boolean includeChilds) {
        Map<Long, TopicViewmodel> topics = new HashMap();
        ResultList<RelatedTopic> relTopics = topicmapTopic.getRelatedTopics(TOPIC_MAPCONTEXT,
            "dm4.core.default", "dm4.topicmaps.topicmap_topic", null, 0);   // othersTopicTypeUri=null, maxResultSize=0
        if (includeChilds) {
            relTopics.loadChildTopics();
        }
        for (RelatedTopic topic : relTopics) {
            topics.put(topic.getId(), createTopicViewmodel(topic));
        }
        return topics;
    }

    private Map<Long, AssociationViewmodel> fetchAssociations(Topic topicmapTopic) {
        Map<Long, AssociationViewmodel> assocs = new HashMap();
        ResultList<RelatedAssociation> relAssocs = topicmapTopic.getRelatedAssociations(ASSOCIATION_MAPCONTEXT,
            "dm4.core.default", "dm4.topicmaps.topicmap_association", null);
        for (RelatedAssociation assoc : relAssocs) {
            assocs.put(assoc.getId(), new AssociationViewmodel(assoc.getModel()));
        }
        return assocs;
    }

    // ---

    private TopicViewmodel createTopicViewmodel(RelatedTopic topic) {
        try {
            ViewProperties viewProps = fetchViewProperties(topic.getRelatingAssociation());
            invokeViewmodelCustomizers(topic, viewProps);
            return new TopicViewmodel(topic.getModel(), viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Creating viewmodel for topic " + topic.getId() + " failed", e);
        }
    }

    // ---

    private Association fetchTopicRefAssociation(long topicmapId, long topicId) {
        return dms.getAssociation(TOPIC_MAPCONTEXT, topicmapId, topicId, ROLE_TYPE_TOPICMAP, ROLE_TYPE_TOPIC);
    }

    private Association fetchAssociationRefAssociation(long topicmapId, long assocId) {
        return dms.getAssociationBetweenTopicAndAssociation(ASSOCIATION_MAPCONTEXT, topicmapId, assocId,
            ROLE_TYPE_TOPICMAP, ROLE_TYPE_ASSOCIATION);
    }

    // ---

    private ViewProperties fetchViewProperties(Association mapcontextAssoc) {
        int x = (Integer) mapcontextAssoc.getProperty(PROP_X);
        int y = (Integer) mapcontextAssoc.getProperty(PROP_Y);
        boolean visibility = (Boolean) mapcontextAssoc.getProperty(PROP_VISIBILITY);
        return new ViewProperties(x, y, visibility);
    }

    // --- Store ---

    private void storeViewProperties(long topicmapId, long topicId, ViewProperties viewProps) {
        storeViewProperties(fetchTopicRefAssociation(topicmapId, topicId), viewProps);
    }

    private void storeViewProperties(Association mapcontextAssoc, ViewProperties viewProps) {
        for (String propUri : viewProps.propUris()) {
            mapcontextAssoc.setProperty(propUri, viewProps.get(propUri), false);    // addToIndex = false
        }
    }

    // --- Viewmodel Customizers ---

    private void invokeViewmodelCustomizers(RelatedTopic topic, ViewProperties viewProps) {
        for (ViewmodelCustomizer customizer : viewmodelCustomizers) {
            invokeViewmodelCustomizer(customizer, topic, viewProps);
        }
    }

    private void invokeViewmodelCustomizer(ViewmodelCustomizer customizer, RelatedTopic topic,
                                                                           ViewProperties viewProps) {
        try {
            customizer.enrichViewProperties(topic, viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Invoking viewmodel customizer for topic " + topic.getId() + " failed " +
                "(customizer=\"" + customizer.getClass().getName() + "\")", e);
        }
    }

    // --- Topicmap Renderers ---

    private TopicmapRenderer getTopicmapRenderer(String rendererUri) {
        TopicmapRenderer renderer = topicmapRenderers.get(rendererUri);
        //
        if (renderer == null) {
            throw new RuntimeException("\"" + rendererUri + "\" is an unknown topicmap renderer");
        }
        //
        return renderer;
    }

    // ---

    private InputStream invokeWebclient() {
        return dms.getPlugin("de.deepamehta.webclient").getStaticResource("/web/index.html");
    }
}
