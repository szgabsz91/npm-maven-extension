package com.github.szgabsz91.maven.extensions.npm;

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Custom {@link Wagon} implementation for downloading npm packages over HTTPS.
 * @author szgabsz91
 */
@Component(role = Wagon.class, hint = "npm-https", instantiationStrategy = "per-lookup")
public class NpmHttpsWagon extends NpmWagon {

    /**
     * Default constructor.
     */
    public NpmHttpsWagon() {
        super("https");
    }

}
