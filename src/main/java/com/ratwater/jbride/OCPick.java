package com.ratwater.jbride;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import org.codehaus.plexus.util.IOUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class OCPick {
    private static Logger log = LoggerFactory.getLogger("OCPick");
    private static final String YAML_CONFIG_PATH_ENV = "YAML_CONFIG_PATH_ENV";
    private static final String YAML_CONFIG_PATH_ARG = "--config_path=";
    private static final String DEFAULT_CONFIG_FILE_NAME = ".ocp_env_details.yaml";
    private static final String APP_VERSION = "app_version";
    private static String version = "0.0";
    private static Properties appProps = null;
    private static String yamlConfigPath = null;
    private static Map<String, OCPENV> envMap = null;

    public static void main(String args[]) {
        testOC();
        readAppProps();
        determineVariables(args);
        readAndValidateYaml();
        String guid = promptForGuid();
        login(guid);
    }

    private static void testOC() {

        InputStream iStream = null;
        try {
            Process p = Runtime.getRuntime().exec("oc version");
            iStream = p.getInputStream();
            String commandOutput = IOUtil.toString(iStream);

            log.info("testOC() commandOutput = " + commandOutput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (iStream != null)
                try { iStream.close();  } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static void determineVariables(String args[]) {
        if (args.length > 0) {
            for (int x = 0; x < args.length; x++) {
                log.info("determineVariables() arg = " + args[x]);
                if (args[x].startsWith(YAML_CONFIG_PATH_ARG)) {
                    yamlConfigPath = args[x].substring(13);
                }
            }
        }
        if (StringUtils.isEmpty(yamlConfigPath)) {
            String temp = System.getenv(YAML_CONFIG_PATH_ENV);
            if (!StringUtils.isEmpty(temp)) {
                yamlConfigPath = System.getenv(YAML_CONFIG_PATH_ENV);
            }
        }
        if (StringUtils.isEmpty(yamlConfigPath)) {
            String currentUsersHomeDir = System.getProperty("user.home");
            yamlConfigPath = currentUsersHomeDir + File.separator + DEFAULT_CONFIG_FILE_NAME;
        }
        log.info("determineVariables() yaml file to parse = " + yamlConfigPath);
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
        log.info("main() app version = " + version);
    }

    private static void dumpSysProperties() {
        Properties pros = System.getProperties();
        pros.list(System.out);
    }

    private static void readAndValidateYaml() {
        File yamlFile = new File(yamlConfigPath);
        if (!yamlFile.exists())
            throw new RuntimeException("readAndValidateYaml() the following file does not exist: " + yamlConfigPath);

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
        log.info(sBuilder.toString());
    }

    private static String promptForGuid() {
        String promptString = "\nWhich of the following OCP environments would you like to connect to ? (Please specify the GUID): \n\n";
        String guid = null;
        Scanner iScanner = new Scanner(System.in);
        while (guid == null) {
            System.out.println(promptString);
            guid = iScanner.next();
            if (envMap.get(guid) == null) {
                log.error("Nothing known about OCP env with GUID "+guid+" in: " + yamlConfigPath);
                guid = null;
            } else {
                OCPENV ocpEnv = envMap.get(guid);
                log.info("\nWill login to the following OCP env: " + ocpEnv.toString());
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

    private static void login(String guid) {
        OCPENV ocpEnv = envMap.get(guid);
        StringBuilder lCommand = new StringBuilder("oc login https://");

        // 1)  Determine URL to OCP Master
        if(ocpEnv.getType().equals(OCPENV.AWS)){
            lCommand.append("master."+guid+"."+ocpEnv.getSubdomainBase());
        } else if(ocpEnv.getType().equals(OCPENV.RAVELLO)){
            lCommand.append(OCPENV.RAVELLO_MASTER_PREFIX+guid+"."+ocpEnv.getSubdomainBase());
        } else {
            throw new RuntimeException("Unknown OCP environment type: "+ocpEnv.getType());
        }


        // 2)  Add userId and passwd
        if(ocpEnv.isLoginAsAdmin()){
            lCommand.append(" -u "+ ocpEnv.getAdminUserId());
            lCommand.append(" -p "+ ocpEnv.getAdminPasswd());
        }else {
            lCommand.append(" -u "+ ocpEnv.getUserId());
            lCommand.append(" -p "+ ocpEnv.getUserPasswd());
        }
        log.info("\nlogin command = "+lCommand);

        InputStream iStream = null;
        try {
            Process p = Runtime.getRuntime().exec(lCommand.toString());
            iStream = p.getInputStream();
            String commandOutput = IOUtil.toString(iStream);

            log.info("\n login Successful; response = " + commandOutput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (iStream != null)
                try { iStream.close();  } catch (IOException e) { e.printStackTrace(); }
        }

    }
}
