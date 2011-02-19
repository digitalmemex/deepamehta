package de.deepamehta.core.util;

import de.deepamehta.core.model.PluginInfo;
import de.deepamehta.core.model.RelatedTopic;
import de.deepamehta.core.model.Relation;
import de.deepamehta.core.model.Topic;
import de.deepamehta.core.model.TopicType;
import de.deepamehta.core.service.CoreService;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;



public class JSONHelper {

    private static Logger logger = Logger.getLogger("de.deepamehta.core.util.JSONHelper");

    // === Generic ===

    public static Map toMap(JSONObject o) {
        return toMap(o, new HashMap());
    }

    public static Map toMap(JSONObject o, Map map) {
        try {
            Iterator<String> i = o.keys();
            while (i.hasNext()) {
                String key = i.next();
                map.put(key, o.get(key));   // throws JSONException
            }
            return map;
        } catch (JSONException e) {
            throw new RuntimeException("Converting JSONObject to Map failed", e);
        }
    }

    // ---

    public static List toList(JSONArray o) {
        try {
            List list = new ArrayList();
            for (int i = 0; i < o.length(); i++) {
                list.add(o.get(i));         // throws JSONException
            }
            return list;
        } catch (JSONException e) {
            throw new RuntimeException("Converting JSONArray to List failed", e);
        }
    }

    // ---

    public static JSONArray stringsToJson(Set<String> strings) {
        JSONArray array = new JSONArray();
        for (String string : strings) {
            array.put(string);
        }
        return array;
    }

    // === DeepaMehta specific ===

    /**
     * Creates types and topics from a JSON formatted input stream.
     *
     * @param   migrationFileName   The origin migration file. Used for logging only.
     */
    public static void readMigrationFile(InputStream is, String migrationFileName, CoreService dms) {
        try {
            logger.info("Reading migration file \"" + migrationFileName + "\"");
            String fileContent = JavaUtils.readText(is);
            //
            JSONObject o = new JSONObject(fileContent);
            JSONArray types = o.optJSONArray("topic_types");
            if (types != null) {
                createTypes(types, dms);
            }
            JSONArray topics = o.optJSONArray("topics");
            if (topics != null) {
                createTopics(topics, dms);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Reading migration file \"" + migrationFileName + "\" failed", e);
        }
    }

    public static void createTypes(JSONArray types, CoreService dms) throws JSONException {
        for (int i = 0; i < types.length(); i++) {
            TopicType topicType = new TopicType(types.getJSONObject(i));
            dms.createTopicType(topicType.getProperties(), topicType.getDataFields(), null);    // clientContext=null
        }
    }

    public static void createTopics(JSONArray topics, CoreService dms) throws JSONException {
        for (int i = 0; i < topics.length(); i++) {
            Topic topic = new Topic(topics.getJSONObject(i));
            dms.createTopic(topic.typeUri, topic.getProperties(), null);                        // clientContext=null
        }
    }
}
