package org.example.mavenartifactresolver;

import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.util.List;

/**
 * @author Diptopol
 * @since 2/9/2022 1:41 PM
 */
public class Main {

    public static void main(String[] args) {
        ArtifactResolver artifactResolver = ArtifactResolverFactory.getArtifactResolver();

        /*<!-- https://mvnrepository.com/artifact/org.bitbucket.b_c/jose4j -->
            <dependency>
                <groupId>org.bitbucket.b_c</groupId>
                <artifactId>jose4j</artifactId>
                <version>0.7.10</version>
            </dependency>
        */

        try {
            List<ArtifactResult> artifactResultList = artifactResolver.resolveArtifact("org.bitbucket.b_c",
                    "jose4j", "0.7.10", "pom");

            for (ArtifactResult artifactResult: artifactResultList) {
                System.out.println(artifactResult.getArtifact().getFile().getAbsolutePath());
            }
        } catch (DependencyResolutionException ex) {
            ex.printStackTrace();
        }

    }
}
