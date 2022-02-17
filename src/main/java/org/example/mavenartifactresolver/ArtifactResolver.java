package org.example.mavenartifactresolver;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.*;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Diptopol
 * @since 2/10/2022 5:50 PM
 */
public class ArtifactResolver {

    private static Logger logger = LoggerFactory.getLogger(ArtifactResolver.class);

    private static final String M2_REPOSITORY_PATH = PropertyReader.getProperty("m2.directory") + "/repository";
    private static final String MAVEN_SETTINGS_PATH = PropertyReader.getProperty("maven.home") + "/conf/settings.xml";

    private DefaultServiceLocator serviceLocator;
    private RepositorySystem repositorySystem;
    private Settings settings;
    private DefaultRepositorySystemSession repositorySystemSession;
    private List<RemoteRepository> remoteRepositoryList;

    public ArtifactResolver() throws SettingsBuildingException {
        this.serviceLocator = getServiceLocator();
        this.repositorySystem = serviceLocator.getService(RepositorySystem.class);
        this.settings = getSettings();
        this.repositorySystemSession = getRepositorySystemSession(this.settings);
        this.remoteRepositoryList = getRemoteRepositoryList(repositorySystem, repositorySystemSession, settings);
    }

    public List<ArtifactResult> resolveArtifact(String groupId, String artifactId, String version, String type)
            throws DependencyResolutionException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, type, version);

        return getArtifactResult(artifact, this.remoteRepositoryList, this.repositorySystem,
                this.repositorySystemSession);
    }

    private List<ArtifactResult> getArtifactResult(Artifact artifact,
                                                   List<RemoteRepository> remoteRepositoryList,
                                                   RepositorySystem repositorySystem,
                                                   RepositorySystemSession repositorySystemSession) throws DependencyResolutionException {

        final DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);

        final CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
        collectRequest.setRepositories(remoteRepositoryList);

        final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);
        final DependencyResult dependencyResult = repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

        return dependencyResult.getArtifactResults();
    }

    private DefaultServiceLocator getServiceLocator() {
        DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();

        serviceLocator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        serviceLocator.addService(TransporterFactory.class, FileTransporterFactory.class);
        serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        serviceLocator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public final void serviceCreationFailed(final Class<?> type, final Class<?> impl, final Throwable exception) {
                if (exception != null) {
                    exception.printStackTrace();
                }
            }
        });

        return serviceLocator;
    }

    public Settings getSettings() throws SettingsBuildingException {
        final SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
        assert settingsBuilder != null;

        final DefaultSettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
        settingsBuildingRequest.setSystemProperties(System.getProperties());
        settingsBuildingRequest.setGlobalSettingsFile(new File(MAVEN_SETTINGS_PATH));
        settingsBuildingRequest.setUserSettingsFile(new File(new File(PropertyReader.getProperty("m2.directory")), "/settings.xml"));

        final SettingsBuildingResult settingsBuildingResult = settingsBuilder.build(settingsBuildingRequest);

        assert settingsBuildingResult != null;

        final List<SettingsProblem> settingsBuildingProblems = settingsBuildingResult.getProblems();

        if (settingsBuildingProblems != null && !settingsBuildingProblems.isEmpty()) {
            throw new SettingsBuildingException(settingsBuildingProblems);
        }

        return settingsBuildingResult.getEffectiveSettings();
    }

    private DefaultRepositorySystemSession getRepositorySystemSession(Settings settings) {
        final DefaultServiceLocator serviceLocator = getServiceLocator();

        final RepositorySystem repositorySystem = serviceLocator.getService(RepositorySystem.class);

        DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils.newSession();

        repositorySystemSession.setTransferListener(new TransferListener());
        repositorySystemSession.setOffline(settings.isOffline());
        repositorySystemSession.setCache(new DefaultRepositoryCache());

        setMirrorSelector(repositorySystemSession, settings);

        final LocalRepository localRepository = getLocalRepository(settings);

        repositorySystemSession.setLocalRepositoryManager(getLocalRepositoryManager(repositorySystem,
                repositorySystemSession, localRepository));

        return repositorySystemSession;
    }

    private LocalRepositoryManager getLocalRepositoryManager(RepositorySystem repositorySystem,
                                                             RepositorySystemSession repositorySystemSession,
                                                             LocalRepository localRepository) {

        return repositorySystem.newLocalRepositoryManager(repositorySystemSession, localRepository);
    }

    private void setMirrorSelector(DefaultRepositorySystemSession repositorySystemSession, Settings settings) {
        final Collection<? extends Mirror> mirrors = settings.getMirrors();

        if (mirrors != null && !mirrors.isEmpty()) {
            final DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
            for (final Mirror mirror : mirrors) {
                assert mirror != null;
                mirrorSelector.add(mirror.getId(),
                        mirror.getUrl(),
                        mirror.getLayout(),
                        false, /* not a repository manager; settings.xml does not encode this information */
                        mirror.getMirrorOf(),
                        mirror.getMirrorOfLayouts());
            }

            repositorySystemSession.setMirrorSelector(mirrorSelector);
        }
    }

    private List<RemoteRepository> getRemoteRepositoryList(RepositorySystem repositorySystem,
                                                           RepositorySystemSession repositorySystemSession,
                                                           Settings settings) {
        List<RemoteRepository> remoteRepositoryList = new ArrayList<>();
        final Map<String, Profile> profiles = settings.getProfilesAsMap();
        if (profiles != null && !profiles.isEmpty()) {
            final Collection<String> activeProfileKeys = settings.getActiveProfiles();
            if (activeProfileKeys != null && !activeProfileKeys.isEmpty()) {
                for (final String activeProfileKey : activeProfileKeys) {
                    final Profile activeProfile = profiles.get(activeProfileKey);
                    if (activeProfile != null) {
                        final Collection<Repository> repositories = activeProfile.getRepositories();
                        if (repositories != null && !repositories.isEmpty()) {
                            for (final Repository repository : repositories) {
                                if (repository != null) {
                                    RemoteRepository.Builder builder = new RemoteRepository.Builder(repository.getId(), repository.getLayout(), repository.getUrl());

                                    final org.apache.maven.settings.RepositoryPolicy settingsReleasePolicy = repository.getReleases();
                                    if (settingsReleasePolicy != null) {
                                        final org.eclipse.aether.repository.RepositoryPolicy releasePolicy = new org.eclipse.aether.repository.RepositoryPolicy(settingsReleasePolicy.isEnabled(), settingsReleasePolicy.getUpdatePolicy(), settingsReleasePolicy.getChecksumPolicy());
                                        builder = builder.setReleasePolicy(releasePolicy);
                                    }

                                    final org.apache.maven.settings.RepositoryPolicy settingsSnapshotPolicy = repository.getSnapshots();
                                    if (settingsSnapshotPolicy != null) {
                                        final org.eclipse.aether.repository.RepositoryPolicy snapshotPolicy = new org.eclipse.aether.repository.RepositoryPolicy(settingsSnapshotPolicy.isEnabled(), settingsSnapshotPolicy.getUpdatePolicy(), settingsSnapshotPolicy.getChecksumPolicy());
                                        builder = builder.setSnapshotPolicy(snapshotPolicy);
                                    }

                                    final RemoteRepository remoteRepository = builder.build();
                                    assert remoteRepository != null;
                                    remoteRepositoryList.add(remoteRepository);
                                }
                            }
                        }
                    }
                }
            }
        }

        final RemoteRepository mavenCentral = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();
        assert mavenCentral != null;
        remoteRepositoryList.add(mavenCentral);

        remoteRepositoryList = repositorySystem.newResolutionRepositories(repositorySystemSession, remoteRepositoryList);

        return remoteRepositoryList;
    }

    private LocalRepository getLocalRepository(Settings settings) {
        String localRepositoryString = settings.getLocalRepository();

        if (localRepositoryString == null) {
            localRepositoryString = M2_REPOSITORY_PATH;
        }

        return new LocalRepository(localRepositoryString);
    }

    private static final class TransferListener extends AbstractTransferListener {

        private TransferListener() {
            super();
        }

        @Override
        public void transferInitiated(final TransferEvent event) {
            logger.debug("Transfer initiated: {}", event);
        }

        @Override
        public void transferStarted(final TransferEvent event) {
            logger.debug("Transfer started: {}", event);
        }

        @Override
        public void transferProgressed(final TransferEvent event) {
            logger.debug("Transfer progressed: {}", event);
        }

        @Override
        public void transferSucceeded(final TransferEvent event) {
            logger.debug("Transfer succeeded: {}", event);
        }

        @Override
        public void transferCorrupted(final TransferEvent event) {
            logger.debug("Transfer corrupted: {}", event);
        }

        @Override
        public void transferFailed(final TransferEvent event) {
            logger.debug("Transfer failed: {}", event);
        }
    }

}
