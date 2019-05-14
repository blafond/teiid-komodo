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
package org.komodo.rest;

import static org.komodo.rest.Messages.Error.COMMIT_TIMEOUT;

import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.komodo.core.KEngine;
import org.komodo.core.repository.SynchronousCallback;
import org.komodo.openshift.TeiidOpenShiftClient;
import org.komodo.relational.model.Model;
import org.komodo.relational.vdb.Vdb;
import org.komodo.relational.workspace.WorkspaceManager;
import org.komodo.rest.SyndesisConnectionMonitor.EventMsg;
import org.komodo.spi.KException;
import org.komodo.spi.repository.Repository;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.spi.repository.Repository.UnitOfWork.State;
import org.komodo.spi.repository.Repository.UnitOfWorkListener;
import org.komodo.spi.runtime.TeiidDataSource;
import org.komodo.spi.runtime.TeiidVdb;

/**
 * This class provides the communication and hooks
 *
 */
public class SyndesisConnectionSynchronizer extends AbstractKomodoMetadataService {
	private static final Log LOGGER = LogFactory.getLog(SyndesisConnectionSynchronizer.class);
	private static final int TIMEOUT = 30;
	private static final TimeUnit UNIT = TimeUnit.SECONDS;

	private KEngine kengine;
	private TeiidOpenShiftClient openshiftClient;

	public SyndesisConnectionSynchronizer(KEngine kengine) {
		super(kengine);
		this.kengine = kengine;
	}

	/*
	 * This method processes each connection event and delegates to appropriate
	 * connection operation
	 */
	public void handleConnectionEvent(EventMsg event) {

		switch (event.getAction()) {
		/*
		 * 1 - On create of a syndesis connection /metadata/syndesisSource (POST) (arg:
		 * name=’syndesis source name’) ‘Binds’ the syndesisSource - this creates source
		 * in teiid which corresponds to syndesis source.
		 */
		case created: {
			LOGGER.info("SyndesisConnectionMonitor.handleConnectionEvent(CREATE)");
//				bindSyndesisSource(event.name);
//				refreshSchema(event.name);
		}
			break;
		/*
		 * 2 - Refresh the connection schema (deploys VDB, builds schema)
		 * /metdata/refresh-schema/{connectionName} (POST) (args: redeploy /
		 * generate-schema) This service controls deployment of the ‘connection’ vdb and
		 * regeneration of a schema model in the repo. Ultimately, the schema model is
		 * used to provide the schema info to the UI A) redeploy=true -
		 * deploys/redeploys connection VDB to teiid - ‘[connName]btlconn’ B)
		 * generate-schema=true - pulls metadata from deployed connection VDB - create
		 * repo schema VDB - ‘[connName]schemavdb’.
		 */
		case deleted: {
			LOGGER.info("SyndesisConnectionMonitor.handleConnectionEvent(DELETE)");
		}
			break;

		case updated: {
			LOGGER.info("SyndesisConnectionMonitor.handleConnectionEvent(UPDATE)");
		}
			break;
		}

	}

	/*
	 * This method checks each applicable syndesis connection and updates all
	 * associated syndesisSource vdbs and schema
	 */
	public void synchronizeConnections() {

		// get all source connections from syndesis
		LOGGER.info("connected synchronize connections");
	}

	private void addConnection() {

	}

	private void removeConnection() {

	}

	private void replaceConnection() {

	}

	private void bindSyndesisSource(String syndesisSourceName) throws Exception {

		this.openshiftClient.bindToSyndesisSource(getAuthenticationToken(), syndesisSourceName);
	}

	private void refreshSchema(String syndesisSourceName) throws Exception {

		UnitOfWork uow = null;

		try {
			uow = createTransaction("refreshSchema", false); //$NON-NLS-1$

			// Find the bound teiid source corresponding to the syndesis source
			TeiidDataSource teiidSource = this.kengine.getMetadataInstance().getDataSource(syndesisSourceName);

			if (teiidSource == null)
				throw new KException("Teiid Source not found for Syndnesis source :" + syndesisSourceName);

			TeiidVdb deployedVdb = findDeployedVdb(syndesisSourceName);

			// Initiate the VDB deployment

			if (deployedVdb == null) {
				doDeploySourceVdb(uow, teiidSource); // this will delete workspace VDB first
			}

			final SynchronousCallback callback = (SynchronousCallback) uow.getCallback();
			uow.commit();

			if (!callback.await(30, TimeUnit.SECONDS)) {
				// callback timeout occurred
				String errorMessage = Messages.getString(COMMIT_TIMEOUT, uow.getName(), TIMEOUT, UNIT);
				throw new KException(errorMessage);
			}

			// check deployed status
			deployedVdb = findDeployedVdb(syndesisSourceName);
			if (deployedVdb == null) {
				throw new KException("VDB did not deploy for Syndesis source : " + syndesisSourceName);
			}

			uow = createTransaction("generateSchema", false); //$NON-NLS-1$

			Vdb schemaVdb = findWorkspaceSchemaVdb(uow, teiidSource);
			final String schemaModelName = getSchemaModelName(syndesisSourceName);
			Model schemaModel = null;

			// create if necessary
			if (schemaVdb == null) {
				final WorkspaceManager wkspMgr = getWorkspaceManager(uow);
				final String schemaVdbName = getSchemaVdbName(syndesisSourceName);
				schemaVdb = wkspMgr.createVdb(uow, null, schemaVdbName, schemaVdbName);

				// Add schema model to schema vdb
				schemaModel = addModelToSchemaVdb(uow, schemaVdb, teiidSource, schemaModelName);
			} else {
				final Model[] models = schemaVdb.getModels(uow, schemaModelName);

				if (models.length != 0) {
					schemaModel = models[0];
				} else {
					// should never happen but just in case
					schemaModel = addModelToSchemaVdb(uow, schemaVdb, teiidSource, schemaModelName);
				}
			}

			final String modelDdl = getMetadataInstance().getSchema(deployedVdb.getName(), "1", schemaModelName); //$NON-NLS-1$
			schemaModel.setModelDefinition(uow, modelDdl);
			// after transaction is committed this will trigger the DDL sequencer which will
			// create
			// the model objects.

			commit(uow);

		} catch (final Exception e) {
			if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
				uow.rollback();
			}
			throw new KException(e);
		}
	}

	/**
	 * @param name         the name of the transaction (cannot be empty)
	 * @param rollbackOnly <code>true</code> if transaction must be rolled back
	 * @param callback     the callback to fire when the transaction is committed
	 * @return the new transaction (never <code>null</code>)
	 * @throws KException if there is an error creating the transaction
	 */
	protected UnitOfWork createTransaction(final String name, final boolean rollbackOnly,
			final UnitOfWorkListener callback) throws KException {
		Repository repo = this.kengine.getDefaultRepository();
		final UnitOfWork result = repo.createTransaction("user",
				(getClass().getSimpleName() + COLON + name + COLON + System.currentTimeMillis()), rollbackOnly,
				callback);
		return result;
	}

	/**
	 * @param name         the name of the transaction (cannot be empty)
	 * @param rollbackOnly <code>true</code> if transaction must be rolled back
	 * @return the new transaction (never <code>null</code>)
	 * @throws KException if there is an error creating the transaction
	 */
	protected UnitOfWork createTransaction(final String name, final boolean rollbackOnly) throws KException {
		Repository repo = this.kengine.getDefaultRepository();
		final SynchronousCallback callback = new SynchronousCallback();
		final UnitOfWork result = repo.createTransaction("user",
				(getClass().getSimpleName() + COLON + name + COLON + System.currentTimeMillis()), rollbackOnly,
				callback);
		return result;
	}

	protected void commit(UnitOfWork transaction) throws Exception {
		assert (transaction.getCallback() instanceof SynchronousCallback);
		final int timeout = TIMEOUT;
		final TimeUnit unit = UNIT;

		final SynchronousCallback callback = (SynchronousCallback) transaction.getCallback();
		transaction.commit();

		if (!callback.await(timeout, unit)) {
			// callback timeout occurred
			String errorMessage = Messages.getString(COMMIT_TIMEOUT, transaction.getName(), timeout, unit);
			throw new KException(errorMessage);
		}

		Throwable error = transaction.getError();
		if (error != null) {
			// callback was called because of an error condition
			throw new KException(error);
		}

		error = callback.error();
		if (error != null) {
			// callback was called because of an error condition
			throw new KException(error);
		}
	}

	protected WorkspaceManager getWorkspaceManager(UnitOfWork transaction) throws KException {
		Repository repo = this.kengine.getDefaultRepository();
		return WorkspaceManager.getInstance(repo, transaction);
	}
}
