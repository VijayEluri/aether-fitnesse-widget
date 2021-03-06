package fr.opensagres.fitnesse.widgets.internal;

/*
 * Copyright (c) 2010 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0, 
 * and you may not use this file except in compliance with the Apache License Version 2.0. 
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the Apache License Version 2.0 is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.MavenServiceLocator;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.installation.InstallationException;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import fr.opensagres.fitnesse.widgets.internal.eclipse.EclipseWorkspaceReader;

public class Aether {

	private RepositorySystem repositorySystem;
	private LocalRepository localRepository;

	public Aether() {

		this.repositorySystem = newManualSystem();

	}

	public void setLocalRepository(LocalRepository localRepository) {
		this.localRepository = localRepository;
	}

	//
	// Setting up the repository system with the mechanism to find components
	// and setting up the implementations to use. This would be much easier
	// using Guice, but we want Aether to be easily embedded.
	//
	private RepositorySystem newManualSystem() {
		MavenServiceLocator locator = new MavenServiceLocator();
		locator.setServices(WagonProvider.class, new ManualWagonProvider());
		locator.addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class);
		return locator.getService(RepositorySystem.class);
	}

	private RepositorySystemSession newSession() {
		MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		session.setWorkspaceReader(new EclipseWorkspaceReader());
		session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(localRepository));

		 session.setTransferListener( new TrivialTransferListener() );
		// session.setRepositoryListener( new ConsoleRepositoryListener( System.out ) );
		return session;
	}

	public AetherResult resolve(Artifact artifact) throws DependencyCollectionException, DependencyResolutionException  {
		RepositorySystemSession session = newSession();

		Dependency dependency = new Dependency(artifact, "runtime");

		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(dependency);

		DependencyRequest request = new DependencyRequest();
		collectRequest.setRepositories(repositories);
		DependencyNode rootNode = repositorySystem.collectDependencies(session, collectRequest).getRoot();
		request.setRoot(rootNode);
		request.setCollectRequest(collectRequest);
		repositorySystem.resolveDependencies(session, request);

		StringBuffer dump = new StringBuffer();
		displayTree(rootNode, "", dump);

		PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
		rootNode.accept(nlg);
		AetherResult aetherResult = new AetherResult(rootNode, nlg.getFiles(), nlg.getClassPath());
		System.out.println(aetherResult.getResolvedClassPath());
		return aetherResult;
	}

	public AetherResult resolve(String groupId, String artifactId, String version) throws DependencyCollectionException, DependencyResolutionException  {
		RepositorySystemSession session = newSession();

		Dependency dependency = new Dependency(new DefaultArtifact(groupId, artifactId, "", "jar", version), "runtime");

		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(dependency);
		collectRequest.setRepositories(repositories);

		DependencyNode rootNode = repositorySystem.collectDependencies(session, collectRequest).getRoot();
		DependencyRequest request = new DependencyRequest();
		
		request.setRoot(rootNode);
		request.setCollectRequest(collectRequest);
		
		
		repositorySystem.resolveDependencies(session, request);

		StringBuffer dump = new StringBuffer();
		displayTree(rootNode, "", dump);

		PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
		rootNode.accept(nlg);

		return new AetherResult(rootNode, nlg.getFiles(), nlg.getClassPath());
	}

	public void install(Artifact artifact, Artifact pom) throws InstallationException {
		RepositorySystemSession session = newSession();

		InstallRequest installRequest = new InstallRequest();
		installRequest.addArtifact(artifact).addArtifact(pom);

		repositorySystem.install(session, installRequest);
	}

	// public void deploy( Artifact artifact, Artifact pom, String remoteRepository )
	// throws DeploymentException
	// {
	// RepositorySystemSession session = newSession();
	//
	// RemoteRepository nexus = new RemoteRepository( "nexus", "default", remoteRepository );
	// Authentication authentication = new Authentication( "admin", "admin123" );
	// nexus.setAuthentication( authentication );
	//
	// DeployRequest deployRequest = new DeployRequest();
	// deployRequest.addArtifact( artifact ).addArtifact( pom );
	// deployRequest.setRepository( nexus );
	//
	// repositorySystem.deploy( session, deployRequest );
	// }

	private void displayTree(DependencyNode node, String indent, StringBuffer sb) {
		sb.append(indent + node.getDependency()).append("\n");
		indent += "  ";
		for (DependencyNode child : node.getChildren()) {
			displayTree(child, indent, sb);
		}
	}

	private List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();

	public void addRemoteRepository(RemoteRepository remoteRepository) {
		repositories.add(remoteRepository);

	}

}
