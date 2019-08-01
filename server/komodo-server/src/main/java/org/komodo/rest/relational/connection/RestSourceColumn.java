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

import javax.ws.rs.core.MediaType;

import org.komodo.rest.KRestEntity;
import org.teiid.metadata.Column;

/**
 * Used to build a JSON representation for a schema node
 */
public class RestSourceColumn implements KRestEntity {


    /**
     * Label for name
     */
    public static final String NAME_LABEL = "columName";
    
    /**
     * Label for datatype
     */
    public static final String TYPE_LABEL = "type";
    
    private Column sourceColumn;

    /**
     * Constructor for use when deserializing
     */
    public RestSourceColumn(Column column) {
        super();
        this.sourceColumn = column;
    }

    @Override
    public Object getXml() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
    }

    public String getName() {
    	return this.sourceColumn.getName();
    }
    
    public String getDatatype() {
    	return this.sourceColumn.getRuntimeType();
    }
}
