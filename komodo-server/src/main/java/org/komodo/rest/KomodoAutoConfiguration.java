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

import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.transaction.TransactionManager;
import javax.websocket.DeploymentException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.lsp4j.*;
import org.komodo.KEngine;
import org.komodo.lsp.TeiidDdlLanguageServer;
import org.komodo.metadata.MetadataInstance;
import org.komodo.metadata.internal.DefaultMetadataInstance;
import org.komodo.metadata.internal.TeiidServer;
import org.komodo.openshift.EncryptionComponent;
import org.komodo.openshift.TeiidOpenShiftClient;
import org.komodo.repository.WorkspaceManagerImpl;
import org.komodo.rest.connections.SyndesisConnectionSynchronizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.teiid.runtime.EmbeddedConfiguration;

@Configuration
@EnableConfigurationProperties({KomodoConfigurationProperties.class, SpringMavenProperties.class})
@ComponentScan(basePackageClasses = {WorkspaceManagerImpl.class, DefaultMetadataInstance.class, SyndesisConnectionSynchronizer.class})
@EnableAsync
public class KomodoAutoConfiguration implements ApplicationListener<ContextRefreshedEvent>, AsyncConfigurer {
    private static final Log LOGGER = LogFactory.getLog(KomodoAutoConfiguration.class);

    private static final String LSP_DEFAULT_HOSTNAME = "localhost"; // syndesis-dv:????
    private static final int LSP_DEFAULT_PORT = 8025;
    private static final String LSP_DEFAULT_CONTEXT_PATH = "/";

    @Value("${encrypt.key}")
    private String encryptKey;

    @Autowired(required=false)
    private TransactionManager transactionManager;

    @Autowired
    private KomodoConfigurationProperties config;

    @Autowired
    private SpringMavenProperties maven;

    @Autowired
    private KEngine kengine;

    private TeiidDdlLanguageServer server;

    @Autowired
    private MetadataInstance metadataInstance;

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    @Bean(name = "connectionExecutor")
    public ScheduledThreadPoolExecutor connectionExecutor() {
        return executor;
    }

    @Bean
    public TextEncryptor getTextEncryptor() {
        return Encryptors.text(encryptKey, "deadbeef");
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            LOGGER.info("   ---->>>>>>>  KomodoAutoConfiguration.onApplicationEvent() kengine.start()");
            kengine.start();
            LOGGER.info("   ---->>>>>>>  KomodoAutoConfiguration.onApplicationEvent() initialize teiidDdlLanguageServer()");
            teiidDdlLanguageServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public TeiidServer teiidServer() {

        // turning off PostgreSQL support
        System.setProperty("org.teiid.addPGMetadata", "false");
        System.setProperty("org.teiid.hiddenMetadataResolvable", "true");
        System.setProperty("org.teiid.allowAlter", "false");

        final TeiidServer server = new TeiidServer();

        EmbeddedConfiguration config = new EmbeddedConfiguration();
        if (this.transactionManager != null) {
            config.setTransactionManager(this.transactionManager);
        }
        server.start(config);
        return server;
    }

    @Bean
    @ConditionalOnMissingBean
    public TeiidOpenShiftClient openShiftClient(@Autowired KEngine kengine, @Autowired TextEncryptor enc) {
        return new TeiidOpenShiftClient(metadataInstance, new EncryptionComponent(enc),
                this.config, kengine, this.maven == null ? null : this.maven.getRepositories());
    }

    @Bean
    protected WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(getAsyncExecutor());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public void teiidDdlLanguageServer() {

        server = new TeiidDdlLanguageServer();

        String hostname = LSP_DEFAULT_HOSTNAME;
        int port = LSP_DEFAULT_PORT;
        String contextPath = LSP_DEFAULT_CONTEXT_PATH;

        LOGGER.info("   ---->>>  KomodoAutoConfiguration.teiidDdlLanguageServer() Host:Port = " + hostname + ":" + port);

        server.startServer();

        server.getTextDocumentService();
// Server server = new Server(hostname, port, contextPath, null, MyLSPWebSocketServerConfigProvider.class);
// Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "camel-lsp-websocket-server-shutdown-hook"));

// try {
// server.connect();
// Thread.currentThread().join();
// } catch (InterruptedException e) {
//
// Thread.currentThread().interrupt();
// } catch (DeploymentException e) {
//
// } finally {
// server.stop();
// }
    }

    @Override
    public AsyncTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor tpte = new ThreadPoolTaskExecutor();
        tpte.initialize();
        return tpte;
    }
}
