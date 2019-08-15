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

package org.komodo.repository;

import java.util.List;

import org.komodo.WorkspaceManager;
import org.komodo.datavirtualization.DataVirtualization;
import org.komodo.datavirtualization.ViewDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.teiid.core.util.EquivalenceUtil;

@Component
public class WorkspaceManagerImpl implements WorkspaceManager {
	
	@Autowired
	private DataVirtualizationRepository dataVirtualizationRepository;
	@Autowired
	private SourceSchemaRepository schemaRepository;
	@Autowired
	private ViewDefinitionRepository viewDefinitionRepository;

	@Override
	public org.komodo.datavirtualization.SourceSchema findSchema(String id) {
		return this.schemaRepository.findOne(id);
	}

	@Override
	public boolean deleteSchema(String id) {
		try {
			this.schemaRepository.delete(id);
			this.schemaRepository.flush();
			return true;
		} catch (EmptyResultDataAccessException e) {
			return false;
		}
	}
	
	@Override
	public void createOrUpdateSchema(String id, String name, String contents) {
		org.komodo.datavirtualization.SourceSchema schema = this.schemaRepository.findOne(id);
		if (schema != null) {
			if (!name.equals(schema.getName())) {
				throw new IllegalArgumentException("Cannot change the name of an existing schema");
			}
			if (!EquivalenceUtil.areEqual(contents, schema.getDdl())) {
				schema.setDdl(contents);
			}
		} else {
			schema = new org.komodo.datavirtualization.SourceSchema(id);
			schema.setName(name);
			schema.setDdl(contents);
			this.schemaRepository.save(schema);
		}
	}
	
	@Override
	public List<String> findAllSchemaNames() {
		return schemaRepository.findAllNames();
	}
	
	@Override
	public DataVirtualization findDataVirtualizationByNameIgnoreCase(String virtualizationName) {
		return this.dataVirtualizationRepository.findByNameIgnoreCase(virtualizationName);
	}

	@Override
	public DataVirtualization createDataVirtualization(String virtualizationName) {
		DataVirtualization dataservice = new DataVirtualization(virtualizationName);
		return this.dataVirtualizationRepository.save(dataservice);
	}

	@Override
	public DataVirtualization findDataVirtualization(String virtualizationName) {
		return this.dataVirtualizationRepository.findByName(virtualizationName);
	}

	@Override
	public Iterable<? extends DataVirtualization> findDataVirtualizations() {
		return this.dataVirtualizationRepository.findAll();
	}
	
	@Override
	public boolean deleteDataVirtualization(String serviceName) {
		org.komodo.datavirtualization.DataVirtualization dv = this.dataVirtualizationRepository.findByName(serviceName);
		if (dv == null) {
			return false;
		}
		this.dataVirtualizationRepository.delete(dv);
		this.dataVirtualizationRepository.flush();
		return true;
	}
	
	@Override
	public org.komodo.datavirtualization.ViewDefinition createViewDefiniton(String dvName, String viewName) {
		org.komodo.datavirtualization.ViewDefinition viewEditorState = new org.komodo.datavirtualization.ViewDefinition(dvName, viewName);
		return this.viewDefinitionRepository.save(viewEditorState);
	}
	
	@Override
	public List<String> findViewDefinitionsNames(String dvName) {
		return this.viewDefinitionRepository.findAllNamesByDataVirtualizationName(dvName);
	}
	
	@Override
	public List<org.komodo.datavirtualization.ViewDefinition> findViewDefinitions(String dvName) {
		return this.viewDefinitionRepository.findAllByDataVirtualizationName(dvName);
	}
	
	@Override
	public boolean deleteViewDefinition(String id) {
		try {
			this.viewDefinitionRepository.delete(id);
			this.viewDefinitionRepository.flush();
			return true;
		} catch (EmptyResultDataAccessException e) {
			return false;
		}
	}
	
	@Override
	public org.komodo.datavirtualization.ViewDefinition findViewDefinition(String id) {
		return this.viewDefinitionRepository.findOne(id);
	}
	
	@Override
	public ViewDefinition findViewDefinitionByNameIgnoreCase(String dvName, String viewDefinitionName) {
		return this.viewDefinitionRepository.findByNameIgnoreCase(dvName, viewDefinitionName);
	}
	
}
