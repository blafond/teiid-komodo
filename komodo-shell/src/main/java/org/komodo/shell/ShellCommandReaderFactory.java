/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.komodo.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.komodo.shell.api.InvalidCommandArgumentException;
import org.komodo.shell.api.ShellCommandFactory;
import org.komodo.shell.api.WorkspaceStatus;
import org.komodo.utils.i18n.I18n;

/**
 * Class that acts as factory method class. It includes the method that depends
 * the kind of arguments passed generates the specific implementation of the
 * ShellCommandReader
 *
 * This class adapted from https://github.com/Governance/s-ramp/blob/master/s-ramp-shell
 * - altered to use different Messages class
 *
 * @author David Virgil Naranjo
 */
public class ShellCommandReaderFactory {

    /**
     * Creates an appropriate {@link ShellCommandReader} based on the command
     * line arguments and the current runtime environment.
     *
     * @param args the args
     * @param wsStatus the workspace status
     * @return the shell command reader
     * @throws Exception Signals exception has occurred.
     */
    public static ShellCommandReader createCommandReader(String[] args, WorkspaceStatus wsStatus) throws Exception {
        ShellCommandFactory factory = wsStatus.getCommandFactory();
        ShellCommandReader commandReader = null;
        if (args.length > 0) {
            Map<String, String> properties = new HashMap<String, String>();
            if (args[0].equals("-simple")) { //$NON-NLS-1$
            	properties = getProperties(1, args);
            	if (System.console() != null) {
            		commandReader = new ConsoleShellCommandReader(factory, wsStatus, properties);
            	} else {
            		commandReader = new StdInShellCommandReader(factory, wsStatus, properties);
            	}
            // -f filePath means we are reading from a file
            } else if (args.length >= 2 && "-f".equals(args[0])) { //$NON-NLS-1$
            	String filePath = args[1];
            	properties = getProperties(2, args);
            	commandReader = new FileShellCommandReader(factory, wsStatus, filePath, properties);
            }
        } else {
            if (System.console() != null) {
                commandReader = new InteractiveShellCommandReader(factory, wsStatus);
            } else {
                commandReader = new StdInShellCommandReader(factory, wsStatus);
            }
        }
        return commandReader;
    }

    /**
     * Gets the properties from the args passed as parameter.
     *
     * @param index the index to start reading the system properties arguments
     * @param args the args
     * @return the properties
     */
    private static Map<String, String> getProperties(int index, String[] args) throws Exception {
        Map<String, String> properties = new HashMap<String, String>();
        boolean propertyFileArg = false;
        for (int i = index; i < args.length; i++) {
            String argument = args[i];
            if (!propertyFileArg) {
                if (argument.startsWith("-D")) { //$NON-NLS-1$
                    if (argument.contains("=")) { //$NON-NLS-1$
                        String key = argument.substring(2, argument.indexOf("=")); //$NON-NLS-1$
                        String value = argument.substring(argument.indexOf("=") + 1); //$NON-NLS-1$
                        properties.put(key, value);
                    } else {
                        throw new Exception( "Error Argument: " //$NON-NLS-1$
                                             + argument
                                             + " index: " //$NON-NLS-1$
                                             + i
                                             + " " //$NON-NLS-1$
                                             + I18n.bind( ShellI18n.invalidArgMsgPropertyNotCorrectFormat ) );

                    }

                } else if (argument.equals("-propertiesFile")) { //$NON-NLS-1$
                    propertyFileArg = true;
                }
            } else {
                propertyFileArg = false;
                try {
                    properties.putAll(getPropertiesFromFile(argument, index));
                } catch (InvalidCommandArgumentException sae) {
                    throw new Exception("Error Argument: " + argument + " index: " + i + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + sae.getMessage());
                }

            }

        }
        return properties;
    }

    /**
     * Gets the properties from a file.
     *
     * @param filePath the file path
     * @param index the index
     * @return the properties from file
     * @throws InvalidCommandArgumentException the shell argument exception
     */
    private static Map<String, String> getPropertiesFromFile(String filePath, int index)
            throws InvalidCommandArgumentException {
        Map<String, String> properties = new HashMap<String, String>();
        File f = new File(filePath);

        Properties props = new Properties();

            try {
            props.load(new FileInputStream(f));
        } catch (FileNotFoundException e) {
            throw new InvalidCommandArgumentException(index,
                                                      I18n.bind(ShellI18n.invalidArgMsgPropertiesFileNotExist));
        } catch (IOException e) {
            throw new InvalidCommandArgumentException( index,
                                                       I18n.bind( ShellI18n.invalidArgMsgPropertiesFileNotExist )
                                                              + ": " //$NON-NLS-1$
                                                              + e.getMessage() );
            }
        for (final String name : props.stringPropertyNames()){
            properties.put(name, props.getProperty(name));
        }
        return properties;
    }

}
