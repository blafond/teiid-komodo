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
package org.komodo.rest.relational.connection;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.komodo.rest.KRestEntity;
import org.teiid.metadata.Column;
import org.teiid.metadata.Schema;
import org.teiid.metadata.Table;

/**
 * Used to build a JSON representation for a source schema
 */
public class RestSourceSchema implements KRestEntity {
	
    /**
     * Label for tables
     */
    public static final String TABLES_LABEL = "sourceTables";

    /**
     * Label for name
     */
    public static final String NAME_LABEL = "schemaName";
    
    private String name;
    
    private String sourcePath;
    
    private Schema sourceSchema;
    
    private List<RestSourceTable> tables = new ArrayList<>();

    /**
     * Constructor for use when deserializing
     */
    public RestSourceSchema(String sourcePath, Schema schema) {
        super();
        this.sourcePath = sourcePath;
        this.sourceSchema = schema;
    }

//    public RestSourceSchema(String name) {
//    	this.name = name;
//    }

    @Override
    public String getXml() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
    }

    public String getName() {
    	return this.sourceSchema.getName();
//        return this.name;
    }
    
    public RestSourceTable[] getTables() {
    	List<RestSourceTable> tables = new ArrayList<RestSourceTable>();
    	for( String key : sourceSchema.getTables().keySet()) {
    		Table nextTable = sourceSchema.getTables().get(key);
    		tables.add(new RestSourceTable(this.sourcePath, nextTable));
    	}

        return tables.toArray(new RestSourceTable[0]);
    }
    
    
//    public void addTable(RestSourceTable column) {
//    	tables.add(column);
//    }
}
