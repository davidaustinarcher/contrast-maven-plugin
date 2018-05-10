package com.contrastsecurity.maven.plugin;

import com.contrastsecurity.exceptions.UnauthorizedException;
import com.contrastsecurity.models.AgentType;
import com.contrastsecurity.sdk.ContrastSDK;
import java.text.SimpleDateFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.awt.peer.SystemTrayPeer;
import java.io.File;
import java.io.IOException;
import java.util.Date;

abstract class AbstractContrastMavenPluginMojo extends AbstractMojo {

    @Component
    protected MavenProject project;

    @Parameter(property = "username", required = true)
    protected String username;

    @Parameter(property = "apiKey", required = true)
    protected String apiKey;

    @Parameter(property = "serviceKey", required = true)
    protected String serviceKey;

    @Parameter(property = "apiUrl")
    protected String apiUrl;

    @Parameter(property = "orgUuid", required = true)
    protected String orgUuid;

    @Parameter(property = "appName", required = true)
    protected String appName;

    @Parameter(property = "minSeverity", defaultValue = "Medium")
    protected String minSeverity;

    @Parameter(property = "serverName", required = true)
    protected String serverName;

    @Parameter(property = "jarPath")
    protected String jarPath;

    @Parameter(property = "appVersion")
    protected String appVersion;

    @Parameter(property = "skipArgLine")
    protected boolean skipArgLine;

    protected String contrastAgentLocation;

    // Start time we will look for
    protected static Date verifyDateTime;

    private static final String AGENT_NAME = "contrast.jar";

    public void execute() throws MojoExecutionException {
    }

    ContrastSDK connectToTeamServer() throws MojoExecutionException {
        try {
            if (!StringUtils.isEmpty(apiUrl)) {
                return new ContrastSDK(username, serviceKey, apiKey, apiUrl);
            } else {
                return new ContrastSDK(username, serviceKey, apiKey);
            }
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("\n\nWe couldn't connect to TeamServer at this address [" + apiUrl + "]. The error is: ", e);
        }
    }

    protected String generateAppVersion() {
        if (appVersion != null) {
            return appVersion;
        }

        String appVersionTimestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        appVersion = appName + "-" + appVersionTimestamp;
        return appVersion;
    }

    protected String buildArgLine() {
        String currentArgLine = project.getProperties().getProperty("argLine");

        if(currentArgLine == null) {
            getLog().info("currentArgLine is null");
            currentArgLine = "";
        } else {
            getLog().info("Current argLine is [" + currentArgLine + "]");
        }

        if(skipArgLine) {
            getLog().info("skipArgLine is set to false.");
            getLog().info("You will need to configure the Maven argLine property manually for the Contrast agent to work.");
            return currentArgLine;
        }

        getLog().info("Configuring argLine property.");

        appVersion = generateAppVersion();

        String newArgLine = currentArgLine + " -javaagent:" + contrastAgentLocation + " -Dcontrast.override.appname=" + appName + " -Dcontrast.server=" + serverName + " -Dcontrast.env=qa -Dcontrast.override.appversion=" + appVersion;

        getLog().info("Updated argLine is " + newArgLine);

        return newArgLine;
    }

    File installJavaAgent(ContrastSDK connection) throws MojoExecutionException {
        byte[] javaAgent;
        File agentFile;

        if (StringUtils.isEmpty(jarPath)) {
            getLog().info("No jar path was configured. Downloading the latest contrast.jar...");

            try {
                javaAgent = connection.getAgent(AgentType.JAVA, orgUuid);
            } catch (IOException e) {
                throw new MojoExecutionException("\n\nWe couldn't download the Java agent from TeamServer. There probably isn't much you can do to resolve this, so please contact Contrast Support. The error is:", e);
            } catch (UnauthorizedException e) {
                throw new MojoExecutionException("\n\nWe contacted TeamServer successfully but couldn't authorize with the credentials you provided. The error is:", e);
            }

            // Save the jar to the 'target' directory
            agentFile = new File(project.getBuild().getDirectory() + File.separator + AGENT_NAME);

            try {
                FileUtils.writeByteArrayToFile(agentFile, javaAgent);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to save the latest java agent.", e);
            }

            getLog().info("Saved the latest java agent to " + agentFile.getAbsolutePath());
            contrastAgentLocation = agentFile.getAbsolutePath();

        } else {
            getLog().info("Using configured jar path " + jarPath);

            agentFile = new File(jarPath);

            if (!agentFile.exists()) {
                throw new MojoExecutionException("Unable to load the local Java agent from " + jarPath);
            }

            getLog().info("Loaded the latest java agent from " + jarPath);
            contrastAgentLocation = jarPath;

        }

        return agentFile;
    }
}