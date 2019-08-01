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
import org.teiid.metadata.Table;

/**
 * Used to build a JSON representation for a schema node
 */
public class RestSourceTable implements KRestEntity {
    /**
     * Label for tables
     */
    public static final String COLUMNS_LABEL = "columns";

    /**
     * Label for name
     */
    public static final String NAME_LABEL = "tableName";
    
    /**
     * Label for sourcePath
     */
    public static final String SOURCE_PATH_LABEL = "sourcePath";
    
    /**
     * Label for fqn
     */
    public static final String FQN_LABEL = "fqn";
    
    
    private String fqn;
    
    private String name;
    
    private String sourcePath;
    
    private Table sourceTable;


    private List<RestSourceColumn> columns = new ArrayList<>();

    /**
     * Constructor for use when de-serializing
     */
    public RestSourceTable(String sourcePath, Table table) {
        super();
        this.sourcePath = sourcePath;
        this.sourceTable = table;
    }

//    public RestSourceTable(String name, String sourcePath, String fqn) {
//    	this.name = name;
//    	this.sourcePath = sourcePath;
//    	this.fqn = fqn;
//    }

    @Override
    public Object getXml() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
    }

    public String getName() {
    	return this.sourceTable.getName();
//        return this.name;
    }
    
    public String getFqn() {
    	return this.sourceTable.getFullName();
//        return this.fqn;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String path) {
        this.sourcePath = path;
    }
    
    public void addColumn(RestSourceColumn column) {
    	columns.add(column);
    }
    
    public RestSourceColumn[] getColumns() {
    	List<RestSourceColumn> columns = new ArrayList<RestSourceColumn>();
    	for( Column column : sourceTable.getColumns()) {
    		columns.add(new RestSourceColumn(column));
    	}
    	return columns.toArray(new RestSourceColumn[0]);
    }
}
