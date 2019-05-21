/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.devel.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.inject.Inject;

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX;

class IvyPluginPublishingPlugin implements Plugin<Project> {
    private final FeaturePreviews featurePreviews;

    @Inject
    IvyPluginPublishingPlugin(FeaturePreviews featurePreviews) {
        this.featurePreviews = featurePreviews;
    }

    @Override
    public void apply(Project project) {
        project.afterEvaluate(this::configurePublishing);
    }

    void configurePublishing(final Project project) {
        project.getExtensions().configure(PublishingExtension.class, publishing -> {
            final GradlePluginDevelopmentExtension pluginDevelopment = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);
            if (!pluginDevelopment.isAutomatedPublishing()) {
                return;
            }
            SoftwareComponent mainComponent = project.getComponents().getByName("java");
            IvyPublication mainPublication = addMainPublication(publishing, mainComponent);
            addMarkerPublications(mainPublication, publishing, pluginDevelopment);
        });
    }

    IvyPublication addMainPublication(PublishingExtension publishing, SoftwareComponent mainComponent) {
        IvyPublication publication = publishing.getPublications().maybeCreate("pluginIvy", IvyPublication.class);
        publication.from(mainComponent);
        return publication;
    }

    void addMarkerPublications(IvyPublication mainPublication, PublishingExtension publishing, GradlePluginDevelopmentExtension pluginDevelopment) {
        for (PluginDeclaration declaration : pluginDevelopment.getPlugins()) {
            createIvyMarkerPublication(declaration, mainPublication, publishing.getPublications());
        }
    }

    private void createIvyMarkerPublication(final PluginDeclaration declaration, final IvyPublication mainPublication, PublicationContainer publications) {
        String pluginId = declaration.getId();
        IvyPublicationInternal publication = (IvyPublicationInternal) publications.create(declaration.getName() + "PluginMarkerIvy", IvyPublication.class);
        publication.setAlias(true);
        publication.setOrganisation(pluginId);
        publication.setModule(pluginId + PLUGIN_MARKER_SUFFIX);
        publication.descriptor(descriptor -> {
            descriptor.description(description -> description.getText().set(declaration.getDescription()));
            descriptor.withXml(xmlProvider -> {
                Element root = xmlProvider.asElement();
                Document document = root.getOwnerDocument();
                Node dependencies = root.getElementsByTagName("dependencies").item(0);
                Node dependency = dependencies.appendChild(document.createElement("dependency"));
                Attr org = document.createAttribute("org");
                org.setValue(mainPublication.getOrganisation());
                dependency.getAttributes().setNamedItem(org);
                Attr name = document.createAttribute("name");
                name.setValue(mainPublication.getModule());
                dependency.getAttributes().setNamedItem(name);
                Attr rev = document.createAttribute("rev");
                rev.setValue(mainPublication.getRevision());
                dependency.getAttributes().setNamedItem(rev);
            });
        });
    }
}
