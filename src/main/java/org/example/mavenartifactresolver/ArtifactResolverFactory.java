package org.example.mavenartifactresolver;

import org.apache.maven.settings.building.SettingsBuildingException;

import java.util.Objects;

/**
 * @author Diptopol
 * @since 2/10/2022 5:44 PM
 */
public class ArtifactResolverFactory {

    private static ArtifactResolver artifactResolver;

    public static ArtifactResolver getArtifactResolver() {
        if (Objects.isNull(artifactResolver)) {
            try {
                artifactResolver = new ArtifactResolver();
            } catch (SettingsBuildingException ex) {
                ex.printStackTrace();
            }

        }

        return artifactResolver;
    }
}
