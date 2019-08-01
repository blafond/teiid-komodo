package org.komodo.rest.relational.json;

import static org.komodo.rest.relational.json.KomodoJsonMarshaller.BUILDER;

import java.io.IOException;

import org.komodo.rest.relational.connection.RestSourceSchema;
import org.komodo.rest.relational.response.vieweditorstate.RestViewSourceSchema;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ViewSourceSchemaSerializer extends TypeAdapter<RestViewSourceSchema> {

    @Override
    public RestViewSourceSchema read(JsonReader in) throws IOException {
        return null;
    }

    @Override
    public void write(JsonWriter out, RestViewSourceSchema value) throws IOException {
        out.beginObject();
        
        out.name(RestViewSourceSchema.VIEW_ID_LABEL);
        out.value(value.getName());
        
        if (value.getSourceSchemas().length > 0) {
            out.name(RestViewSourceSchema.SCHEMAS_LABEL);
            BUILDER.toJson(value.getSourceSchemas(), RestSourceSchema[].class, out);
        }

        out.endObject();
    }
}
