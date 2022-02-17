# MavenArtifactResolver

This project tries to download maven artifact using maven. First local `.m2` directory will be lookup for the artifact and if it is not found it will fetch the artifact from `https://repo1.maven.org/maven2/`. It takes four parameters to fetch the artifact.

- GroupId
- ArtifactId
- Version
- Type (e.g., POM, JAR)
