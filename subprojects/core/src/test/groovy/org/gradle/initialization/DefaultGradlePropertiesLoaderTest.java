/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.api.Project;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.GUtil;
import org.gradle.util.SetSystemProperties;
import org.gradle.util.WrapUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefaultGradlePropertiesLoaderTest {
    private DefaultGradlePropertiesLoader gradlePropertiesLoader;
    private File gradleUserHomeDir;
    private File settingsDir;
    private File gradleInstallationHomeDir;
    private Map<String, String> systemProperties = new HashMap<String, String>();
    private Map<String, String> envProperties = new HashMap<String, String>();
    private StartParameterInternal startParameter = new StartParameterInternal();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties();

    @Before
    public void setUp() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome");
        settingsDir = tmpDir.createDir("settingsDir");
        gradleInstallationHomeDir = tmpDir.createDir("gradleInstallationHome");
        gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter);
        startParameter.setGradleUserHomeDir(gradleUserHomeDir);
        startParameter.setGradleHomeDir(gradleInstallationHomeDir);
    }

    private void writePropertyFile(File location, Map<String, String> propertiesMap) {
        Properties properties = new Properties();
        properties.putAll(propertiesMap);
        GUtil.saveProperties(properties, new File(location, Project.GRADLE_PROPERTIES));
    }

    @Test
    public void mergeAddsPropertiesFromInstallationPropertiesFile() {
        writePropertyFile(gradleInstallationHomeDir, GUtil.map("settingsProp", "settings value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("settings value", properties.get("settingsProp"));
    }

    @Test
    public void mergeAddsPropertiesFromUserPropertiesFile() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("userProp", "user value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("user value", properties.get("userProp"));
    }

    @Test
    public void mergeAddsPropertiesFromSettingsPropertiesFile() {
        writePropertyFile(settingsDir, GUtil.map("settingsProp", "settings value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("settings value", properties.get("settingsProp"));
    }

    @Test
    public void mergeAddsPropertiesFromEnvironmentVariablesWithPrefix() {
        envProperties = GUtil.map(
                IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX + "envProp", "env value",
                "ignoreMe", "ignored");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("env value", properties.get("envProp"));
    }

    @Test
    public void mergeAddsPropertiesFromSystemPropertiesWithPrefix() {
        systemProperties = GUtil.map(
                IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp", "system value",
                "ignoreMe", "ignored");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("system value", properties.get("systemProp"));
    }

    @Test
    public void mergeAddsPropertiesFromStartParameter() {
        startParameter.setProjectProperties(GUtil.map("paramProp", "param value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("param value", properties.get("paramProp"));
    }

    @Test
    public void projectPropertiesHavePrecedenceOverInstallationPropertiesFile() {
        writePropertyFile(gradleInstallationHomeDir, GUtil.map("prop", "settings value"));
        Map<String, String> projectProperties = GUtil.map("prop", "project value");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(projectProperties);

        assertEquals("project value", properties.get("prop"));
    }

    @Test
    public void projectPropertiesHavePrecedenceOverSettingsPropertiesFile() {
        writePropertyFile(settingsDir, GUtil.map("prop", "settings value"));
        Map<String, String> projectProperties = GUtil.map("prop", "project value");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(projectProperties);

        assertEquals("project value", properties.get("prop"));
    }

    @Test
    public void userPropertiesFileHasPrecedenceOverSettingsPropertiesFile() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("prop", "user value"));
        writePropertyFile(settingsDir, GUtil.map("prop", "settings value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());

        assertEquals("user value", properties.get("prop"));
    }

    @Test
    public void userPropertiesFileHasPrecedenceOverProjectProperties() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("prop", "user value"));
        writePropertyFile(settingsDir, GUtil.map("prop", "settings value"));
        Map<String, String> projectProperties = GUtil.map("prop", "project value");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(projectProperties);

        assertEquals("user value", properties.get("prop"));
    }

    @Test
    public void environmentVariablesHavePrecedenceOverProjectProperties() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("prop", "user value"));
        writePropertyFile(settingsDir, GUtil.map("prop", "settings value"));
        Map<String, String> projectProperties = GUtil.map("prop", "project value");
        envProperties = GUtil.map(IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(projectProperties);

        assertEquals("env value", properties.get("prop"));
    }

    @Test
    public void systemPropertiesHavePrecedenceOverEnvironmentVariables() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("prop", "user value"));
        writePropertyFile(settingsDir, GUtil.map("prop", "settings value"));
        Map<String, String> projectProperties = GUtil.map("prop", "project value");
        envProperties = GUtil.map(IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value");
        systemProperties = GUtil.map(IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value");

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(projectProperties);

        assertEquals("system value", properties.get("prop"));
    }

    @Test
    public void startParameterPropertiesHavePrecedenceOverSystemProperties() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("prop", "user value"));
        writePropertyFile(settingsDir, GUtil.map("prop", "settings value"));
        Map<String, String> projectProperties = GUtil.map("prop", "project value");
        envProperties = GUtil.map(IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value");
        systemProperties = GUtil.map(IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value");
        startParameter.setProjectProperties(GUtil.map("prop", "param value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(projectProperties);

        assertEquals("param value", properties.get("prop"));
    }

    @Test
    public void loadSetsSystemProperties() {
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("systemPropArgKey", "systemPropArgValue"));
        writePropertyFile(gradleUserHomeDir, GUtil.map(Project.SYSTEM_PROP_PREFIX + ".userSystemProp", "userSystemValue"));
        writePropertyFile(settingsDir, GUtil.map(
                Project.SYSTEM_PROP_PREFIX + ".userSystemProp", "settingsSystemValue",
                Project.SYSTEM_PROP_PREFIX + ".settingsSystemProp2", "settingsSystemValue2"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);

        assertEquals("userSystemValue", System.getProperty("userSystemProp"));
        assertEquals("settingsSystemValue2", System.getProperty("settingsSystemProp2"));
        assertEquals("systemPropArgValue", System.getProperty("systemPropArgKey"));
    }

    @Test
    public void loadPropertiesWithNoExceptionForNonexistentGradleInstallationHomeAndUserHomeAndSettingsDir() {
        tmpDir.getTestDirectory().deleteDir();
        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
    }

    @Test
    public void loadPropertiesWithNoExceptionIfGradleInstallationHomeIsNotKnown() {
        gradleInstallationHomeDir = null;
        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
    }

    @Test
    public void reloadsProperties() {
        writePropertyFile(settingsDir, GUtil.map("prop1", "value", "prop2", "value"));

        File otherSettingsDir = tmpDir.createDir("otherSettingsDir");
        writePropertyFile(otherSettingsDir, GUtil.map("prop1", "otherValue"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        Map<String, String> properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());
        assertEquals("value", properties.get("prop1"));
        assertEquals("value", properties.get("prop2"));

        gradlePropertiesLoader.loadProperties(otherSettingsDir, startParameter, systemProperties, envProperties);
        properties = gradlePropertiesLoader.mergeProperties(Collections.<String, String>emptyMap());
        assertEquals("otherValue", properties.get("prop1"));
        assertNull(properties.get("prop2"));
    }

    @Test
    public void buildSystemProperties() {
        System.setProperty("gradle-loader-test", "value");
        assertTrue(gradlePropertiesLoader.getAllSystemProperties().containsKey("gradle-loader-test"));
        assertEquals("value", gradlePropertiesLoader.getAllSystemProperties().get("gradle-loader-test"));
    }

    @Test
    public void startParameterSystemPropertiesHavePrecedenceOverPropertiesFiles() {
        writePropertyFile(gradleUserHomeDir, GUtil.map("systemProp.prop", "user value"));
        writePropertyFile(settingsDir, GUtil.map("systemProp.prop", "settings value"));
        systemProperties = GUtil.map("prop", "system value");
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("prop", "commandline value"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);

        assertEquals("commandline value", System.getProperty("prop"));
    }
}
