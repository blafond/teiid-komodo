/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.komodo.rest.relational.response.vieweditorstate;

import org.komodo.rest.relational.connection.RestSourceSchema;

/**
 * Represents the configuration for the view's source info 
 */
public class RestViewSourceSchema {
    
    /*
     * The view name
     */
    private String viewName;
	
    /*
     * The array of source schemas
     */
    private RestSourceSchema[] schemas = new RestSourceSchema[0];
    
    /**
     * Constructor for use <strong>only</strong> when deserializing.
     */
    public RestViewSourceSchema(String viewId, RestSourceSchema[] sourceSchemas) {
    	this.viewName = viewId;
    	this.schemas = sourceSchemas;
    }
	
	public String getViewDefinitionName() {
		return this.viewName;
	}
	
    /**
     * @return the projected columns
     */
    public RestSourceSchema[] getSchemas() {
        return schemas;
    }
}
