package com.github.szgabsz91.maven.extensions.npm;

import com.github.szgabsz91.maven.extensions.npm.model.NpmPackage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Custom {@link org.apache.maven.wagon.Wagon} implementation for downloading npm packages over HTTP/HTTPS.
 * @author szgabsz91
 */
@Slf4j
public class NpmWagon extends AbstractWagon {

    private static final String DEFAULT_GROUPID = "npm";

    private final String protocol;

    /**
     * Constructor that sets the protocol, either "http" or "https".
     * @param protocol the protocol to use for downloading npm packages
     */
    public NpmWagon(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Does nothing.
     *
     * {@inheritDoc}
     */
    protected final void openConnectionInternal() {
        log.debug("Opening connection");
    }

    /**
     * Does nothing.
     *
     * {@inheritDoc}
     */
    protected final void closeConnection() {
        log.debug("Closing connection");
    }

    /**
     * Throws {@link UnsupportedOperationException} because npm packages should be published via npm or yarn.
     *
     * {@inheritDoc}
     */
    public final void put(File source, String destination) {
        throw new UnsupportedOperationException("The npm packages should be published using npm or yarn, this operation is illegal");
    }

    /**
     * Downloads the required resource by calling {@link NpmWagon#get(String, File)}.
     *
     * {@inheritDoc}
     */
    public final boolean getIfNewer(String resourceName, File destination, long timestamp) throws TransferFailedException {
        if (!resourceName.endsWith(".pom") && !resourceName.endsWith(".npm")) {
            return false;
        }

        this.get(resourceName, destination);
        return true;
    }

    /**
     * Tries to download different resources from the repository. The supported resource types are:
     *
     * <ul>
     *     <li>*.pom: generates a dummy pom.xml file containing the default groupId, the package name and version</li>
     *     <li>*.npm: downloads the npm package from the repository as a tar.gz file</li>
     * </ul>
     *
     * {@inheritDoc}
     */
    public final void get(String resourceName, File destination) throws TransferFailedException {
        log.info("Trying to download {} into {}", resourceName, destination);

        if (resourceName.endsWith(".pom")) {
            generatePomXml(resourceName, destination.toPath());
        }
        else if (resourceName.endsWith(".npm")) {
            downloadNpmPackage(resourceName, destination.toPath());
        }
    }

    /**
     * Generates a dummy pom.xml file that contains the default groupId, the package name and version.
     * @param resourceName the resource name
     * @param destination the destination file
     * @throws TransferFailedException if the file cannot be written
     */
    private static void generatePomXml(String resourceName, Path destination) throws TransferFailedException {
        NpmPackage npmPackage = NpmPackage.fromResourceName(resourceName);
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        Template template = velocityEngine.getTemplate("/templates/pom.xml.vm", StandardCharsets.UTF_8.name());
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("groupId", DEFAULT_GROUPID);
        velocityContext.put("artifactId", npmPackage.getArtifactId());
        velocityContext.put("version", npmPackage.getVersion());
        StringWriter stringWriter = new StringWriter();
        template.merge(velocityContext, stringWriter);
        String pomXmlContent = stringWriter.toString();
        log.debug(
            "Generating a dummy pom.xml file into {} with groupId={}, artifactId={}, version={}",
            destination, DEFAULT_GROUPID, npmPackage.getName(), npmPackage.getVersion()
        );
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write(pomXmlContent);
        }
        catch (IOException e) {
            throw new TransferFailedException("Could not write pom.xml", e);
        }
    }

    /**
     * Downloads the given resource to the given destination.
     * The target file will be a tar.gz file downloaded from the repository.
     *
     * @param resourceName the name of the resource
     * @param destination the destination file
     * @throws TransferFailedException if the npm package cannot be downloaded
     */
    private void downloadNpmPackage(String resourceName, Path destination) throws TransferFailedException {
        NpmPackage npmPackage = NpmPackage.fromResourceName(resourceName);
        AuthenticationInfo authenticationInfo = getAuthenticationInfo();
        CredentialsProvider credentialsProvider = null;

        if (authenticationInfo.getUserName() != null) {
            credentialsProvider = CredentialsProviderBuilder.create()
                .add(new HttpHost(repository.getHost(), repository.getPort()), authenticationInfo.getUserName(), authenticationInfo.getPassword().toCharArray())
                .build();
        }

        CloseableHttpClient httpClient = HttpClients
            .custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .build();

        try {
            String url = npmPackage.toUrl(protocol, repository);
            log.info("Downloading npm package {} from {}", npmPackage, url);
            HttpGet httpGet = new HttpGet(url);
            httpClient.execute(httpGet, response -> {
                int statusCode = response.getCode();
                if (statusCode != HttpStatus.SC_OK) {
                    throw new IllegalArgumentException(new ResourceDoesNotExistException("Cannot download npm package " + resourceName + ", status code: " + statusCode));
                }

                try (InputStream inputStream = response.getEntity().getContent();
                     OutputStream outputStream = Files.newOutputStream(destination)) {
                    IOUtils.copy(inputStream, outputStream);
                }

                return null;
            });
        }
        catch (IOException e) {
            throw new TransferFailedException("Cannot download npm package " + resourceName + " to " + destination, e);
        }
        finally {
            try {
                httpClient.close();
            }
            catch (IOException e) {
                log.warn("Could not close HttpClient", e);
            }
        }
    }

}
