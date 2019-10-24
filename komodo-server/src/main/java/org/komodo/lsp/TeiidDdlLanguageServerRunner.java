package org.komodo.lsp;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.komodo.lsp.websocket.TeiidDdlWebSocketRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeiidDdlLanguageServerRunner {
    /**
     * For test only
     */
    static TeiidDdlLanguageServer server;

    private static final String WEBSOCKET_PARAMETER = "--websocket";
    private static final String PORT_PARAMETER = "--port=";
    private static final String HOSTNAME_PARAMETER = "--hostname=";
    private static final String CONTEXTPATH_PARAMETER = "--contextPath=";

    private static final Logger LOGGER = LoggerFactory.getLogger(TeiidDdlTextDocumentService.class);

    public static void main(String[] args) {
        LOGGER.info("   --  >>>  TeiidDdlLanguageServerRunner.main()");
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains(WEBSOCKET_PARAMETER)) {
            int port = extractPort(arguments);
            String hostname = extractHostname(arguments);
            String contextPath = extractContextPath(arguments);
            new TeiidDdlWebSocketRunner().runWebSocketServer(hostname, port, contextPath);
        } else {
            server = new TeiidDdlLanguageServer();
            Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
            server.connect(launcher.getRemoteProxy());
            launcher.startListening();
        }
    }

    private static String extractContextPath(List<String> arguments) {
        return extractParameterValue(arguments, CONTEXTPATH_PARAMETER);
    }

    private static String extractHostname(List<String> arguments) {
        return extractParameterValue(arguments, HOSTNAME_PARAMETER);
    }

    private static String extractParameterValue(List<String> arguments, String parameterToExtract) {
        for (String argument : arguments) {
            if (argument.startsWith(parameterToExtract)) {
                return argument.substring(parameterToExtract.length());
            }
        }
        return null;
    }

    private static int extractPort(List<String> arguments) {
        for (String argument : arguments) {
            if (argument.startsWith(PORT_PARAMETER)) {
                String providedPort = argument.substring(PORT_PARAMETER.length());
                try {
                    return Integer.parseInt(providedPort);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("The provided port is invalid.", nfe);
                }
            }
        }
        return -1;
    }
}
