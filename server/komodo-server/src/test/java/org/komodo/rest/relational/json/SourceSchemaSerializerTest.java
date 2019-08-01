package org.komodo.rest.relational.json;

import org.junit.Before;
import org.junit.Test;
import org.komodo.StringConstants;
import org.komodo.rest.relational.connection.RestSourceColumn;
import org.komodo.rest.relational.connection.RestSourceSchema;
import org.komodo.rest.relational.connection.RestSourceTable;
import org.komodo.rest.relational.response.vieweditorstate.RestViewSourceSchema;
import org.teiid.metadata.Column;
import org.teiid.metadata.Schema;
import org.teiid.metadata.Table;

public class SourceSchemaSerializerTest implements StringConstants {
	
	private String schemaName = "partssupplier";

    private String[] tables = { "parts", "supplier_parts", "ship_via", "status", "supplier" };

    private Object[][] columns = {
        { "PART_ID", "PART_NAME", "PART_COLOR", "PART_WEIGHT" },
        { "SUPPLIER_ID", "PART_ID", "QUANTITY", "SHIPPER_ID" },
        { "SHIPPER_ID", "SHIPPER_NAME" },
        { "STATUS_ID", "STATUS_NAME" },
        { "SUPPLIER_ID", "SUPPLIER_NAME", "SUPPLIER_STATUS", "SUPPLIER_CITY", "SUPPLIER_STATE" },
    };
    
/* PARTIAL JSON EXAMPLE
{ 
	"schemaName": "partssupplier",
    "tables": [
    	{
    		"tableName": "parts",
    		"columns": [
		        { 
		        	"columnName": "PART_ID" 
		        }, 
		        { 
		        	"columnName": "PART_NAME", 
		        },
		        { 
		        	"columnName": "PART_COLOR", 
		        },
		        { 
		        	"columnName": "PART_WEIGHT" 
		        }
		    ]
		},
		{
    		"tableName": "status",
    		"columns": [
		        {
		         	"columnName": "STATUS_ID" 
		        },
		        {
		         	"columnName": "STATUS_NAME" 
		        }
		    ]
		},
	}
} 
*/
    
    RestViewSourceSchema rvss;

    private String JSON;

    private String tab(String text, int num) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < num; ++i)
            buf.append(SPACE).append(SPACE);

        buf.append(text);
        return buf.toString();
    }

    private String tab(String text) {
        return tab(text, 1);
    }
    
    private Schema createSchema(String name, int nTables) {
    	Schema newSchema = new Schema();
    	newSchema.setName(name);
    	
    	for( int i=0; i< nTables; i++ ) {
	    	Table newTable = new Table();
	    	newTable.setName("table_" + i);
	    	
	    	for( int j=0; j<4; j++ ) {
	    		Column newColumn = new Column();
	    		newColumn.setName("column_" + j);
	    		newColumn.setRuntimeType("string");
	    		newTable.addColumn(newColumn);
	    	}

	    	newSchema.addTable(newTable);
    	}
    	
    	return newSchema;
    }
    
    private RestSourceSchema createSourceSchema(Schema schema, String sourcePath) {
    	RestSourceSchema rss = new RestSourceSchema(sourcePath, schema); //.getName());
    	
//    	for(String key: schema.getTables().keySet()) {
//    		Table table = schema.getTables().get(key);
//    		RestSourceTable rst = new RestSourceTable(sourcePath, table);//table.getName(), sourcePath, table.getFullName());
//    		
//    		for(Column nextCol: table.getColumns()) {
//    			RestSourceColumn rsc = new RestSourceColumn(nextCol);
////    			RestSourceColumn rsc = new RestSourceColumn(nextCol.getName(), nextCol.getRuntimeType());
//    			rst.addColumn(rsc);
//    		}
////    		rss.addTable(rst);
//    	}
    	
    	return rss;
    }

    @Before
    public void init() throws Exception {
    	Schema schema1 = createSchema("schema_1", 4);
    	Schema schema2 = createSchema("schema_2", 3);
    	
    	RestSourceSchema rss1 = createSourceSchema(schema1, "connection=PostgresDB/schema=sampledb/table=winelist");
    	RestSourceSchema rss2 = createSourceSchema(schema2, "connection=SampleWinesDB/schema=public/table=wineinfo");
    	
    	RestSourceSchema[] schemas = {rss1, rss2};
    	
    	rvss = new RestViewSourceSchema(schemas);

//        StringBuffer jsonBuf = new StringBuffer(OPEN_BRACE).append(NEW_LINE);
//        // 	"name": "partssupplier",
//        jsonBuf.append(tab("\"" + RestSourceSchema.NAME_LABEL + "\"")).append(COLON)
//                        .append(SPACE)
//                        .append(SPEECH_MARK)
//                        .append(schemaName)
//                        .append(SPEECH_MARK)
//                        .append(COMMA)
//                        .append(NEW_LINE);

        /*
    "tables": [
    	{
    		"name": "parts",
    		"columns": [

         */
//        jsonBuf.append(tab("\"" + RestSourceSchema.TABLES_LABEL + "\"")).append(COLON)
//        .append(SPACE)
//        .append(OPEN_SQUARE_BRACKET)
//        .append(NEW_LINE);
//        
//        // LOOP ON TABLES
//        for (int i = 0; i < tables.length; ++i) {
//            String tableName = tables[i];
//            RestSourceTable newTable = new RestSourceTable();
////            newTable.setName(tableName);
//            jsonBuf.append(tab(OPEN_BRACE, 2)).append(NEW_LINE)
//                                .append(tab("\"" + RestSourceTable.NAME_LABEL + "\"", 3))
//                                    .append(COLON)
//                                    .append(SPACE)
//                                    .append(SPEECH_MARK)
//                                    .append(tableName)
//                                    .append(SPEECH_MARK)
//                                    .append(COMMA)
//                                    .append(NEW_LINE);
//            
//            jsonBuf.append(tab("\"" + RestSourceTable.COLUMNS_LABEL + "\"", 3)).append(COLON)
//	            .append(SPACE)
//	            .append(OPEN_SQUARE_BRACKET)
//	            .append(NEW_LINE);
//            
//            // LOOP ON COLUMNS
//            for( int j = 0; j < columns[i].length; j++) {
//            	RestSourceColumn newColumn = new RestSourceColumn();
////            	newColumn.setName((String)columns[i][j]);
//            	jsonBuf.append(tab(OPEN_BRACE, 4)).append(NEW_LINE)
//                .append(tab("\"" + RestSourceColumn.NAME_LABEL + "\"", 5))
//                .append(COLON)
//                .append(SPACE)
//                .append(SPEECH_MARK)
//                .append(columns[i][j])
//                .append(SPEECH_MARK)
//                .append(NEW_LINE)
//            	.append(tab(CLOSE_BRACE, 4));
//            	if( j < columns[i].length-1) {
//            		jsonBuf.append(COMMA).append(NEW_LINE);
//            	}
//            	newTable.addColumn(newColumn);
//            }
//            jsonBuf.append(NEW_LINE).append(tab(CLOSE_SQUARE_BRACKET, 3)).append(NEW_LINE);
//            
//            jsonBuf.append(tab(CLOSE_BRACE, 2));
//        	if( i < tables.length-1) {
//        		jsonBuf.append(COMMA).append(NEW_LINE);
//        	}

//            inputSourceSchema.addTable(newTable);
//        }

//        jsonBuf.append(NEW_LINE).append(tab(CLOSE_SQUARE_BRACKET)).append(NEW_LINE);
        
//        jsonBuf.append(tab(CLOSE_BRACE, 0));
        
//        JSON = jsonBuf.toString();
    }

    @Test
    public void shouldExportResult() throws Exception {
        
        String json = KomodoJsonMarshaller.marshall( this.rvss );
        
        System.out.println("SourceSchemaSerializerTest >>> Generated json = \n " + json);
//        assertEquals(JSON, json);
    }

    @Test
    public void shouldImportResult() throws Exception {
//        RestSourceSchema sourceSchema = KomodoJsonMarshaller.unmarshall( JSON, RestSourceSchema.class );
//        assertEquals(sourceSchema.getTables().length, inputSourceSchema.getTables().length);
//        assertEquals(rowsData.length, queryResult.getRows().length);
    }
}

