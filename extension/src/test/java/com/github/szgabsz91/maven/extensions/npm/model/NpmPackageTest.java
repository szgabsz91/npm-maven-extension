package com.github.szgabsz91.maven.extensions.npm.model;

import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NpmPackageTest {

    @Test
    public void testFromResourceNameWithNonScopedPackage() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/jquery/1.5.1/jquery-1.5.1.npm");
        assertThat(npmPackage.getScope()).isNull();
        assertThat(npmPackage.getName()).isEqualTo("jquery");
        assertThat(npmPackage.getFullName()).isEqualTo("jquery");
        assertThat(npmPackage.getArtifactId()).isEqualTo("jquery");
        assertThat(npmPackage.getVersion()).isEqualTo("1.5.1");
    }

    @Test
    public void testFromResourceNameWithScopedPackage() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/_angular_router/4.1.3/_angular_router-4.1.3.npm");
        assertThat(npmPackage.getScope()).isEqualTo("angular");
        assertThat(npmPackage.getName()).isEqualTo("router");
        assertThat(npmPackage.getFullName()).isEqualTo("@angular/router");
        assertThat(npmPackage.getArtifactId()).isEqualTo("_angular_router");
        assertThat(npmPackage.getVersion()).isEqualTo("4.1.3");
    }

    @Test
    public void testToUrlWithNonScopedPackage() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/jquery/1.5.1/jquery-1.5.1.npm");
        String protocol = "https";
        Repository repository = new Repository("npm", "https://registry.npmjs.org");
        String url = npmPackage.toUrl(protocol, repository);
        assertThat(url).isEqualTo("https://registry.npmjs.org/jquery/-/jquery-1.5.1.tgz");
    }

    @Test
    public void testToUrlWithScopedPackage() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/_angular_router/4.1.3/_angular_router-4.1.3.npm");
        String protocol = "https";
        Repository repository = new Repository("npm", "https://registry.npmjs.org");
        String url = npmPackage.toUrl(protocol, repository);
        assertThat(url).isEqualTo("https://registry.npmjs.org/@angular/router/-/router-4.1.3.tgz");
    }

    @Test
    public void testToUrlWithNonDefaultPort() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/custom-package/1.0.0-SNAPSHOT/custom-package-1.0.0-SNAPSHOT.npm");
        String protocol = "http";
        Repository repository = new Repository("npm", "http://custom.repository.com:8080");
        String url = npmPackage.toUrl(protocol, repository);
        assertThat(url).isEqualTo("http://custom.repository.com:8080/custom-package/-/custom-package-1.0.0-SNAPSHOT.tgz");
    }

    @Test
    public void testToStringWithNonScopedPackage() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/jquery/1.5.1/jquery-1.5.1.npm");
        assertThat(npmPackage).hasToString("jquery@1.5.1");
    }

    @Test
    public void testToStringWithScopedPackage() {
        NpmPackage npmPackage = NpmPackage.fromResourceName("npm/_angular_router/4.1.3/_angular_router-4.1.3.npm");
        assertThat(npmPackage).hasToString("@angular/router@4.1.3");
    }

}
