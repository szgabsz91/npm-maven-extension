package com.github.szgabsz91.maven.plugins.npm;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Mojo(name = "extract", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyCollection = ResolutionScope.COMPILE)
public class ExtractMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(name = "outputFolder", defaultValue = "${project.build.directory/npm}", required = false)
    private File outputFolder;

    public void execute() throws MojoExecutionException {
        Path outputFolder = this.outputFolder.toPath();
        recreateFolder(outputFolder);

        for (Artifact dependency : project.getArtifacts()) {
            if (!"npm".equals(dependency.getType())) {
                continue;
            }


            Path file = dependency.getFile().toPath();
            Path destinationFile = outputFolder.resolve(dependency.getArtifactId() + ".tar.gz");
            getLog().info("Copying " + file + " to " + destinationFile);

            try {
                if (Files.exists(destinationFile)) {
                    Files.delete(destinationFile);
                }

                Files.copy(file, destinationFile);

                Path destinationFolder = getDestinationFolder(outputFolder, dependency.getArtifactId());
                extract(destinationFile, destinationFolder);
                Files.delete(destinationFile);
            }
            catch (IOException e) {
                throw new MojoExecutionException("Cannot copy file " + file + " to " + destinationFile, e);
            }
        }
    }

    private static void recreateFolder(Path folder) throws MojoExecutionException {
        if (Files.isDirectory(folder)) {
            try {
                Files.walk(folder)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
            catch (IOException e) {
                throw new MojoExecutionException("Cannot delete folder " + folder, e);
            }
        }

        try {
            Files.createDirectories(folder);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Cannot create folder " + folder, e);
        }
    }

    private static Path getDestinationFolder(Path outputFolder, String artifactId) throws MojoExecutionException {
        if (artifactId.startsWith("_")) {
            String[] artifactIdParts = artifactId.split("_");
            String packageScope = artifactIdParts[1];
            String packageName = artifactIdParts[2];
            Path scopeFolder = outputFolder.resolve(packageScope);
            if (!Files.isDirectory(scopeFolder)) {
                try {
                    Files.createDirectories(scopeFolder);
                }
                catch (IOException e) {
                    throw new MojoExecutionException("Cannot create scope folder " + scopeFolder + " for " + artifactId, e);
                }
            }
            Path packageFolder = scopeFolder.resolve(packageName);
            return packageFolder;
        }

        return outputFolder.resolve(artifactId);
    }

    private static void extract(Path targzFile, Path destinationFolder) {
        TarGZipUnArchiver tarGZipUnArchiver = new TarGZipUnArchiver();
        tarGZipUnArchiver.setSourceFile(targzFile.toFile());
        destinationFolder.toFile().mkdirs();
        tarGZipUnArchiver.setDestDirectory(destinationFolder.toFile());
        tarGZipUnArchiver.extract();
    }

}
