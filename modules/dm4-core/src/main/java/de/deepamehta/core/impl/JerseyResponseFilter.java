package de.deepamehta.core.impl;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationType;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.service.Directives;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.logging.Logger;



class JerseyResponseFilter implements ContainerResponseFilter {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private EmbeddedService dms;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    JerseyResponseFilter(EmbeddedService dms) {
        this.dms = dms;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
        try {
            dms.fireEvent(CoreEvent.SERVICE_RESPONSE_FILTER, response);
            //
            Object entity = response.getEntity();
            boolean includeChilds = getIncludeChilds(request);
            if (entity != null) {
                // 1) Loading child topics
                if (entity instanceof DeepaMehtaObject) {
                    loadChildTopics((DeepaMehtaObject) entity, includeChilds);
                } else if (isIterable(response, DeepaMehtaObject.class)) {
                    loadChildTopics((Iterable<DeepaMehtaObject>) entity, includeChilds);
                }
                // 2) Firing PRE_SEND events
                if (entity instanceof TopicType) {          // Note: must take precedence over topic
                    firePreSend((TopicType) entity);
                } else if (entity instanceof AssociationType) {
                    firePreSend((AssociationType) entity);  // Note: must take precedence over topic
                } else if (entity instanceof Topic) {
                    firePreSend((Topic) entity);
                } else if (entity instanceof Association) {
                    firePreSend((Association) entity);
                } else if (entity instanceof Directives) {
                    // Note: some plugins rely on the PRE_SEND event in order to enrich updated objects, others don't.
                    // E.g. the Access Control plugin must enrich updated objects with permission information.
                    // ### TODO: check if this is still required. Meanwhile permissions are not an enrichment anymore.
                    // ### Update: Yes, it is still required, e.g. by the Time plugin when enriching with timestamps.
                    firePreSend((Directives) entity);
                } else if (isIterable(response, TopicType.class)) {
                    firePreSendTopicTypes((Iterable<TopicType>) entity);
                } else if (isIterable(response, AssociationType.class)) {
                    firePreSendAssociationTypes((Iterable<AssociationType>) entity);
                } else if (isIterable(response, Topic.class)) {
                    firePreSendTopics((Iterable<Topic>) entity);
                // ### FIXME: for Iterable<Association> no PRE_SEND_ASSOCIATION events are fired
                }
            }
            //
            logger.fine("### Removing tread-local directives");
            Directives.remove();
            //
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Jersey response filtering failed", e);
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    // === Loading child topics ===

    private void loadChildTopics(DeepaMehtaObject object, boolean includeChilds) {
        if (includeChilds) {
            object.loadChildTopics();
        }
    }

    private void loadChildTopics(Iterable<DeepaMehtaObject> objects, boolean includeChilds) {
        if (includeChilds) {
            for (DeepaMehtaObject object : objects) {
                object.loadChildTopics();
            }
        }
    }

    // === Firing PRE_SEND events ===

    private void firePreSend(Topic topic) {
        dms.fireEvent(CoreEvent.PRE_SEND_TOPIC, topic);
    }

    private void firePreSend(Association assoc) {
        dms.fireEvent(CoreEvent.PRE_SEND_ASSOCIATION, assoc);
    }

    private void firePreSend(TopicType topicType) {
        dms.fireEvent(CoreEvent.PRE_SEND_TOPIC_TYPE, topicType);
    }

    private void firePreSend(AssociationType assocType) {
        dms.fireEvent(CoreEvent.PRE_SEND_ASSOCIATION_TYPE, assocType);
    }

    private void firePreSend(Directives directives) {
        for (Directives.Entry entry : directives) {
            switch (entry.dir) {
            case UPDATE_TOPIC:
                firePreSend((Topic) entry.arg);
                break;
            case UPDATE_ASSOCIATION:
                firePreSend((Association) entry.arg);
                break;
            case UPDATE_TOPIC_TYPE:
                firePreSend((TopicType) entry.arg);
                break;
            case UPDATE_ASSOCIATION_TYPE:
                firePreSend((AssociationType) entry.arg);
                break;
            }
        }
    }

    private void firePreSendTopics(Iterable<Topic> topics) {
        for (Topic topic : topics) {
            firePreSend(topic);
        }
    }

    private void firePreSendTopicTypes(Iterable<TopicType> topicTypes) {
        for (TopicType topicType : topicTypes) {
            firePreSend(topicType);
        }
    }

    private void firePreSendAssociationTypes(Iterable<AssociationType> assocTypes) {
        for (AssociationType assocType : assocTypes) {
            firePreSend(assocType);
        }
    }

    // === Helper ===

    private boolean isIterable(ContainerResponse response, Class elementType) {
        Type genericType = response.getEntityType();
        if (genericType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
            Class<?> type = response.getEntity().getClass();
            if (typeArgs.length == 1 && Iterable.class.isAssignableFrom(type) &&
                                           elementType.isAssignableFrom((Class) typeArgs[0])) {
                return true;
            }
        }
        return false;
    }

    private boolean getIncludeChilds(ContainerRequest request) {
        return Boolean.parseBoolean(request.getQueryParameters().getFirst("include_childs"));
    }
}
