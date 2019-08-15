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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.komodo.datavirtualization.ViewDefinition;
import org.komodo.metadata.internal.DefaultMetadataInstance;
import org.komodo.repository.KomodoRepositoryConfiguration;
import org.komodo.repository.WorkspaceManagerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.teiid.adminapi.Model.Type;
import org.teiid.adminapi.impl.ModelMetaData;
import org.teiid.adminapi.impl.VDBMetaData;

@RunWith(SpringRunner.class)
@DataJpaTest
@ContextConfiguration(classes = {KomodoRepositoryConfiguration.class, ServiceTestConfiguration.class})
public class KomodoUtilServiceTest {
	
	@Autowired
	private DefaultMetadataInstance metadataInstance;
	
	@Autowired
	private KomodoUtilService komodoUtilService;
	
	@Autowired
    private TestEntityManager entityManager;
	
	@Autowired
	private WorkspaceManagerImpl workspaceManagerImpl;

	@Test public void testStash() throws Exception {
		
		workspaceManagerImpl.createDataVirtualization("x");
		
		ViewDefinition vd = new ViewDefinition("x", "y");
		
		ViewDefinition saved = komodoUtilService.upsertViewEditorState(vd);
		
		entityManager.flush();
		
		assertNotNull(saved.getId());
		
		vd = new ViewDefinition("x", "y");
		vd.setId("not correct");
		
		try {
			saved = komodoUtilService.upsertViewEditorState(vd);
			fail();
		} catch (IllegalArgumentException e) {
			//trying to change the id
		}
		
		//add a dummy preview vdb
		VDBMetaData vdb = new VDBMetaData();
		vdb.setName(KomodoUtilService.PREVIEW_VDB);
		ModelMetaData m = new ModelMetaData();
		m.setName("x");
		vdb.addModel(m);
		m.setModelType(Type.VIRTUAL);
		m.addSourceMetadata("DDL", "create view v as select 1");
		metadataInstance.deploy(vdb);
		
		//update with invalid ddl
		vd.setId(null);
		vd.setDdl("create something");
		vd.setUserDefined(true);
		vd.setComplete(true);
		
		saved = komodoUtilService.upsertViewEditorState(vd);
		
		entityManager.flush();
		
		ViewDefinition found = workspaceManagerImpl.findViewDefinition(saved.getId());
		assertEquals("create something", found.getDdl());
		
		//saving with valid ddl
		vd.setDdl("create view y as select * from v");
		
		saved = komodoUtilService.upsertViewEditorState(vd);
		
		//the save should determine what is used in the view
		assertEquals(Arrays.asList("schema=x/table=v"), saved.getSourcePaths());
	}

}
