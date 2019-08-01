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

package org.komodo.rest.relational.json;

import static org.komodo.rest.relational.json.KomodoJsonMarshaller.BUILDER;

import java.io.IOException;

import org.komodo.rest.relational.connection.RestSourceColumn;
import org.komodo.rest.relational.connection.RestSourceTable;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class SourceTableSerializer extends TypeAdapter<RestSourceTable> {

    @Override
    public RestSourceTable read(JsonReader in) throws IOException {
    	return null;
    }

    @Override
    public void write(JsonWriter out, RestSourceTable value) throws IOException {
        out.beginObject();

        out.name(RestSourceTable.FQN_LABEL);
        out.value(value.getFqn());
        
        out.name(RestSourceTable.NAME_LABEL);
        out.value(value.getName());
        
        if (value.getColumns().length > 0) {
            out.name(RestSourceTable.COLUMNS_LABEL);
            BUILDER.toJson(value.getColumns(), RestSourceColumn[].class, out);
        }

        out.endObject();
    }
}