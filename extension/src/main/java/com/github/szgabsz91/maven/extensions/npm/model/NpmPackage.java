package com.github.szgabsz91.maven.extensions.npm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.maven.wagon.WagonConstants;
import org.apache.maven.wagon.repository.Repository;

/**
 * Model class that represents an npm package.
 * @author szgabsz91
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public final class NpmPackage {

    private String scope;

    @NonNull
    private String name;

    @NonNull
    private String version;

    /**
     * Factory method for constructing an {@link NpmPackage} from the given resource name.
     * A resource name might look like "npm/npm-package/1.1.0-SNAPSHOT/npm-package-1.1.0-SNAPSHOT.pom".
     * Here, the second part is the package name and the third part is the version.
     *
     * @param resourceName the resource name
     * @return the newly created {@link NpmPackage} containing the package name and version
     */
    public static NpmPackage fromResourceName(String resourceName) {
        String[] resourceNameParts = resourceName.split("/");
        String name = resourceNameParts[resourceNameParts.length - 3];
        String version = resourceNameParts[resourceNameParts.length - 2];

        // Scoped package
        if (name.startsWith("_")) {
            String[] nameParts = name.split("_");
            String scope = nameParts[1];
            name = nameParts[2];
            return new NpmPackage(scope, name, version);
        }

        return new NpmPackage(name, version);
    }

    /**
     * Returns the full package name that is either in the form of "@scope/name" or simple "name".
     * @return the full name of the npm package
     */
    public String getFullName() {
        if (scope == null) {
            return name;
        }

        return "@" + scope + "/" + name;
    }

    /**
     * Returns the package name as a valid Maven artifactId.
     * For scoped packages like "@angular/router" the converted artifactId will be "_angular_router".
     *
     * @return the valid Maven artifactId
     */
    public String getArtifactId() {
        if (scope == null) {
            return name;
        }

        return "_" + scope + "_" + name;
    }

    /**
     * Returns the URL where the tar.gz can be downloaded based on the protocol and repository.
     * @param protocol the protocol
     * @param repository the repository containing the host, port and base directory
     * @return the URL string
     */
    public String toUrl(String protocol, Repository repository) {
        String portPart = repository.getPort() == WagonConstants.UNKNOWN_PORT ? "" : ":" + repository.getPort();

        String urlWithoutProtocol = String.format(
            "%s%s/%s/%s/-/%s-%s.tgz",
            repository.getHost(), portPart, repository.getBasedir(), getFullName(), name, version
        );

        return protocol + "://" + urlWithoutProtocol.replaceAll("/+", "/");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (scope == null) {
            return name + "@" + version;
        }

        return "@" + scope + "/" + name + "@" + version;
    }

}
