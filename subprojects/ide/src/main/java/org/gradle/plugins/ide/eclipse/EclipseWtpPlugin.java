/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.Ear;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ear.EarPluginConvention;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.Facet;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.gradle.plugins.ide.eclipse.model.internal.WtpClasspathAttributeSupport;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin which configures the Eclipse Web Tools Platform.
 */
public class EclipseWtpPlugin extends IdePlugin {

    public static final String ECLIPSE_WTP_COMPONENT_TASK_NAME = "eclipseWtpComponent";
    public static final String ECLIPSE_WTP_FACET_TASK_NAME = "eclipseWtpFacet";
    public static final String WEB_LIBS_CONTAINER = "org.eclipse.jst.j2ee.internal.web.container";

    public final Instantiator instantiator;

    @Inject
    public EclipseWtpPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "eclipseWtp";
    }

    @Override
    protected void onApply(Project project) {
        project.getPluginManager().apply(EclipsePlugin.class);

        getLifecycleTask().configure(withDescription("Generates Eclipse wtp configuration files."));
        getCleanTask().configure(withDescription("Cleans Eclipse wtp configuration files."));

        project.getTasks().named(EclipsePlugin.ECLIPSE_TASK_NAME, dependsOn(getLifecycleTask()));
        project.getTasks().named(cleanName(EclipsePlugin.ECLIPSE_TASK_NAME), dependsOn(getCleanTask()));

        EclipseModel model = project.getExtensions().getByType(EclipseModel.class);

        configureEclipseProject(project, model);
        configureEclipseWtpComponent(project, model);
        configureEclipseWtpFacet(project, model);

        // do this after wtp is configured because wtp config is required to update classpath properly
        configureEclipseClasspath(project, model);
    }

    private static void configureEclipseClasspath(final Project project, final EclipseModel model) {
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            AfterEvaluateHelper.afterEvaluateOrExecute(project, project1 -> {
                Collection<Configuration> plusConfigurations = model.getClasspath().getPlusConfigurations();
                EclipseWtpComponent component = model.getWtp().getComponent();
                plusConfigurations.addAll(component.getRootConfigurations());
                plusConfigurations.addAll(component.getLibConfigurations());
            });

            model.getClasspath().getFile().whenMerged((Action<Classpath>) classpath -> new WtpClasspathAttributeSupport(project, model).enhance(classpath));
        });

        project.getPlugins().withType(WarPlugin.class, warPlugin -> model.getClasspath().containers(WEB_LIBS_CONTAINER));
    }

    private void configureEclipseWtpComponent(final Project project, final EclipseModel model) {
        XmlTransformer xmlTransformer = new XmlTransformer();
        xmlTransformer.setIndentation("\t");
        final EclipseWtpComponent component = project.getObjects().newInstance(EclipseWtpComponent.class, project, new XmlFileContentMerger(xmlTransformer));
        model.getWtp().setComponent(component);

        TaskProvider<GenerateEclipseWtpComponent> task = project.getTasks().register(ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent.class, component);
        task.configure(task1 -> {
            task1.setDescription("Generates the Eclipse WTP component settings file.");
            task1.setInputFile(project.file(".settings/org.eclipse.wst.common.component"));
            task1.setOutputFile(project.file(".settings/org.eclipse.wst.common.component"));
        });
        addWorker(task, ECLIPSE_WTP_COMPONENT_TASK_NAME);

        ((IConventionAware) component).getConventionMapping().map("deployName", (Callable<String>) () -> model.getProject().getName());

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            if (hasWarOrEarPlugin(project)) {
                return;
            }

            Set<Configuration> libConfigurations = component.getLibConfigurations();

            libConfigurations.add(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            component.setClassesDeployPath("/");
            ((IConventionAware) component).getConventionMapping().map("libDeployPath", (Callable<String>) () -> "../");
            ((IConventionAware) component).getConventionMapping().map("sourceDirs", (Callable<Set<File>>) () -> getMainSourceDirs(project));
        });
        project.getPlugins().withType(WarPlugin.class, warPlugin -> {
            Set<Configuration> libConfigurations = component.getLibConfigurations();
            Set<Configuration> minusConfigurations = component.getMinusConfigurations();

            libConfigurations.add(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            minusConfigurations.add(project.getConfigurations().getByName("providedRuntime"));
            component.setClassesDeployPath("/WEB-INF/classes");
            ConventionMapping convention = ((IConventionAware) component).getConventionMapping();
            convention.map("libDeployPath", (Callable<String>) () -> "/WEB-INF/lib");
            convention.map("contextPath", (Callable<String>) () -> ((War) project.getTasks().getByName("war")).getBaseName());
            convention.map("resources", (Callable<List<WbResource>>) () -> Lists.newArrayList(new WbResource("/", project.getConvention().getPlugin(WarPluginConvention.class).getWebAppDirName())));
            convention.map("sourceDirs", (Callable<Set<File>>) () -> getMainSourceDirs(project));
        });
        project.getPlugins().withType(EarPlugin.class, earPlugin -> {
            Set<Configuration> libConfigurations = component.getLibConfigurations();
            Set<Configuration> rootConfigurations = component.getRootConfigurations();

            rootConfigurations.clear();
            rootConfigurations.add(project.getConfigurations().getByName("deploy"));
            libConfigurations.clear();
            libConfigurations.add(project.getConfigurations().getByName("earlib"));
            component.setClassesDeployPath("/");
            final ConventionMapping convention = ((IConventionAware) component).getConventionMapping();
            convention.map("libDeployPath", (Callable<String>) () -> {
                String deployPath = ((Ear) project.getTasks().findByName(EarPlugin.EAR_TASK_NAME)).getLibDirName();
                if (!deployPath.startsWith("/")) {
                    deployPath = "/" + deployPath;
                }

                return deployPath;
            });
            convention.map("sourceDirs", (Callable<Set<File>>) () -> project.getLayout().files(project.getConvention().getPlugin(EarPluginConvention.class).getAppDirName()).getFiles());
            project.getPlugins().withType(JavaPlugin.class, javaPlugin -> convention.map("sourceDirs", (Callable<Set<File>>) () -> getMainSourceDirs(project)));
        });
    }

    private void configureEclipseWtpFacet(final Project project, final EclipseModel eclipseModel) {
        TaskProvider<GenerateEclipseWtpFacet> task = project.getTasks().register(ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet.class, eclipseModel.getWtp().getFacet());
        task.configure(task1 -> {
            task1.setDescription("Generates the Eclipse WTP facet settings file.");
            task1.setInputFile(project.file(".settings/org.eclipse.wst.common.project.facet.core.xml"));
            task1.setOutputFile(project.file(".settings/org.eclipse.wst.common.project.facet.core.xml"));
        });
        addWorker(task, ECLIPSE_WTP_FACET_TASK_NAME);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            if (hasWarOrEarPlugin(project)) {
                return;
            }

            ((IConventionAware) eclipseModel.getWtp().getFacet()).getConventionMapping().map("facets", (Callable<List<Facet>>) () -> Lists.newArrayList(
                new Facet(Facet.FacetType.fixed, "jst.java", null),
                new Facet(Facet.FacetType.installed, "jst.utility", "1.0"),
                new Facet(Facet.FacetType.installed, "jst.java", toJavaFacetVersion(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility()))
            ));
        });
        project.getPlugins().withType(WarPlugin.class, warPlugin -> ((IConventionAware) eclipseModel.getWtp().getFacet()).getConventionMapping().map("facets", (Callable<List<Facet>>) () -> Lists.newArrayList(
            new Facet(Facet.FacetType.fixed, "jst.java", null),
            new Facet(Facet.FacetType.fixed, "jst.web", null),
            new Facet(Facet.FacetType.installed, "jst.web", "2.4"),
            new Facet(Facet.FacetType.installed, "jst.java", toJavaFacetVersion(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility()))
        )));
        project.getPlugins().withType(EarPlugin.class, earPlugin -> ((IConventionAware) eclipseModel.getWtp().getFacet()).getConventionMapping().map("facets", (Callable<List<Facet>>) () -> Lists.newArrayList(
            new Facet(Facet.FacetType.fixed, "jst.ear", null),
            new Facet(Facet.FacetType.installed, "jst.ear", "5.0")
        )));
    }

    private static void configureEclipseProject(final Project project, final EclipseModel model) {
        Action<Object> action = ignored -> {
            model.getProject().buildCommand("org.eclipse.wst.common.project.facet.core.builder");
            model.getProject().buildCommand("org.eclipse.wst.validation.validationbuilder");
            model.getProject().natures("org.eclipse.wst.common.project.facet.core.nature");
            model.getProject().natures("org.eclipse.wst.common.modulecore.ModuleCoreNature");
            model.getProject().natures("org.eclipse.jem.workbench.JavaEMFNature");
        };
        project.getPlugins().withType(JavaPlugin.class, action);
        project.getPlugins().withType(EarPlugin.class, action);
    }

    static boolean hasWarOrEarPlugin(Project project) {
        return project.getPlugins().hasPlugin(WarPlugin.class) || project.getPlugins().hasPlugin(EarPlugin.class);
    }

    static Set<File> getMainSourceDirs(Project project) {
        return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main").getAllSource().getSrcDirs();
    }

    static String toJavaFacetVersion(JavaVersion version) {
        if (version.equals(JavaVersion.VERSION_1_5)) {
            return "5.0";
        }

        if (version.equals(JavaVersion.VERSION_1_6)) {
            return "6.0";
        }

        return version.toString();
    }
}
