package com.github.szgabsz91.maven.extensions.npm;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class NpmWagonTest {

    @TestFactory
    public Stream<DynamicTest> dynamicTests() {
        return Stream.of(new Parameters("npm-https", new NpmHttpsWagon()), new Parameters("npm-http", new NpmHttpWagon()))
                .flatMap(parameters -> {
                    Consumer<Consumer<Parameters>> testMethodWrapper = testMethod -> {
                        String protocol = parameters.getProtocol();
                        Wagon wagon = parameters.getWagon();
                        Repository repository = new Repository("npm", protocol + "://registry.npmjs.org");
                        AuthenticationInfo authentictionInfo = new AuthenticationInfo();
                        authentictionInfo.setUserName("admin");
                        authentictionInfo.setPassword("admin123");
                        ProxyInfo proxyInfo = new ProxyInfo();

                        try {
                            wagon.connect(repository, authentictionInfo, proxyInfo);
                            Path tempFolder = Files.createTempDirectory("npm-maven-extension");
                            parameters.setTempFolder(tempFolder);

                            testMethod.accept(parameters);

                            Files.walkFileTree(tempFolder, new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.delete(file);
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult postVisitDirectory(Path folder, IOException exc) throws IOException {
                                    Files.delete(folder);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        }
                        catch (IOException | ConnectionException | AuthenticationException e) {
                            throw new IllegalStateException("Test failed due to exception", e);
                        }
                    };

                    return Arrays.stream(NpmWagonTest.class.getDeclaredMethods())
                            .filter(method -> method.getName().startsWith("test"))
                            .map(method -> dynamicTest(method.getName() + " (" + parameters.getProtocol() + ")", () -> {
                                testMethodWrapper.accept(params -> {
                                    try {
                                        method.invoke(this, params);
                                    }
                                    catch (IllegalAccessException | InvocationTargetException e) {
                                        throw new IllegalStateException("Test failed due to exception", e);
                                    }
                                });
                            }));
                });
    }

    public void testOpenConnectionInternal(Parameters parameters) throws ConnectionException, AuthenticationException {
        Wagon wagon = parameters.getWagon();
        Repository repository = new Repository("npm-https", "https://registry.npmjs.org");
        wagon.connect(repository);
    }

    public void testCloseConnection(Parameters parameters) throws ConnectionException {
        Wagon wagon = parameters.getWagon();
        wagon.disconnect();
    }

    public void testPut(Parameters parameters) {
        Wagon wagon = parameters.getWagon();
        assertThrows(UnsupportedOperationException.class, () -> wagon.put(null, null), "The npm packages should be published using npm or yarn, this operation is illegal");
    }

    public void testGetIfNewerWithPom(Parameters parameters) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/jquery/1.5.1/jquery-1.5.1.pom";
        Path destination = Paths.get(tempFolder.toString(), "jquery.pom");
        boolean result = wagon.getIfNewer(resourceName, destination.toFile(), 0L);
        assertThat(result).isTrue();
        assertPomXml(destination, "npm", "jquery", "1.5.1");
    }

    public void testGetIfNewerWithNpm(Parameters parameters) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/jquery/1.5.1/jquery-1.5.1.npm";
        Path destination = Paths.get(tempFolder.toString(), "jquery.npm");
        boolean result = wagon.getIfNewer(resourceName, destination.toFile(), 0L);
        assertThat(result).isTrue();
        assertNpm(destination, "package/dist/node-jquery.js");
    }

    public void testGetIfNewerWithUnknownResourceType(Parameters parameters) {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "unknown";
        Path destination = Paths.get(tempFolder.toString(), "unknown");

        assertThrows(IllegalArgumentException.class, () -> wagon.getIfNewer(resourceName, destination.toFile(), 0L), "Unsupported resource " + resourceName + " and file " + destination.toFile());
    }

    public void testGetWithPom(Parameters parameters) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/jquery/1.5.1/jquery-1.5.1.pom";
        Path destination = Paths.get(tempFolder.toString(), "jquery.pom");
        wagon.get(resourceName, destination.toFile());
        assertPomXml(destination, "npm", "jquery", "1.5.1");
    }
    
    public void testGetWithPomAndIOException(Parameters parameters) {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/jquery/1.5.1/jquery-1.5.1.pom";
        Path destination = Paths.get(tempFolder.toString(), "non-existent", "jquery.pom");
        TransferFailedException exception = assertThrows(TransferFailedException.class, () -> wagon.get(resourceName, destination.toFile()), "Could not write pom.xml");
        assertThat(exception.getCause()).isInstanceOf(NoSuchFileException.class);
    }

    public void testGetWithNpm(Parameters parameters) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException, IOException {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/jquery/1.5.1/jquery-1.5.1.npm";
        Path destination = Paths.get(tempFolder.toString(), "jquery.npm");
        boolean result = wagon.getIfNewer(resourceName, destination.toFile(), 0L);
        assertThat(result).isTrue();
        assertNpm(destination, "package/dist/node-jquery.js");
    }

    public void testGetWithNpmAndIOException(Parameters parameters) {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/jquery/1.5.1/jquery-1.5.1.npm";
        Path destination = Paths.get(tempFolder.toString(), "non-existent", "jquery.npm");
        TransferFailedException exception = assertThrows(TransferFailedException.class, () -> wagon.get(resourceName, destination.toFile()), "Cannot download npm package " + resourceName + " to " + destination.toFile());
        assertThat(exception.getCause()).isInstanceOf(NoSuchFileException.class);
    }

    public void testGetWithNpmAndNotFoundError(Parameters parameters) {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "npm/nonexistent/100.0.0/othername-100.0.0.npm";
        Path destination = Paths.get(tempFolder.toString(), "othername.npm");
        assertThrows(ResourceDoesNotExistException.class, () -> wagon.get(resourceName, destination.toFile()), "Cannot download npm package " + resourceName + ", status code: 404");
    }

    public void testGetWithUnknownResourceType(Parameters parameters) {
        Wagon wagon = parameters.getWagon();
        Path tempFolder = parameters.getTempFolder();
        String resourceName = "unknown";
        Path destination = Paths.get(tempFolder.toString(), "unknown");
        assertThrows(IllegalArgumentException.class, () -> wagon.get(resourceName, destination.toFile()), "Unsupported resource " + resourceName + " and file " + destination.toFile());
    }

    private static void assertPomXml(Path file, String groupId, String artifactId, String version) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Document document = readDocument(file);
        XPath xpath = createXPath();
        String modelVersionText = (String) xpath.evaluate("/project/modelVersion/text()", document, XPathConstants.STRING);
        assertThat(modelVersionText).isEqualTo("4.0.0");
        String groupIdText = (String) xpath.evaluate("/project/groupId/text()", document, XPathConstants.STRING);
        assertThat(groupIdText).isEqualTo(groupId);
        String artifactIdText = (String) xpath.evaluate("/project/artifactId/text()", document, XPathConstants.STRING);
        assertThat(artifactIdText).isEqualTo(artifactId);
        String versionText = (String) xpath.evaluate("/project/version/text()", document, XPathConstants.STRING);
        assertThat(versionText).isEqualTo(version);
    }

    private static Document readDocument(Path file) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(file.toFile());
    }

    private static XPath createXPath() {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        return xPathFactory.newXPath();
    }

    private static void assertNpm(Path file, String distFilePath) throws IOException {
        boolean foundDistFile = false;

        try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(Files.newInputStream(file)))) {
            TarArchiveEntry tarArchiveEntry = tarArchiveInputStream.getNextTarEntry();

            while (tarArchiveEntry != null) {
                if (tarArchiveEntry.getName().equals(distFilePath)) {
                    foundDistFile = true;
                    break;
                }

                tarArchiveEntry = tarArchiveInputStream.getNextTarEntry();
            }
        }

        assertThat(foundDistFile).as("The dist file is not found in the tgz file").isTrue();
    }

    @Data
    @RequiredArgsConstructor
    class Parameters {

        @NonNull
        private String protocol;

        @NonNull
        private Wagon wagon;

        private Path tempFolder;

    }

}
