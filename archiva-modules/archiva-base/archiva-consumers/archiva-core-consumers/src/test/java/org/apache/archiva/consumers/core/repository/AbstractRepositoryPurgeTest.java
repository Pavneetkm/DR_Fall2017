package org.apache.archiva.consumers.core.repository;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.admin.model.beans.ManagedRepository;
import org.apache.archiva.metadata.model.ArtifactMetadata;
import org.apache.archiva.metadata.repository.MetadataRepository;
import org.apache.archiva.metadata.repository.RepositorySession;
import org.apache.archiva.metadata.repository.storage.maven2.Maven2RepositoryPathTranslator;
import org.apache.archiva.repository.ManagedRepositoryContent;
import org.apache.archiva.repository.events.RepositoryListener;
import org.apache.archiva.test.utils.ArchivaSpringJUnit4ClassRunner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.index.NexusIndexer;
import org.apache.maven.index.context.IndexingContext;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
@RunWith(ArchivaSpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath*:/META-INF/spring-context.xml", "classpath:/spring-context.xml" })
public abstract class AbstractRepositoryPurgeTest
{
    public static final String TEST_REPO_ID = "test-repo";

    public static final String TEST_REPO_NAME = "Test Repository";

    public static final int TEST_RETENTION_COUNT = 2;

    public static final int TEST_DAYS_OLDER = 30;

    public static final String PATH_TO_BY_DAYS_OLD_ARTIFACT =
        "org/apache/maven/plugins/maven-install-plugin/2.2-SNAPSHOT/maven-install-plugin-2.2-20061118.060401-2.jar";

    public static final String PATH_TO_BY_DAYS_OLD_METADATA_DRIVEN_ARTIFACT =
        "org/codehaus/plexus/plexus-utils/1.4.3-SNAPSHOT/plexus-utils-1.4.3-20070113.163208-4.jar";

    public static final String PATH_TO_BY_RETENTION_COUNT_ARTIFACT =
        "org/jruby/plugins/jruby-rake-plugin/1.0RC1-SNAPSHOT/jruby-rake-plugin-1.0RC1-20070504.153317-1.jar";

    public static final String PATH_TO_BY_RETENTION_COUNT_POM =
        "org/codehaus/castor/castor-anttasks/1.1.2-SNAPSHOT/castor-anttasks-1.1.2-20070506.163513-2.pom";

    public static final String PATH_TO_TEST_ORDER_OF_DELETION =
        "org/apache/maven/plugins/maven-assembly-plugin/1.1.2-SNAPSHOT/maven-assembly-plugin-1.1.2-20070615.105019-3.jar";

    protected static final String RELEASES_TEST_REPO_ID = "releases-test-repo-one";

    protected static final String RELEASES_TEST_REPO_NAME = "Releases Test Repo One";

    private ManagedRepository config;

    private ManagedRepositoryContent repo;

    protected RepositoryPurge repoPurge;

    protected IMocksControl listenerControl;

    protected RepositoryListener listener;

    protected RepositorySession repositorySession;

    protected MetadataRepository metadataRepository;

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected NexusIndexer nexusIndexer;


    @Before
    public void setUp()
        throws Exception
    {

        removeMavenIndexes();

        listenerControl = EasyMock.createControl();

        listener = listenerControl.createMock( RepositoryListener.class );

        repositorySession = mock( RepositorySession.class );
        metadataRepository = mock( MetadataRepository.class );
        when( repositorySession.getRepository() ).thenReturn( metadataRepository );


    }

    @After
    public void tearDown()
        throws Exception
    {
        removeMavenIndexes();
        config = null;
        repo = null;

    }

    protected void removeMavenIndexes()
        throws Exception
    {
        for ( IndexingContext indexingContext : nexusIndexer.getIndexingContexts().values() )
        {
            nexusIndexer.removeIndexingContext( indexingContext, false );
        }
    }

    protected static String fixPath( String path )
    {
        if ( path.contains( " " ) )
        {
            LoggerFactory.getLogger( AbstractRepositoryPurgeTest.class.getName() ).error(
                "You are building and testing with a path: \n {} containing space. Consider relocating.", path );
            return path.replaceAll( " ", "&amp;20" );
        }
        return path;
    }

    public ManagedRepository getRepoConfiguration( String repoId, String repoName )
    {
        config = new ManagedRepository();
        config.setId( repoId );
        config.setName( repoName );
        config.setDaysOlder( TEST_DAYS_OLDER );
        String path = AbstractRepositoryPurgeTest.fixPath(
            Paths.get( "target/test-" + getName() + "/" + repoId ).toAbsolutePath().toString() );
        config.setLocation( path );
        config.setReleases( true );
        config.setSnapshots( true );
        config.setDeleteReleasedSnapshots( true );
        config.setRetentionCount( TEST_RETENTION_COUNT );

        return config;
    }

    public ManagedRepositoryContent getRepository()
        throws Exception
    {
        if ( repo == null )
        {
            repo = applicationContext.getBean( "managedRepositoryContent#default", ManagedRepositoryContent.class );
            repo.setRepository( getRepoConfiguration( TEST_REPO_ID, TEST_REPO_NAME ) );
        }

        return repo;
    }

    protected void assertDeleted( String path )
    {
        assertFalse( "File should have been deleted: " + path, Files.exists(Paths.get( path )) );
    }

    protected void assertExists( String path )
    {
        assertTrue( "File should exist: " + path, Files.exists(Paths.get(path)) );
    }

    protected Path getTestRepoRoot()
    {
        return Paths.get( "target/test-" + getName() + "/" + TEST_REPO_ID );
    }

    protected Path getTestRepoRootPath() {
        return Paths.get("target/test-"+getName()+"/"+TEST_REPO_ID);
    }

    protected String prepareTestRepos()
        throws Exception
    {
        removeMavenIndexes();
        Path testDir = Paths.get( AbstractRepositoryPurgeTest.fixPath( getTestRepoRoot().toAbsolutePath().toString() ) );
        org.apache.archiva.common.utils.FileUtils.deleteDirectory( testDir );
        Path sourceDir = Paths.get( Paths.get( "target/test-classes/" + TEST_REPO_ID ).toAbsolutePath().toString() );
        FileUtils.copyDirectory( sourceDir.toFile(), testDir.toFile() );

        Path releasesTestDir = Paths.get( AbstractRepositoryPurgeTest.fixPath(
            Paths.get( "target/test-" + getName() + "/" + RELEASES_TEST_REPO_ID ).toAbsolutePath().toString() ) );

        org.apache.archiva.common.utils.FileUtils.deleteDirectory( releasesTestDir );
        Path sourceReleasesDir =
            Paths.get( Paths.get( "target/test-classes/" + RELEASES_TEST_REPO_ID ).toAbsolutePath().toString() );
        FileUtils.copyDirectory( sourceReleasesDir.toFile(), releasesTestDir.toFile() );

        return AbstractRepositoryPurgeTest.fixPath( testDir.toAbsolutePath().toString() );
    }

    public String getName()
    {
        return StringUtils.substringAfterLast( getClass().getName(), "." );
    }

    protected List<ArtifactMetadata> getArtifactMetadataFromDir( String repoId, String projectName, Path repoDir, Path vDir ) throws IOException
    {
        Maven2RepositoryPathTranslator translator = new Maven2RepositoryPathTranslator( new ArrayList<>(  ) );
        return Files.find(vDir, 1,
                    (path, basicFileAttributes) -> basicFileAttributes.isRegularFile() && path.getFileName().toString().startsWith(projectName))
            .map( path ->
                                translator.getArtifactForPath( repoId, repoDir.relativize( path ).toString() )
            ).collect( Collectors.toList());
    }
}