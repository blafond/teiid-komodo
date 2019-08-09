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
package org.komodo.rest.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.komodo.KException;
import org.komodo.StringConstants;
import org.komodo.WorkspaceManager;
import org.komodo.datasources.DefaultSyndesisDataSource;
import org.komodo.datavirtualization.DataVirtualization;
import org.komodo.datavirtualization.SourceSchema;
import org.komodo.datavirtualization.ViewDefinition;
import org.komodo.metadata.DeployStatus;
import org.komodo.metadata.MetadataInstance;
import org.komodo.metadata.TeiidDataSource;
import org.komodo.metadata.TeiidVdb;
import org.komodo.metadata.internal.DefaultMetadataInstance;
import org.komodo.metadata.internal.TeiidVdbImpl;
import org.komodo.metadata.query.QSResult;
import org.komodo.openshift.BuildStatus;
import org.komodo.openshift.PublishConfiguration;
import org.komodo.openshift.TeiidOpenShiftClient;
import org.komodo.rest.AuthHandlingFilter.OAuthCredentials;
import org.komodo.rest.KomodoRestV1Application.V1Constants;
import org.komodo.rest.KomodoService;
import org.komodo.rest.ResourceNotFound;
import org.komodo.rest.datavirtualization.KomodoQueryAttribute;
import org.komodo.rest.datavirtualization.KomodoStatusObject;
import org.komodo.rest.datavirtualization.PublishRequestPayload;
import org.komodo.rest.datavirtualization.RelationalMessages;
import org.komodo.rest.datavirtualization.RestSyndesisSourceStatus;
import org.komodo.rest.datavirtualization.RestViewSourceInfo;
import org.komodo.rest.datavirtualization.connection.RestSchemaNode;
import org.komodo.rest.datavirtualization.connection.RestSourceSchema;
import org.komodo.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.teiid.adminapi.Model.Type;
import org.teiid.adminapi.VDBImport;
import org.teiid.adminapi.impl.ModelMetaData;
import org.teiid.adminapi.impl.VDBImportMetadata;
import org.teiid.adminapi.impl.VDBMetaData;
import org.teiid.metadata.AbstractMetadataRecord;
import org.teiid.metadata.Schema;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
/**
 * A Komodo REST service for obtaining information from a metadata instance.
 */
@Component
@Path( V1Constants.METADATA_SEGMENT )
@Api( tags = {V1Constants.METADATA_SEGMENT} )
public class KomodoMetadataService extends KomodoService implements ServiceVdbGenerator.SchemaFinder {

    private static final String CONNECTION_VDB_SUFFIX = "btlconn"; //$NON-NLS-1$

    /**
     * fqn table option key
     */
    public final static String TABLE_OPTION_FQN = AbstractMetadataRecord.RELATIONAL_URI+"fqn"; //$NON-NLS-1$

    @Autowired
    private TeiidOpenShiftClient openshiftClient;
    
    @Autowired
    private MetadataInstance metadataInstance;

    private MetadataInstance getMetadataInstance() throws KException {
        return metadataInstance;
    }

    /**
     * Does not need to be transactional as it only affects the runtime instance
     */
	public KomodoStatusObject removeVdb(final String vdbName) throws KException {
		getMetadataInstance().undeployDynamicVdb(vdbName);

		String title = RelationalMessages.getString(RelationalMessages.Info.VDB_DEPLOYMENT_STATUS_TITLE);
		KomodoStatusObject status = new KomodoStatusObject(title);

		if (getMetadataInstance().getVdb(vdbName) == null) {
		    status.addAttribute(vdbName,
		                        RelationalMessages.getString(RelationalMessages.Info.VDB_SUCCESSFULLY_UNDEPLOYED));
		} else
		    status.addAttribute(vdbName,
		                        RelationalMessages.getString(RelationalMessages.Info.VDB_UNDEPLOYMENT_REQUEST_SENT));
		return status;
	}

	public KomodoStatusObject refreshPreviewVdb(final String vdbName, SecurityPrincipal principal)
			throws KException, Exception {
		return runInTransaction(principal, "refreshPreviewVdb", false, () -> {
			TeiidVdb previewVdb = getMetadataInstance().getVdb(vdbName);
			VDBMetaData workingCopy = new VDBMetaData();
			workingCopy.setName(vdbName);
			
			// if workspace does not have preview vdb, then create it.
			if (previewVdb == null ) {
				previewVdb = new TeiidVdbImpl(workingCopy);
			}
	
			// Get the list of current preview VDB import names
			List<String> currentVdbImportNames = new ArrayList<String>();
			List<? extends VDBImport> currentVdbImports = previewVdb.getImports();
			for( VDBImport vdbImport: currentVdbImports ) {
				currentVdbImportNames.add(vdbImport.getName());
			}
	
			// Get the current workspace connection VDB names
			List<String> connectionVdbNames = new ArrayList<String>();
			Collection<String> vdbNames = getMetadataInstance().getVdbNames();
			for( String name: vdbNames) {
				if (name.endsWith(CONNECTION_VDB_SUFFIX)) {
					connectionVdbNames.add(name);
				}
			}
	
			// Add import for connectionVdb if it is missing
			boolean importAdded = false;
			for(String connVdbName: connectionVdbNames) {
				if(!currentVdbImportNames.contains(connVdbName)) {
					VDBImportMetadata vdbImport = new VDBImportMetadata();
					vdbImport.setVersion(DefaultMetadataInstance.DEFAULT_VDB_VERSION);
					vdbImport.setName(connVdbName);
					workingCopy.getVDBImports().add(vdbImport);
					importAdded = true;
				}
			}
	
			// Remove extra imports
			boolean importRemoved = false;
			for(String currentVdbImportName: currentVdbImportNames) {
				if(!connectionVdbNames.contains(currentVdbImportName)) {
					importRemoved = true;
					break;
				}
			}
	
			// check if there is a VDB already deployed in the instance
			TeiidVdb vdb = getMetadataInstance().getVdb(previewVdb.getName());
			 
			// The updated VDB is deployed if imports were added or removed
			if(vdb == null || importAdded || importRemoved) {
			    //
			    // Deploy the VDB
			    //
			    DeployStatus deployStatus = getMetadataInstance().deploy(workingCopy);
	
			    String title = RelationalMessages.getString(RelationalMessages.Info.VDB_DEPLOYMENT_STATUS_TITLE);
			    KomodoStatusObject status = new KomodoStatusObject(title);
	
			    List<String> progressMessages = deployStatus.getProgressMessages();
			    for (int i = 0; i < progressMessages.size(); ++i) {
			        status.addAttribute("ProgressMessage" + (i + 1), progressMessages.get(i)); //$NON-NLS-1$
			    }
	
			    if (deployStatus.ok()) {
			        status.addAttribute("deploymentSuccess", Boolean.TRUE.toString()); //$NON-NLS-1$
			        status.addAttribute(previewVdb.getName(),
			                            RelationalMessages.getString(RelationalMessages.Info.VDB_SUCCESSFULLY_DEPLOYED));
			    } else {
			        status.addAttribute("deploymentSuccess", Boolean.FALSE.toString()); //$NON-NLS-1$
			        List<String> errorMessages = deployStatus.getErrorMessages();
			        for (int i = 0; i < errorMessages.size(); ++i) {
			            status.addAttribute("ErrorMessage" + (i + 1), errorMessages.get(i)); //$NON-NLS-1$
			        }
	
			        status.addAttribute(previewVdb.getName(),
			                            RelationalMessages.getString(RelationalMessages.Info.VDB_DEPLOYED_WITH_ERRORS));
			    }
	
			    return status;
			} else {
				KomodoStatusObject kso = new KomodoStatusObject("Preview VDB Status"); //$NON-NLS-1$
				kso.addAttribute(vdbName, "No refresh required"); //$NON-NLS-1$
	
				return kso;
			}
		});
	}

    /**
     * Query the teiid server
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param queryAttribute
     *        the query attribute (never <code>null</code>)
     * @return a JSON representation of the Query results (never <code>null</code>)
     * @throws KException 
     */
    @SuppressWarnings( "nls" )
    @POST
    @Path(V1Constants.QUERY_SEGMENT)
    @Produces( MediaType.APPLICATION_JSON )
    @Consumes ( { MediaType.APPLICATION_JSON } )
    @ApiOperation(value = "Pass a query to the teiid server")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 400, message = "An error has occurred.")
    })
    public Response query(final @Context HttpHeaders headers,
                                   final @Context UriInfo uriInfo,
                                   @ApiParam(
                                             value = "" +
                                                     "JSON of the properties of the query:<br>" +
                                                     OPEN_PRE_TAG +
                                                     OPEN_BRACE + BR +
                                                     NBSP + "query: \"SQL formatted query to interrogate the target\"" + COMMA + BR +
                                                     NBSP + "target: \"The name of the target to be queried\"" + BR +
                                                     NBSP + OPEN_PRE_CMT + "(The target can be a vdb or data service. If the latter " +
                                                     NBSP + "then the name of the service vdb is extracted and " +
                                                     NBSP + "replaces the data service)" + CLOSE_PRE_CMT + COMMA + BR +
                                                     NBSP + "limit: Add a limit on number of results to be returned" + COMMA + BR +
                                                     NBSP + "offset: The index of the result to begin the results with" + BR +
                                                     CLOSE_BRACE +
                                                     CLOSE_PRE_TAG,
                                             required = true
                                   )
                                   final KomodoQueryAttribute kqa) throws KException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        //
        // Error if there is no query attribute defined
        //
        if (kqa.getQuery() == null) {
            return createErrorResponseWithForbidden(RelationalMessages.Error.METADATA_SERVICE_QUERY_MISSING_QUERY);
        }

        if (kqa.getTarget() == null) {
            return createErrorResponseWithForbidden(RelationalMessages.Error.METADATA_SERVICE_QUERY_MISSING_TARGET);
        }

        String target = kqa.getTarget();
        String query = kqa.getQuery();

        String vdbName = target;

        TeiidVdb vdb = getMetadataInstance().getVdb(vdbName);
        if (vdb == null) {
			return createErrorResponse(Status.BAD_REQUEST, RelationalMessages.Error.METADATA_SERVICE_QUERY_TARGET_NOT_DEPLOYED);
        }

        LOGGER.debug("Establishing query service for query {0} on vdb {1}", query, vdbName);
        QSResult result = getMetadataInstance().query(vdbName, query, kqa.getOffset(), kqa.getLimit());

        return toResponse(result);
    }

    /**
     * Initiate schema refresh for a syndesis source.
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param komodoSourceName
     *        the syndesis source name (cannot be empty)
     * @return a JSON representation of the refresh status (never <code>null</code>)
     * @throws Exception 
     * @throws  
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.REFRESH_SCHEMA_SEGMENT + StringConstants.FORWARD_SLASH + V1Constants.KOMODO_SOURCE_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Initiate schema refresh for a syndesis source")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response refreshSchema( final @Context HttpHeaders headers,
                                   final @Context UriInfo uriInfo,
                                   @ApiParam( value = "Name of the komodo source",
                                              required = true )
                                   final @PathParam( "komodoSourceName" ) String komodoSourceName,
                                   @ApiParam( value = "Indicates that the source vdb should be deployed, existing metadata will not be deleted",
                                              required = false )
                                   @DefaultValue( "true" )
                                   @QueryParam( "deployOnly" )
                                   final boolean redeployServerVdb ) throws Exception {
        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        // Error if the syndesisSource is missing
        if (StringUtils.isBlank( komodoSourceName )) {
            return createErrorResponseWithForbidden(RelationalMessages.Error.CONNECTION_SERVICE_MISSING_CONNECTION_NAME);
        }

        final KomodoStatusObject kso = refreshSchema(komodoSourceName, redeployServerVdb, principal);

        return toResponse(kso);
    }

	public KomodoStatusObject refreshSchema(final String komodoName, final boolean deployOnly, SecurityPrincipal principal) throws KException, Exception {
		return runInTransaction(principal, "refreshSchema?deployOnly=" + deployOnly, false, () -> {// Find the bound teiid source corresponding to the syndesis source
			TeiidDataSource teiidSource = getMetadataInstance().getDataSource(komodoName);

			if (teiidSource == null)
			    return null;

			final KomodoStatusObject kso = new KomodoStatusObject( "Refresh schema" ); //$NON-NLS-1$

			if (!deployOnly) {
				final WorkspaceManager mgr = kengine.getWorkspaceManager();
	            
	            mgr.deleteSchema(teiidSource.getId());
			}

			// Initiate the VDB deployment
		    doDeploySourceVdb(teiidSource);
		    kso.addAttribute(komodoName, "Delete workspace VDB, recreate, redeploy, and generated schema"); //$NON-NLS-1$
		    saveSchema(teiidSource.getId(), komodoName);
			return kso;
		});
	}
	
	public boolean deleteSchema(String schemaId, SecurityPrincipal principal) throws Exception {
		return runInTransaction(principal, "deleteSchema", false, () -> {
			final WorkspaceManager mgr = kengine.getWorkspaceManager();
            
            return mgr.deleteSchema(schemaId);
		});
	}

	private void saveSchema(final String schemaId, final String komodoName) throws KException {
		final String schemaModelName = getSchemaModelName( komodoName );

		final String sourceVdbName = getWorkspaceSourceVdbName( komodoName );
		
		final String modelDdl = getMetadataInstance().getSchema( sourceVdbName, schemaModelName ); //$NON-NLS-1$
		
		if (modelDdl != null) {
			getWorkspaceManager().createOrUpdateSchema(schemaId, komodoName, modelDdl);
		}
	}

    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param komodoSourceName
     *        the name of the komodoSource whose tables are being requested (cannot be empty)
     * @return the JSON representation of the tables collection (never <code>null</code>)
     * @throws Exception 
     */
    @GET
    @Path( "{komodoSourceName}/schema" )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get the native schema for the komodo source",
                   response = RestSchemaNode[].class )
    @ApiResponses( value = {
        @ApiResponse( code = 403, message = "An error has occurred." ),
        @ApiResponse( code = 404, message = "No komodo source could be found with the specified name" ),
        @ApiResponse( code = 406, message = "Only JSON is returned by this operation" )
    } )
    public Response getSchema( @Context final HttpHeaders headers,
                               final @Context UriInfo uriInfo,
                               @ApiParam( value = "Name of the komodo source",
                                          required = true )
                               @PathParam( "komodoSourceName" )
                               final String komodoSourceName ) throws Exception {
        final SecurityPrincipal principal = checkSecurityContext( headers );

        if ( principal.hasErrorResponse() ) {
            return principal.getErrorResponse();
        }

    	return runInTransaction(principal, "getSchema?komodoSourceName=" + komodoSourceName, true, ()->{
            return getSchema(komodoSourceName); 
    	}); 
    }

	Response getSchema(final String komodoSourceName) throws KException {
		// Find the bound teiid source corresponding to the syndesis source
		TeiidDataSource teiidSource = getMetadataInstance().getDataSource(komodoSourceName);

		if (teiidSource == null) {
			LOGGER.debug( "Connection '{0}' was not found", komodoSourceName ); //$NON-NLS-1$
		    return toResponse(new ResourceNotFound( komodoSourceName ) );
		}
		
		Schema schemaModel = findSchemaModel( teiidSource );

		List<RestSchemaNode> schemaNodes = Collections.emptyList();
		if ( schemaModel != null ) {
		    schemaNodes = this.generateSourceSchema(komodoSourceName, schemaModel.getTables().values());
		}

		return toResponse(schemaNodes);
	}

    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @return the JSON representation of the schema collection (never <code>null</code>)
     * @throws Exception 
     */
    @GET
    @Path( "connection-schema" )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get the native schema for all syndesis sources",
                   response = RestSchemaNode[].class )
    @ApiResponses( value = {
        @ApiResponse( code = 403, message = "An error has occurred." ),
        @ApiResponse( code = 404, message = "No results found" ),
        @ApiResponse( code = 406, message = "Only JSON is returned by this operation" )
    } )
    public Response getAllConnectionSchema( @Context final HttpHeaders headers,
                                            final @Context UriInfo uriInfo ) throws Exception {
        final SecurityPrincipal principal = checkSecurityContext( headers );

        if ( principal.hasErrorResponse() ) {
            return principal.getErrorResponse();
        }

        return runInTransaction(principal, "getAllConnectionSchema", true, ()->{
            List<RestSchemaNode> rootNodes = new ArrayList<RestSchemaNode>();
            
            // Get syndesis sources
            Collection<DefaultSyndesisDataSource> dataSources = this.openshiftClient.getSyndesisSources(getAuthenticationToken());

            // Get teiid datasources
            Collection<TeiidDataSource> allTeiidSources = getMetadataInstance().getDataSources();
            
            Map<String, TeiidDataSource> teiidSourceMap = allTeiidSources.stream().collect(Collectors.toMap(t -> t.getName(), Function.identity()));

            // Add status summary for each of the syndesis sources.  Determine if there is a matching teiid source
            for (DefaultSyndesisDataSource dataSource : dataSources) {
            	String komodoName = dataSource.getKomodoName();
            	TeiidDataSource teiidSource = teiidSourceMap.get(komodoName);
            	if (teiidSource == null) {
            		continue;
            	}
                final Schema schemaModel = findSchemaModel( teiidSource );

                List<RestSchemaNode> schemaNodes = null;
                if ( schemaModel != null ) {
                    schemaNodes = this.generateSourceSchema(komodoName, schemaModel.getTables().values());
                    if(schemaNodes != null && !schemaNodes.isEmpty()) {
                    	RestSchemaNode rootNode = new RestSchemaNode();
                    	rootNode.setName(komodoName);
                    	rootNode.setType("root");
                    	for(RestSchemaNode sNode: schemaNodes) {
                    		rootNode.addChild(sNode);
                    	}
                    	rootNodes.add(rootNode);
                    }
                }
            }

            return toResponse(rootNodes ); 
        });
    }

    /**
     * Get status for the available syndesis sources.
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @return a JSON document representing the statuses of the sources (never <code>null</code>)
     * @throws Exception 
     */
    @GET
    @Path(V1Constants.SYNDESIS_SOURCE_STATUSES)
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Return the syndesis source statuses",
                  response = RestSyndesisSourceStatus[].class)
    @ApiResponses(value = {
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getSyndesisSourceStatuses( final @Context HttpHeaders headers,
                                               final @Context UriInfo uriInfo ) throws Exception {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        final List< RestSyndesisSourceStatus > statuses = new ArrayList<>();

    	return runInTransaction(principal, "getSyndesisSourceStatuses", true, ()->{
            // Get syndesis sources
            Collection<DefaultSyndesisDataSource> dataSources = this.openshiftClient.getSyndesisSources(getAuthenticationToken());

            // Add status summary for each of the syndesis sources.  Determine if there is a matching teiid source
            for (DefaultSyndesisDataSource dataSource : dataSources) {
            	String komodoName = dataSource.getKomodoName();
            	if (komodoName == null) {
            		continue;
            	}
                statuses.add(createSourceStatus(dataSource));
            }
            
            LOGGER.debug( "getSyndesisSourceStatuses '{0}' statuses", statuses.size() ); //$NON-NLS-1$
            return toResponse(statuses );
    	});
    }
    
	public RestSyndesisSourceStatus getSyndesisSourceStatus(final DefaultSyndesisDataSource sds, SecurityPrincipal principal) throws Exception {
		return runInTransaction(principal, "getSyndesisSourceStatusByName", true, () -> {
            return createSourceStatus(sds);
		});
	}

	private RestSyndesisSourceStatus createSourceStatus(final DefaultSyndesisDataSource sds)
			throws KException, Exception {
		String komodoName = sds.getKomodoName();
		if (komodoName == null) {
			return null;
		}
		TeiidDataSource teiidSource = getMetadataInstance().getDataSource(komodoName);
		RestSyndesisSourceStatus status = new RestSyndesisSourceStatus(komodoName);
		if (teiidSource != null) {
			status.setHasTeiidSource(true);
		}

		// Name of vdb based on source name
		String vdbName = getWorkspaceSourceVdbName(komodoName);
		TeiidVdb teiidVdb = getMetadataInstance().getVdb(vdbName);
		if (teiidVdb != null) {
			status.setTeiidVdbDetails(teiidVdb);
		}

		// For each syndesis source, set the schema availability status
		setSchemaStatus(teiidSource.getId(), status);
		return status;
	}    

	/**
	 * Find and return table column info for input source paths from view definition
	 * 
	 * @param headers
	 *            the request headers (never <code>null</code>)
	 * @param uriInfo
	 *            the request URI information (never <code>null</code>)
	 * @return source schema object array
	 * @throws Exception 
	 */
	@POST
	@Produces( MediaType.APPLICATION_JSON )
	@Path(V1Constants.VIEW_SOURCE_INFO + FORWARD_SLASH + V1Constants.VIEW_EDITOR_STATE_PLACEHOLDER)
	@ApiOperation(value = "Get Source Schema for View Definition", response = RestViewSourceInfo.class)
	@ApiResponses(value = { @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
			@ApiResponse(code = 403, message = "An error has occurred.") })
	public Response viewSourceInfo(final @Context HttpHeaders headers, final @Context UriInfo uriInfo,
			@ApiParam(value = "Name of the view editor state", required = true) final @PathParam("viewEditorStateId") String viewEditorStateId) throws Exception {
		SecurityPrincipal principal = checkSecurityContext(headers);
		if (principal.hasErrorResponse())
			return principal.getErrorResponse();

		LOGGER.info("getViewSourceSchemas()   viewEditorStateId : " + viewEditorStateId);

		if (StringUtils.isBlank(viewEditorStateId)) {
			return createErrorResponseWithForbidden(RelationalMessages.Error.PROFILE_EDITOR_STATE_MISSING_NAME);
		}

		return runInTransaction(principal, "getViewSourceSchemas", true, ()->{
			ViewDefinition viewDefinition = this.getWorkspaceManager().findViewDefinition(viewEditorStateId);

			List<String> sourcePaths = viewDefinition.getSourcePaths();

			List<RestSourceSchema> srcSchemas = new ArrayList<RestSourceSchema>();

			for (int i = 0; i < sourcePaths.size(); i++) {
				String nextPath = sourcePaths.get(i);
				// Example sourcePath >>>> sourcePaths[0] =
				// "connection=conn1/schema=public/table=customer";
				StringTokenizer tkzr = new StringTokenizer(nextPath, "/");
				String connectionName = getPathValue(tkzr.nextToken());

				Schema schema = findSchema(connectionName);
				
				if (schema != null) {
					srcSchemas.add(new RestSourceSchema(nextPath, schema));
				}
			}

			RestViewSourceInfo response = new RestViewSourceInfo(viewDefinition.getName(), srcSchemas.toArray(new RestSourceSchema[0]));
			return toResponse(response);
		});
	}

    @GET
    @Path(V1Constants.PUBLISH)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Gets the published virtualization services", response = BuildStatus[].class)
    @ApiResponses(value = { @ApiResponse(code = 403, message = "An error has occurred.") })
    public Response getVirtualizations(final @Context HttpHeaders headers, final @Context UriInfo uriInfo,
            @ApiParam(value = "true to include in progress services", required = true, defaultValue="true")
            @QueryParam("includeInProgress") boolean includeInProgressServices) {
        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        //
        // Ensure include in-progress services is included by default
        //
        if (! uriInfo.getQueryParameters().containsKey("includeInProgressServices")) //$NON-NLS-1$
            includeInProgressServices = true;

        Collection<BuildStatus> statuses = this.openshiftClient.getVirtualizations(includeInProgressServices);
        return toResponse(statuses);
    }

    @GET
    @Path(V1Constants.PUBLISH + StringConstants.FORWARD_SLASH + V1Constants.VDB_PLACEHOLDER)
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Find Build Status of Virtualization by VDB name", response = BuildStatus.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No VDB could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getVirtualizationStatus(final @Context HttpHeaders headers, final @Context UriInfo uriInfo,
            @ApiParam(value = "Name of the VDB", required = true) final @PathParam("vdbName") String vdbName) {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        BuildStatus status = this.openshiftClient.getVirtualizationStatus(vdbName);

        return toResponse(status);
    }

    @GET
    @Path(V1Constants.PUBLISH_LOGS + StringConstants.FORWARD_SLASH + V1Constants.VDB_PLACEHOLDER)
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Find Publish Logs of Virtualization by VDB name", response = KomodoStatusObject.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No VDB could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getVirtualizationLogs(final @Context HttpHeaders headers, final @Context UriInfo uriInfo,
            @ApiParam(value = "Name of the VDB", required = true) final @PathParam("vdbName") String vdbName) {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        KomodoStatusObject status = new KomodoStatusObject("Logs for " + vdbName); //$NON-NLS-1$

        String log = this.openshiftClient.getVirtualizationLog(vdbName);
        status.addAttribute("log", log); //$NON-NLS-1$
        return toResponse(status);
    }
    
    @DELETE
    @Path(V1Constants.PUBLISH + StringConstants.FORWARD_SLASH + V1Constants.VDB_PLACEHOLDER)
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Delete Virtualization Service by VDB name",response = BuildStatus.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No VDB could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response deleteVirtualization(final @Context HttpHeaders headers, final @Context UriInfo uriInfo,
            @ApiParam(value = "Name of the VDB", required = true) final @PathParam("vdbName") String vdbName) {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        BuildStatus status = this.openshiftClient.deleteVirtualization(vdbName);
        return toResponse(status);
    }

    @POST
    @Path(V1Constants.PUBLISH)
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Publish Virtualization Service",
                                response = KomodoStatusObject.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No Dataservice could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response publishVirtualization(final @Context HttpHeaders headers, final @Context UriInfo uriInfo,
            @ApiParam(value = "JSON properties:<br>" + OPEN_PRE_TAG + OPEN_BRACE + BR + NBSP
                    + "\"name\":      \"Name of the Dataservice\"" + BR
                    + "\"cpu-units\": \"(optional) Number of CPU units to allocate. 100 is 0.1 CPU (default 500)\"" + BR
                    + "\"memory\":    \"(optional) Amount memory to allocate in MB (default 1024)\"" + BR
                    + "\"disk-size\": \"(optional) Amount disk allocated in GB (default 20)\"" + BR
                    + "\"enable-odata\": \"(optional) Enable OData interface. true|false (default true)\"" + BR
                    + CLOSE_BRACE
                    + CLOSE_PRE_TAG, required = true) final PublishRequestPayload payload) throws Exception {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse()) {
            return principal.getErrorResponse();
        }

        //
        // Error if there is no name attribute defined
        //
        if (payload.getName() == null) {
            return createErrorResponseWithForbidden(RelationalMessages.Error.VDB_NAME_NOT_PROVIDED);
        }

        return runInTransaction(principal, "publish-init", true, ()-> {
            DataVirtualization dataservice = getWorkspaceManager().findDataVirtualization(payload.getName());
		    if (dataservice == null) {
		        return createErrorResponse(Status.NOT_FOUND, RelationalMessages.Error.VDB_NOT_FOUND);
		    }
            
            KomodoStatusObject status = new KomodoStatusObject();
            status.addAttribute("Publishing", "Operation initiated");  //$NON-NLS-1$//$NON-NLS-2$

            final OAuthCredentials creds = getAuthenticationToken();

            // Get all of the editor states from the user profile
            // They are stored under ids of form "serviceVdbName.viewName"
			VDBMetaData theVdb = generateServiceVDB(dataservice);
            
            // the properties in this class can be exposed for user input
            PublishConfiguration config = new PublishConfiguration();
            config.setVDB(theVdb);
            config.setOAuthCredentials(creds);
            config.setEnableOData(payload.getEnableOdata());
            config.setContainerDiskSize(payload.getDiskSize());
            config.setContainerMemorySize(payload.getMemory());
            config.setCpuUnits(payload.getCpuUnits());
            BuildStatus buildStatus = openshiftClient.publishVirtualization(config, theVdb.getName());

            //
            // If the thread concludes within the time of the parent thread sleeping
            // then add some build status messages.
            //
            status.addAttribute("Vdb Name", buildStatus.vdbName()); //$NON-NLS-1$
            status.addAttribute("Build Status", buildStatus.status().name()); //$NON-NLS-1$
            status.addAttribute("Build Status Message", buildStatus.statusMessage()); //$NON-NLS-1$

            //
            // Return the status from this request. Otherwise, monitor using #getVirtualizations()
            //
            return toResponse(status);
        }); 
    }

	VDBMetaData generateServiceVDB(DataVirtualization dataservice) throws KException, Exception {
		String serviceVdbName = dataservice.getServiceVdbName();
		List<? extends ViewDefinition> editorStates = getWorkspaceManager().getViewDefinitions(dataservice.getName());
		 
		VDBMetaData theVdb = new ServiceVdbGenerator(this).refreshServiceVdb(serviceVdbName, editorStates);
		return theVdb;
	}

    /**
     * Deploy / re-deploy a VDB to the metadata instance for the provided teiid data source.
     * @param teiidSource the teiidSource
     * @return the DeployStatus from deploying the VDB
     * @throws KException
     * @throws InterruptedException
     */
    private void doDeploySourceVdb( TeiidDataSource teiidSource ) throws KException {
        assert( teiidSource != null );

        // VDB is created in the repository.  If it already exists, delete it
        final WorkspaceManager mgr = this.getWorkspaceManager();
        
        SourceSchema schema = mgr.findSchema(teiidSource.getId());
        
        // Name of VDB to be created is based on the source name
        String vdbName = getWorkspaceSourceVdbName( teiidSource.getName() );
        
        VDBMetaData vdb = generateSourceVdb(teiidSource, vdbName, schema == null ? null : schema.getDdl());
		getMetadataInstance().deploy(vdb);
		
		if (schema != null && schema.getDdl() == null) {
			saveSchema(teiidSource.getId(), teiidSource.getName());
		}
    }

	static VDBMetaData generateSourceVdb(TeiidDataSource teiidSource, String vdbName, String schema) throws KException {
		// Get necessary info from the source
        String sourceName = teiidSource.getName();
        String jndiName = teiidSource.getJndiName();
        String driverName = teiidSource.getType();
        
        VDBMetaData vdb = new VDBMetaData();
        vdb.setName(vdbName);
        vdb.setDescription("Vdb for source "+teiidSource); //$NON-NLS-1$
        ModelMetaData mmd = new ModelMetaData();
        mmd.setName(getSchemaModelName(sourceName));
        vdb.addModel(mmd);
        mmd.setModelType(Type.PHYSICAL);
        mmd.addProperty("importer.TableTypes", "TABLE,VIEW"); //$NON-NLS-1$ //$NON-NLS-2$
        mmd.addProperty("importer.UseQualifiedName", "true");  //$NON-NLS-1$//$NON-NLS-2$
        mmd.addProperty("importer.UseCatalogName", "false");  //$NON-NLS-1$//$NON-NLS-2$
        mmd.addProperty("importer.UseFullSchemaName", "false");  //$NON-NLS-1$//$NON-NLS-2$
        if (teiidSource.getSchema() != null) {
        	mmd.addProperty("importer.schemaName", teiidSource.getSchema());  //$NON-NLS-1$//$NON-NLS-2$
        }
        
        if (schema != null) {
        	//use this instead
        	mmd.addSourceMetadata("DDL", schema);
        }
        
        // Add model source to the model
        final String modelSourceName = teiidSource.getName();
        mmd.addSourceMapping(modelSourceName, driverName, jndiName);
        return vdb;
	}
    
    /**
     * Find the schema VDB model in the workspace for the specified teiid source
     * @param dataSource the teiid datasource
     * @return the Model
     * @throws KException
     */
    private Schema findSchemaModel(final TeiidDataSource dataSource ) throws KException {
        final String dataSourceName = dataSource.getName( );
		
		//find from deployed state
        String vdbName = getWorkspaceSourceVdbName( dataSourceName );
		TeiidVdb vdb = getMetadataInstance().getVdb(vdbName);
		if (vdb == null) {
	        doDeploySourceVdb(dataSource);
			vdb = getMetadataInstance().getVdb(vdbName);
		}
		
		if (vdb == null) {
			return null;
		}
		
		String name = getSchemaModelName(dataSourceName);
        
		return vdb.getSchema(name);
	}

    /**
     * Generate a workspace source vdb name, given the name of the source
     * @param sourceName the source name
     * @return the source vdb name
     */
    static String getWorkspaceSourceVdbName( final String sourceName ) {
        return sourceName.toLowerCase() + CONNECTION_VDB_SUFFIX;
    }

    /**
     * Just a no-op
     */
    static String getSchemaModelName( final String sourceName ) {
        return sourceName;
    }
    
    /**
     * Generate the syndesis source schema structure using the supplied table fqn information.
     * @param sourceName the name of the source
     * @param tables the supplied array of tables
     * @return the list of schema nodes
     * @throws KException exception if problem occurs
     */
    private List<RestSchemaNode> generateSourceSchema(final String sourceName, final Collection<org.teiid.metadata.Table> tables) throws KException {
        List<RestSchemaNode> schemaNodes = new ArrayList<RestSchemaNode>();

        for(final org.teiid.metadata.Table table : tables) {
            // Use the fqn table option do determine native structure
            String option = table.getProperty(TABLE_OPTION_FQN, false );
            if( option != null ) {
                // Break fqn into segments (segment starts at root, eg "schema=public/table=customer")
                String[] segments = option.split(FORWARD_SLASH);
                // Get the parent node of the final segment in the 'path'.  New nodes are created if needed.
                RestSchemaNode parentNode = getLeafNodeParent(sourceName, schemaNodes, segments);

                // Use last segment to create the leaf node child in the parent.  If parent is null, was root (and leaf already created).
                if( parentNode != null ) {
                    String type = getSegmentType(segments[segments.length-1]);
                    String name = getSegmentName(segments[segments.length-1]);
                    RestSchemaNode node = new RestSchemaNode(sourceName, name, type);
                    node.setQueryable(true);
                    String path = createSchemaNodePath(segments.length-1, segments);
                    node.setPath(path);
                    parentNode.addChild(node);
                }
            }
        }
        
        return schemaNodes;
    }

    /**
     * Get the RestSchemaNode immediately above the last path segment (leaf parent).  If the parent nodes do not already exist,
     * they are created and added to the currentNodes.  The returned List is a list of the root nodes.  The root node children,
     * children's children, etc, are built out according to the path segments.
     * @param sourceName the name of the source
     * @param currentNodes the current node list
     * @param segments the full path of segments, starting at the root
     * @return the final segments parent node.  (null if final segment is at the root)
     */
    private RestSchemaNode getLeafNodeParent(String sourceName, List<RestSchemaNode> currentNodes, String[] segments) {
        RestSchemaNode parentNode = null;
        // Determine number of levels to process.
        // - process one level if one segment
        // - if more than one level, process nSegment - 1 levels
        int nLevels = (segments.length > 1) ? segments.length-1 : 1;

        // Start at beginning of segment path, creating nodes if necessary
        for( int i=0; i < nLevels; i++ ) {
            String type = getSegmentType(segments[i]);
            String name = getSegmentName(segments[i]);
            // Root Level - look for matching root node in the list 
            if( i == 0 ) {
                RestSchemaNode matchNode = getMatchingNode(sourceName, name, type, currentNodes.toArray( new RestSchemaNode[ currentNodes.size() ] ));
                // No match - create a new node
                if(matchNode == null) {
                    matchNode = new RestSchemaNode(sourceName, name, type);
                    String path = createSchemaNodePath(i,segments);
                    matchNode.setPath(path);
                    currentNodes.add(matchNode);
                }
                // Set parent for next iteration
                if( segments.length == 1 ) {       // Only one segment - parent is null (root)
                    matchNode.setQueryable(true);
                    parentNode = null;
                } else {
                    // Set next parent if not last level
                    if( i != segments.length-1 ) { 
                        parentNode = matchNode;
                    }
                }
            // Not at root - look for matching node in parents children
            } else {
                RestSchemaNode matchNode = getMatchingNode(sourceName, name, type, parentNode.getChildren());
                // No match - create a new node
                if(matchNode == null) {
                    matchNode = new RestSchemaNode(sourceName, name, type);
                    String path = createSchemaNodePath(i,segments);
                    matchNode.setPath(path);
                    parentNode.addChild(matchNode);
                }
                // Set next parent if not last level
                if( i != segments.length-1 ) {
                    parentNode = matchNode;
                }
            }
        }
        return parentNode;
    }

    /**
     * Generate the path for the node, given the segments and the position within the segments
     * @param iPosn the index position within the segments
     * @param segments the array of segments
     * @return the node path (segment0/segment1/etc)
     */
    private String createSchemaNodePath(int iPosn, String[] segments) {
        StringBuilder sb = new StringBuilder();
        if(segments!=null && segments.length > 0) {
            for (int i = 0; i < segments.length; i++) {
                if(i < iPosn) {
                    sb.append(segments[i]+"/"); //$NON-NLS-1$
                } else {
                    sb.append(segments[i]);
                    break;
                }
            }
        }
        return sb.toString();
    }
    
    /**
     * Searches the supplied list for node with matching name and type.  Does NOT search children or parents of supplied nodes.
     * @param sourceName the source name
     * @param name the node name
     * @param type the node type
     * @param nodeList the list of nodes to search
     * @return the matching node, if found
     */
    private RestSchemaNode getMatchingNode(String sourceName, String name, String type, RestSchemaNode[] nodeArray) {
        RestSchemaNode matchedNode = null;
        for(RestSchemaNode node : nodeArray) {
            if( node.getConnectionName().equals(sourceName) && node.getName().equals(name) && node.getType().equals(type) ) {
                matchedNode = node;
                break;
            }
        }
        return matchedNode;
    }

    /**
     * Split the segment apart and return the name
     * @param segment the segment (eg "table=customer")
     * @return the name (eg "customer")
     */
    private String getSegmentName(String segment) {
        String[] parts = segment.split(EQUALS);
        return parts[1].trim();
    }
    
    /**
     * Split the segment apart and return the type
     * @param segment the segment (eg "table=customer")
     * @return the type (eg "table")
     */
    private String getSegmentType(String segment) {
        String[] parts = segment.split(EQUALS);
        return parts[0].trim();
    }
    
    /**
     * Set the schema availability for the provided RestSyndesisSourceStatus 
     * @param status the RestSyndesisSourceStatus
     * @throws Exception if error occurs
     */
    private void setSchemaStatus(String schemaId, final RestSyndesisSourceStatus status ) throws Exception {
        // Get the workspace schema VDB
        SourceSchema schema = getWorkspaceManager().findSchema(schemaId);
        status.setId(schemaId);
        
        if ( schema != null && schema.getDdl() != null) {
            status.setSchemaState( RestSyndesisSourceStatus.EntityState.ACTIVE );
        } else {
        	status.setSchemaState( RestSyndesisSourceStatus.EntityState.MISSING );
        }
    }
    
    @Override
    public Schema findSchema(String connectionName) throws KException {
    	TeiidDataSource tds = findTeiidDatasource(connectionName);
    	if (tds == null) {
    		return null;
    	}
    	return findSchemaModel(tds);
    }
    
    @Override
    public TeiidDataSource findTeiidDatasource(String connectionName) throws KException {
    	return getMetadataInstance().getDataSource(connectionName);
    }
    
    private String getPathValue(String pathSegment) {
		// Example sourcePath >>>> sourcePaths[0] =
		// "connection=conn1/schema=public/table=customer";
		LOGGER.info("      >>> path segment = " + pathSegment);
		StringTokenizer tkzr = new StringTokenizer(pathSegment, "=");
		tkzr.nextToken();
		return tkzr.nextToken();
    }
    
}
