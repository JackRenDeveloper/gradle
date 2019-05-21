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
package org.gradle.api.plugins.quality;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.internal.JDependInvoker;
import org.gradle.api.plugins.quality.internal.JDependReportsImpl;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.ClosureBackedAction;

import javax.inject.Inject;

/**
 * Analyzes code with <a href="https://github.com/clarkware/jdepend">JDepend</a>.
 *
 * @deprecated JDepend is unmaintained and does not support bytecode compiled for Java 8 and above.
 */
@CacheableTask
@Deprecated
public class JDepend extends DefaultTask implements Reporting<JDependReports> {

    private FileCollection jdependClasspath;

    private FileCollection classesDirs;

    private final JDependReports reports;

    /**
     * The directories containing the classes to be analyzed.
     *
     * @since 4.0
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @SkipWhenEmpty
    public FileCollection getClassesDirs() {
        return classesDirs;
    }

    /**
     * The directories containing the classes to be analyzed.
     *
     * @since 4.0
     */
    public void setClassesDirs(FileCollection classesDirs) {
        this.classesDirs = classesDirs;
    }

    public JDepend() {
        reports = getObjectFactory().newInstance(JDependReportsImpl.class, this);
    }

    @Inject
    protected static ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * jdependTask {
     *   reports {
     *     xml {
     *       destination "build/jdepend.xml"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    @Override
    public JDependReports reports(Closure closure) {
        return reports(new ClosureBackedAction<JDependReports>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * jdependTask {
     *   reports {
     *     xml {
     *       destination "build/jdepend.xml"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction The configuration
     * @return The reports container
     */
    @Override
    public JDependReports reports(Action<? super JDependReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    @TaskAction
    public void run() {
        JDependInvoker.invoke(this);
    }

    /**
     * The class path containing the JDepend library to be used.
     */
    @Classpath
    public FileCollection getJdependClasspath() {
        return jdependClasspath;
    }

    /**
     * The class path containing the JDepend library to be used.
     */
    public void setJdependClasspath(FileCollection jdependClasspath) {
        this.jdependClasspath = jdependClasspath;
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final JDependReports getReports() {
        return reports;
    }
}
