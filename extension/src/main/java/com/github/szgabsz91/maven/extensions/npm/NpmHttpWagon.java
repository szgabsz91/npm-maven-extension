package com.github.szgabsz91.maven.extensions.npm;

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Custom {@link Wagon} implementation for downloading npm packages over HTTP.
 * @author szgabsz91
 */
@Component(role = Wagon.class, hint = "npm-http", instantiationStrategy = "per-lookup")
public class NpmHttpWagon extends NpmWagon {

    /**
     * Default constructor.
     */
    public NpmHttpWagon() {
        super("http");
    }

}
