package com.ratwater.jbride;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import org.codehaus.plexus.util.IOUtil;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Possible alternatives to SnakeYaml:
 *   1)  YamlBean: 
 *           - https://github.com/EsotericSoftware/yamlbeans
 *           - Makes use of reflection
 *   2)  Jackson Yaml Module:  
 *           - https://github.com/FasterXML/jackson-dataformats-text/tree/master/yaml
 *           - Jackson wrapper around SnakeYaml
 *
 */

public class OCPick {

    private static final String YAML_CONFIG_PATH_ENV = "YAML_CONFIG_PATH_ENV";
    private static final String YAML_CONFIG_PATH_ARG = "--config_path=";
    private static final String HELP_ARG = "--help";
    private static final String VERSION_ARG = "--version";
    private static final String GUID_ARG = "--guid=";
    private static final String DEFAULT_CONFIG_FILE_NAME = ".ocp_env_details.yaml";
    private static final String APP_VERSION = "app_version";
    private static String version = "0.0";
    private static Properties appProps = null;
    private static String yamlConfigPath = null;
    private static Map<String, OCPENV> envMap = null;
    
    public static void main(String args[]) {
        readAppProps();
        String guid = parseArgsAndEnvVariables(args);
        readAndValidateYaml();
        if(StringUtils.isEmpty(guid)) {
            guid = promptForGuid();
        }
        testOC(guid);
        login(guid);
    }
    

    /**
     * parseArgsAndEnvVariables
     *   - returns optional guid if passed as a command line argument
    */
    private static String parseArgsAndEnvVariables(String args[]) {
        
        String currentUsersHomeDir = System.getProperty("user.home");
        yamlConfigPath = currentUsersHomeDir + File.separator + DEFAULT_CONFIG_FILE_NAME;

        String guid = null;
        if (args.length > 0) {
            for (int x = 0; x < args.length; x++) {
                
                if(args[x].startsWith(HELP_ARG)) {
                    dumpHelp();
                    System.exit(0);
                } else if (args[x].startsWith(VERSION_ARG)) {
                    System.out.println("\n\nOC_Pick version = "+version);
                    System.exit(0);
                } else if (args[x].startsWith(YAML_CONFIG_PATH_ARG)) {
                    yamlConfigPath = args[x].substring(14);
                } else if(args[x].startsWith(GUID_ARG)) {
                    guid = args[x].substring(7);
                } else {
                    System.out.println("Unknown command line arg: "+args[x]);
                    dumpHelp();
                    System.exit(1);
                }
            }
        }
        if (!StringUtils.isEmpty(System.getenv(YAML_CONFIG_PATH_ENV))) {
            yamlConfigPath = System.getenv(YAML_CONFIG_PATH_ENV);
        }
        return guid;
    }
    
    private static void dumpHelp() {
        StringBuilder sBuilder = new StringBuilder("NAME\n\tOCP Pick");
        sBuilder.append("\n\nUSAGE\n\tocp_pick [options]");
        sBuilder.append("\n\nDESCRIPTION\n\tSelect from mulitple OCP environments to log into.");
        sBuilder.append("\n\nOPTIONS");
        sBuilder.append("\n\t"+HELP_ARG+"\t\t\tHelp");
        sBuilder.append("\n\n\t"+YAML_CONFIG_PATH_ARG+"\t\tPath to yaml formatted data file containing one or more OCP environments.");
        sBuilder.append("\n\t\t\t\t\tDefault path = "+ yamlConfigPath);
        sBuilder.append("\n\t"+VERSION_ARG+"\t\tOC_Pick version");
        sBuilder.append("\n\n");
        System.out.println(sBuilder.toString());
    }
    
    private static void readAppProps() {
        InputStream is = null;
        try {
            is = OCPick.class.getResourceAsStream("/application.properties");
            if (is != null) {
                appProps = new Properties();
                appProps.load(is);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // dumpSysProperties();
        if ((appProps != null) && (!StringUtils.isEmpty((String) appProps.get(APP_VERSION)))) {
            version = (String) appProps.get(APP_VERSION);
        }
    }
    
    private static void dumpSysProperties() {
        Properties pros = System.getProperties();
        pros.list(System.out);
    }
    
    private static void readAndValidateYaml() {
        System.out.println("app version = " + version);
        File yamlFile = new File(yamlConfigPath);
        if (!yamlFile.exists())
            throw new RuntimeException("readAndValidateYaml() the following file does not exist: " + yamlConfigPath);
        System.out.println("yaml file to parse = " + yamlConfigPath);
        
        FileInputStream yamlReader = null;
        OCPENVs yamlValues = null;
        try {
            yamlReader = new FileInputStream(yamlFile);
            yamlValues = new Yaml().loadAs(yamlReader, OCPENVs.class);
        } catch (IOException x) {
            throw new RuntimeException(x);
        } finally {
            try {
                yamlReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        envMap = new HashMap<String, OCPENV>();
        StringBuilder sBuilder = new StringBuilder("\nYAML objects = ");
        for (OCPENV yamlObj : yamlValues.getOcpEnvs()) {
            envMap.put(yamlObj.getGuid(), yamlObj);
            sBuilder.append("\n\t" + yamlObj.toString());
        }
        System.out.println(sBuilder.toString());
    }

    private static String promptForGuid() {
        String promptString = "\nWhich of the following OCP environments would you like to connect to ? (Please specify the GUID): \n\n";
        String guid = null;
        Scanner iScanner = new Scanner(System.in);
        while (guid == null) {
            System.out.println(promptString);
            guid = iScanner.next();
            if (envMap.get(guid) == null) {
                System.out.println("Nothing known about OCP env with GUID "+guid+" in: " + yamlConfigPath);
                guid = null;
            } else {
                OCPENV ocpEnv = envMap.get(guid);
                System.out.println("\nWill login to the following OCP env: " + ocpEnv.toString());
            }
        }
        try {
            System.in.close();
            iScanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return guid;
    }

    private static void testOC(String guid) {
        
        InputStream iStream = null;
        try {
            Process p = Runtime.getRuntime().exec("oc version");
            iStream = p.getInputStream();
            String commandOutput = IOUtil.toString(iStream);

            OCPENV ocpEnv = envMap.get(guid);
            if(ocpEnv == null)
                throw new RuntimeException("Unknown guid:  "+guid);
            if(StringUtils.isNotEmpty(commandOutput) && commandOutput.contains(ocpEnv.getOcpMajorVersion())) {
                //System.out.println("testOC() commandOutput = " + commandOutput);
            } else {
                throw new RuntimeException("Expected oc client version: "+ocpEnv.getOcpMajorVersion()+". Instead, response of 'oc version' is: \n"+commandOutput);
            }          
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (iStream != null)
            try { iStream.close();  } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static void login(String guid) {
        OCPENV ocpEnv = envMap.get(guid);
        StringBuilder lCommand = new StringBuilder("oc login https://");

        // 1)  Determine URL to OCP Master
        String url = null;
        int port=0;
        if(ocpEnv.getType().equals(OCPENV.AWS)){
            url = OCPENV.AWS_MASTER_PREFIX+guid+"."+ocpEnv.getSubdomainBase();
            port = OCPENV.AWS_MASTER_PORT;
        } else if(ocpEnv.getType().equals(OCPENV.RAVELLO)){
            url = OCPENV.RAVELLO_MASTER_PREFIX+guid+"."+ocpEnv.getSubdomainBase();
            port = OCPENV.RAVELLO_MASTER_PORT;
        } else {
            throw new RuntimeException("Unknown OCP environment type: "+ocpEnv.getType());
        }
        lCommand.append(url);
        testConnectivityToMaster(url, port);


        // 2)  Add userId and passwd
        if(ocpEnv.isLoginAsAdmin()){
            lCommand.append(" -u "+ ocpEnv.getAdminUserId());
            lCommand.append(" -p "+ ocpEnv.getAdminPasswd());
        }else { 
            lCommand.append(" -u "+ocpEnv.getUserId());
            lCommand.append(" -p "+ ocpEnv.getUserPasswd());
        }

        // 3)  Avoid prompt to accept self signed certs
        lCommand.append(" --insecure-skip-tls-verify=true");

        System.out.println("\nlogin command = "+lCommand);

        InputStream iStream = null;
        try {
            Process p = Runtime.getRuntime().exec(lCommand.toString());
            iStream = p.getInputStream();
            String commandOutput = IOUtil.toString(iStream);

            System.out.println("\n login Successful; response = " + commandOutput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (iStream != null)
                try { iStream.close();  } catch (IOException e) { e.printStackTrace(); }
        }

    }

    private static void testConnectivityToMaster(String host, int port) {
        Socket s = null;
        try {
            s = new Socket(host, port);
        } catch (Exception e) {
            throw new RuntimeException("The following network address is not available: "+host+":"+port);
        } finally {
            if (s != null)
                try { s.close(); } catch (Exception e) {}
        }
    }
}
