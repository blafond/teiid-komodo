package org.komodo.lsp.websocket;

import java.util.Collections;
import java.util.Set;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

public class TeiidDdlWebSocketServerConfigProvider implements ServerApplicationConfig {

    private static final String WEBSOCKET_TEIID_DDL_SERVER_PATH = "/teiid-ddl-language-server";

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        ServerEndpointConfig conf = ServerEndpointConfig.Builder.create(TeiidDdlWebSocketEndpoint.class, WEBSOCKET_TEIID_DDL_SERVER_PATH).build();
        return Collections.singleton(conf);
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        return scanned;
    }

}