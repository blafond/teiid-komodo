package org.komodo.rest.relational.response.vieweditorstate;

import javax.ws.rs.core.MediaType;

import org.komodo.rest.KRestEntity;
import org.komodo.rest.relational.connection.RestSourceSchema;

public class RestViewSourceSchema implements KRestEntity {
	
	
    /**
     * Label for viewDefinitionName
     */
    public static final String VIEW_ID_LABEL = "viewDefinitionName";
    
    /**
     * Label for sourceSchemas
     */
    public static final String SCHEMAS_LABEL = "sourceSchemas";
    
    private String viewDefinitionName;
	
    /*
     * The view compositions
     */
    private RestSourceSchema[] sourceSchemas = new RestSourceSchema[0];
    
    /**
     * Constructor for use <strong>only</strong> when deserializing.
     */
    public RestViewSourceSchema(RestSourceSchema[] schema) {
    	setSourceSchemas(schema);
    }

	@Override
	public boolean supports(MediaType mediaType) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getXml() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setName(String name) {
		this.viewDefinitionName = name;
	}
	
	public String getName() {
		return this.viewDefinitionName;
	}
	
    /**
     * @return the projected columns
     */
    public RestSourceSchema[] getSourceSchemas() {
        return sourceSchemas;
    }
    
    /**
     * Set the projected columns
     * @param projCols the projected columns
     */
    public void setSourceSchemas(RestSourceSchema[] srcTables) {
        this.sourceSchemas = srcTables;
    }

}
