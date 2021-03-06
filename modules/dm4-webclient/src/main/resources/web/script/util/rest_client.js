/**
 * A REST client for the DeepaMehta Core Service.
 *
 * @param   config      Optional: an object with these properties:
 *              on_send_request     Optional: the callback invoked before a request is sent (a function). One argument
 *                                  is passed: the request, an object with these properties:
 *                                      method
 *                                      uri
 *                                      header
 *                                      data
 *              process_directives  Optional: the callback invoked to process the directives received from the server.
 *                                  (a function). One argument is passed: the directives (array of directive).
 */
function RESTClient(config) {

    // -------------------------------------------------------------------------------------------------- Public Methods



    // === Topics ===

    this.get_topic_by_id = function(topic_id, include_childs) {
        var params = new RequestParameter({include_childs: include_childs})
        return request("GET", "/core/topic/" + topic_id + params.to_query_string())
    }

    /**
     * Looks up a topic by exact property value.
     * If no such topic exists <code>null</code> is returned.
     * If more than one topic is found a runtime exception is thrown. FIXME: check this.
     * <p>
     * IMPORTANT: Looking up a topic this way requires the property to be indexed with indexing mode <code>KEY</code>.
     *
     * @return  the topic, or <code>null</code>.
     */
    this.get_topic_by_value = function(key, value, include_childs) {
        var params = new RequestParameter({include_childs: include_childs})
        return request("GET", "/core/topic/by_value/" + key + "/" + encodeURIComponent(value) +
            params.to_query_string())
    }

    this.get_topics_by_value = function(key, value, include_childs) {
        var params = new RequestParameter({include_childs: include_childs})
        return request("GET", "/core/topic/multi/by_value/" + key + "/" + encodeURIComponent(value) +
            params.to_query_string())
    }

    /**
     * @param   sort                Optional: Result sorting.
     *                              If evaluates to true the returned topics are sorted.
     * @param   max_result_size     Optional: Result limitation (a number).
     *                              If 0 or if not specified the result is not limited.
     *
     * @return  An object with 2 properties:
     *              "items"       - array of topics, possibly empty.
     *              "total_count" - result set size before limitation.
     */
    this.get_topics = function(type_uri, include_childs, sort, max_result_size) {
        var params = new RequestParameter({include_childs: include_childs, max_result_size: max_result_size})
        var result = request("GET", "/core/topic/by_type/" + type_uri + params.to_query_string())
        if (sort) {
            this.sort_topics(result.items)
        }
        return result
    }

    /**
     * @param   traversal_filter    Optional: Traversal Filtering.
     *                              An object with 4 possible properties (each one is optional):
     *                                  "assoc_type_uri"
     *                                  "my_role_type_uri"
     *                                  "others_role_type_uri"
     *                                  "others_topic_type_uri"
     *                              If not specified no filter is applied.
     * @param   sort                Optional: Result sorting.
     *                              If evaluates to true the returned topics are sorted.
     * @param   max_result_size     Optional: Result limitation (a number).
     *                              If 0 or if not specified the result is not limited.
     *
     * @return  An object with 2 properties:
     *              "items"       - array of topics, possibly empty.
     *              "total_count" - result set size before limitation.
     */
    this.get_topic_related_topics = function(topic_id, traversal_filter, sort, max_result_size) {
        var params = new RequestParameter(traversal_filter)
        params.add("max_result_size", max_result_size)
        var result = request("GET", "/core/topic/" + topic_id + "/related_topics" + params.to_query_string())
        if (sort) {
            this.sort_topics(result.items)
        }
        return result
    }

    this.search_topics = function(text, field_uri) {
        var params = new RequestParameter({search: text, field: field_uri})
        return request("GET", "/core/topic" + params.to_query_string())
    }

    this.create_topic = function(topic_model) {
        return request("POST", "/core/topic", topic_model)
    }

    this.update_topic = function(topic_model) {
        return request("PUT", "/core/topic/" + topic_model.id, topic_model)
    }

    this.delete_topic = function(id) {
        return request("DELETE", "/core/topic/" + id)
    }



    // === Associations ===

    this.get_association_by_id = function(assoc_id, include_childs) {
        var params = new RequestParameter({include_childs: include_childs})
        return request("GET", "/core/association/" + assoc_id + params.to_query_string())
    }

    /**
     * Returns the association between two topics, qualified by association type and both role types.
     * If no such association exists <code>null</code> is returned.
     * If more than one association exist, an exception is thrown.
     *
     * @param   assoc_type_uri  Association type filter.
     *
     * @return  The association (a JavaScript object).
     */
    this.get_association = function(assoc_type_uri, topic1_id, topic2_id, role_type1_uri, role_type2_uri,
                                                                                          include_childs) {
        var params = new RequestParameter({include_childs: include_childs})
        return request("GET", "/core/association/" + assoc_type_uri + "/" +  topic1_id + "/" + topic2_id + "/" +
            role_type1_uri + "/" + role_type2_uri + params.to_query_string())
    }

    /**
     * Returns the associations between two topics. If no such association exists an empty array is returned.
     *
     * @param   assoc_type_uri  Association type filter (optional).
     *                          Pass <code>null</code>/<code>undefined</code> to switch filter off.
     *
     * @return  An array of associations.
     */
    this.get_associations = function(topic1_id, topic2_id, assoc_type_uri) {
        return request("GET", "/core/association/multiple/" + topic1_id + "/" + topic2_id + "/" +
            (assoc_type_uri || ""))
    }

    /**
     * @param   traversal_filter    Optional: Traversal Filtering.
     *                              An object with 4 possible properties (each one is optional):
     *                                  "assoc_type_uri"
     *                                  "my_role_type_uri"
     *                                  "others_role_type_uri"
     *                                  "others_topic_type_uri"
     *                              If not specified no filter is applied.
     * @param   sort                Optional: Result sorting.
     *                              If evaluates to true the returned topics are sorted.
     * @param   max_result_size     Optional: Result limitation (a number).
     *                              If 0 or if not specified the result is not limited.
     *
     * @return  An object with 2 properties:
     *              "items"       - array of topics, possibly empty.
     *              "total_count" - result set size before limitation.
     */
    this.get_association_related_topics = function(assoc_id, traversal_filter, sort, max_result_size) {
        var params = new RequestParameter(traversal_filter)
        params.add("max_result_size", max_result_size)
        var result = request("GET", "/core/association/" + assoc_id + "/related_topics" + params.to_query_string())
        if (sort) {
            this.sort_topics(result.items)
        }
        return result
    }

    this.create_association = function(assoc_model) {
        return request("POST", "/core/association", assoc_model)
    }

    // ### TODO: remove stay_in_edit_mode parameter
    this.update_association = function(assoc_model, stay_in_edit_mode) {
        return request("PUT", "/core/association/" + assoc_model.id, assoc_model, undefined, undefined, undefined,
                                                                                  stay_in_edit_mode)
    }

    this.delete_association = function(id) {
        return request("DELETE", "/core/association/" + id)
    }



    // === Topic Types ===

    this.get_topic_type_uris = function() {
        return request("GET", "/core/topictype")
    }

    this.get_topic_type = function(type_uri) {
        return request("GET", "/core/topictype/" + type_uri)
    }

    this.get_all_topic_types = function(callback) {
        request("GET", "/core/topictype/all", undefined, callback)
    }

    this.create_topic_type = function(topic_type_model) {
        return request("POST", "/core/topictype", topic_type_model)
    }

    this.update_topic_type = function(topic_type_model) {
        return request("PUT", "/core/topictype", topic_type_model)
    }

    this.delete_topic_type = function(type_uri) {
        return request("DELETE", "/core/topictype/" + type_uri)
    }



    // === Association Types ===

    this.get_association_type_uris = function() {
        return request("GET", "/core/assoctype")
    }

    this.get_association_type = function(type_uri) {
        return request("GET", "/core/assoctype/" + type_uri)
    }

    this.get_all_association_types = function(callback) {
        request("GET", "/core/assoctype/all", undefined, callback)
    }

    this.create_association_type = function(assoc_type_model) {
        return request("POST", "/core/assoctype", assoc_type_model)
    }

    this.update_association_type = function(assoc_type_model) {
        return request("PUT", "/core/assoctype", assoc_type_model)
    }

    this.delete_association_type = function(type_uri) {
        return request("DELETE", "/core/assoctype/" + type_uri)
    }



    // === Role Types ===

    this.create_role_type = function(topic_model) {
        return request("POST", "/core/roletype", topic_model)
    }



    // === Plugins ===

    this.get_plugins = function() {
        return request("GET", "/core/plugin")
    }



    // === Plugin Support ===

    /**
     * Sends an AJAX request.
     *
     * A plugin uses this method to send a request to its REST service.
     * As an example see the DeepaMehta 4 Topicmaps plugin.
     */
    this.request = function(method, uri, data, callback, headers, response_data_type) {
        return request(method, uri, data, callback, headers, response_data_type)
    }

    /**
     * Helps with construction of the URI's query string part.
     *
     * This helper method might be useful for plugins which provides a REST service.
     * As an example see the DeepaMehta 4 Webclient plugin.
     */
    this.createRequestParameter = function(params) {
        return new RequestParameter(params)
    }

    this.sort_topics = function(topics) {
        topics.sort(function(topic_1, topic_2) {
            if (topic_1.type_uri != topic_2.type_uri) {
                // 1st sort criteria: topic type
                return compare(topic_1.type_uri, topic_2.type_uri)
            } else {
                // 2nd sort criteria: topic value
                return compare(topic_1.value.toString().toLowerCase(),
                               topic_2.value.toString().toLowerCase())
                // Note 1: toString() is required for non-string topic values
                // Note 2: to keep things simple we sort everything alphanumerically ### FIXME?
            }

            function compare(val_1, val_2) {
                return val_1 < val_2 ? -1 : val_1 == val_2 ? 0 : 1
            }
        })
    }



    // ----------------------------------------------------------------------------------------------- Private Functions

    /**
     * Sends an AJAX request.
     *
     * @param   method              The HTTP method: "GET", "POST", "PUT", "DELETE".
     * @patam   uri                 The request URI, including query parameters.
     * @param   data                Optional: the data to be sent to the server (an object). By default the data object
     *                              is serialized to JSON format. Note: key/value pairs with undefined values are not
     *                              serialized.
     *                              To use an alternate format set the Content-Type header (see "headers" parameter).
     * @param   callback            Optional: the function to be called if the request is successful. One argument is
     *                              passed: the data returned from the server.
     *                              If not specified, the request is send synchronously.
     * @param   headers             Optional: a map of additional header key/value pairs to send along with the request.
     * @param   response_data_type  Optional: affects the "Accept" header to be sent and controls the post-processing
     *                              of the response data. 2 possible values:
     *                                  "json" - the response data is parsed into a JavaScript object. The default.
     *                                  "text" - the response data is returned as is.
     *
     * @return  For successful synchronous requests: the data returned from the server. Otherwise undefined.
     *
     * ### TODO: remove stay_in_edit_mode parameter
     */
    function request(method, uri, data, callback, headers, response_data_type, stay_in_edit_mode) {
        var request = {
            method: method,
            uri: uri,
            headers: headers || {},
            data: data
        }
        if (config && config.on_send_request) {
            config.on_send_request(request)
        }
        //
        var async = callback != undefined
        var status          // used only for synchronous request: "success" if request was successful
        var response_data   // used only for synchronous successful request: the response data (response body)
        //
        var content_type = request.headers["Content-Type"] || "application/json"       // set default
        if (content_type == "application/json") {
            data = JSON.stringify(data)
        }
        //
        response_data_type = response_data_type || "json"
        //
        $.ajax({
            type: method,
            url: request.uri,
            contentType: content_type,
            headers: request.headers,
            data: data,
            dataType: response_data_type,
            processData: false,
            async: async
        })
        .done(function(data, text_status, jq_xhr) {
            if (callback) {
                callback(data)
            }
            response_data = data
        })
        .fail(function(jq_xhr, text_status, error_thrown) {
            // Note: since at least jQuery 2.0.3 an exception thrown from the "error" callback (as registered in the
            // $.ajax() settings object) does not reach the calling plugin. (In jQuery 1.7.2 it did.) Apparently the
            // exception is catched by jQuery. That's why we use the Promise style to register our callbacks (done(),
            // fail(), always()). An exception thrown from fail() does reach the calling plugin.
            throw "RESTClientError: " + method + " request failed (" + text_status + ": " + error_thrown + ")"
        })
        .always(function(dummy, text_status) {
            // Note: the signature of the always() callback varies. Depending on the response status it takes
            // shape either of the done() or the fail() callback.
            status = text_status
        })
        //
        if (!async && status == "success") {
            var directives = response_data.directives
            if (directives && config && config.process_directives) {
                // update client model and GUI
                config.process_directives(directives, stay_in_edit_mode)
                //
                return directives
            } else {
                return response_data
            }
        }
    }

    /**
     * @params      Optional: initial set of parameters
     *
     * ### TODO: rename to QueryParameter (to fit JAX-RS wording)
     */
    function RequestParameter(params) {

        var param_array = []

        if (params && !params.length) {
            for (var param_name in params) {
                add(param_name, params[param_name])
            }
        }

        this.add = function(param_name, value) {
            add(param_name, value)
        }

        this.add_list = function(param_name, value_list) {
            if (value_list) {
                for (var i = 0; i < value_list.length; i++) {
                    add(param_name, value_list[i])
                }
            }
        }

        this.to_query_string = function() {
            var query_string = encodeURI(param_array.join("&"))
            if (query_string) {
                query_string = "?" + query_string
            }
            return query_string
        }

        function add(param_name, value) {
            // Do not add null or undefined values.
            // On the other hand false *is* added.
            if (value == null || value == undefined) {
                return
            }
            //
            if (typeof(value) == "object") {
                value = JSON.stringify(value)
            }
            //
            param_array.push(param_name + "=" + value)
        }
    }
}
