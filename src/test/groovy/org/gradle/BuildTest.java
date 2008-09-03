/*
 * Copyright 2002-2007 the original author or authors.
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

package org.gradle;

import org.gradle.api.Project;
import org.gradle.api.Settings;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.initialization.*;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildTest {
    private ProjectsLoader projectsLoaderMock;
    private ISettingsFinder settingsFinderMock;
    private IGradlePropertiesLoader gradlePropertiesLoaderMock;
    private SettingsProcessor settingsProcessorMock;
    private BuildConfigurer buildConfigurerMock;
    private File expectedCurrentDir;
    private File expectedRootDir;
    private File expectedGradleUserHomeDir;
    private DefaultProject expectedRootProject;
    private DefaultProject expectedCurrentProject;
    private URLClassLoader expectedClassLoader;
    private DefaultSettings settingsMock;
    private boolean expectedSearchUpwards;
    private Map expectedProjectProperties;
    private Map expectedSystemPropertiesArgs;
    private List<String> expectedTaskNames;
    private List<Iterable<Task>> expectedTasks;
    private StartParameter expectedStartParams;
    private BuildListener buildListenerMock;

    private Map testGradleProperties = new HashMap();

    private Build build;

    private BuildExecuter buildExecuterMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        HelperUtil.deleteTestDir();
        settingsFinderMock = context.mock(ISettingsFinder.class);
        gradlePropertiesLoaderMock = context.mock(IGradlePropertiesLoader.class);
        settingsMock = context.mock(DefaultSettings.class);
        buildExecuterMock = context.mock(BuildExecuter.class);
        settingsProcessorMock = context.mock(SettingsProcessor.class);
        projectsLoaderMock = context.mock(ProjectsLoader.class);
        buildConfigurerMock = context.mock(BuildConfigurer.class);
        buildListenerMock = context.mock(BuildListener.class);
        build = new Build(settingsFinderMock, gradlePropertiesLoaderMock, settingsProcessorMock, projectsLoaderMock,
                buildConfigurerMock, buildExecuterMock);
        testGradleProperties = WrapUtil.toMap(Project.SYSTEM_PROP_PREFIX + ".prop1", "value1");
        testGradleProperties.put("prop2", "value2");
        expectedSearchUpwards = false;
        expectedClassLoader = new URLClassLoader(new URL[0]);
        expectedProjectProperties = WrapUtil.toMap("prop", "value");
        expectedSystemPropertiesArgs = WrapUtil.toMap("systemProp", "systemPropValue");

        expectedRootDir = new File("rootDir");
        expectedCurrentDir = new File(expectedRootDir, "currentDir");
        expectedGradleUserHomeDir = new File(HelperUtil.TMP_DIR_FOR_TEST, "gradleUserHomeDir");

        expectedRootProject = HelperUtil.createRootProject(expectedRootDir);
        expectedCurrentProject = HelperUtil.createRootProject(expectedCurrentDir);

        expectTasks("a", "b");

        expectedStartParams = new StartParameter();
        expectedStartParams.setTaskNames(expectedTaskNames);
        expectedStartParams.setCurrentDir(expectedCurrentDir);
        expectedStartParams.setSearchUpwards(expectedSearchUpwards);
        expectedStartParams.setGradleUserHomeDir(expectedGradleUserHomeDir);
        expectedStartParams.setSystemPropertiesArgs(expectedSystemPropertiesArgs);
        expectedStartParams.setProjectProperties(expectedProjectProperties);

        context.checking(new Expectations() {
            {
                allowing(settingsFinderMock).find(with(any(StartParameter.class)));
                allowing(gradlePropertiesLoaderMock).loadProperties(with(equal(expectedRootDir)), with(any(StartParameter.class)));
                allowing(gradlePropertiesLoaderMock).getGradleProperties();
                will(returnValue(testGradleProperties));
                allowing(settingsFinderMock).getSettingsDir();
                will(returnValue(expectedRootDir));
                allowing(settingsMock).createClassLoader();
                will(returnValue(expectedClassLoader));
                allowing(projectsLoaderMock).getRootProject();
                will(returnValue(expectedRootProject));
                allowing(projectsLoaderMock).getCurrentProject();
                will(returnValue(expectedCurrentProject));
            }
        });
    }

    private void expectTasks(String... tasks) {
        expectedTaskNames = WrapUtil.toList(tasks);
        expectedTasks = new ArrayList<Iterable<Task>>();
        for (String task : tasks) {
            expectedTasks.add(WrapUtil.toSortedSet(expectedCurrentProject.createTask(task)));
        }
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testInit() {
        build = new Build(settingsFinderMock, gradlePropertiesLoaderMock, settingsProcessorMock, projectsLoaderMock,
                buildConfigurerMock, buildExecuterMock);
        assertSame(settingsFinderMock, build.getSettingsFinder());
        assertSame(gradlePropertiesLoaderMock, build.getGradlePropertiesLoader());
        assertSame(settingsProcessorMock, build.getSettingsProcessor());
        assertSame(projectsLoaderMock, build.getProjectLoader());
        assertSame(buildConfigurerMock, build.getBuildConfigurer());
        assertSame(buildExecuterMock, build.getBuildExecuter());
        assertEquals(new ArrayList(), build.getBuildListeners());
    }

    @Test
    public void testAddAndGetBuildListeners() {
        build.addBuildListener(buildListenerMock);
        assertEquals(WrapUtil.toList(buildListenerMock), build.getBuildListeners());
        BuildListener buildListenerMock2 = context.mock(BuildListener.class, "buildListener2");
        build.addBuildListener(buildListenerMock2);
        assertEquals(WrapUtil.toList(buildListenerMock, buildListenerMock2), build.getBuildListeners());
    }

    @Test
    public void testRun() {
        expectSettingsBuilt();
        expectTasksRunWithDagRebuild();
        build.run(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testRunWithRebuildDagFalse() {
        expectSettingsBuilt();
        expectTasksRunWithoutDagRebuild();
        build.run(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testRunWithDefaultTasks() {
        expectSettingsBuilt();
        expectedStartParams.setTaskNames(new ArrayList<String>());
        expectedTaskNames = WrapUtil.toList("c", "d");
        expectedCurrentProject.setDefaultTasks(expectedTaskNames);
        expectTasks("c", "d");
        expectTasksRunWithoutDagRebuild();
        build.run(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testRunWithEmbeddedScript() {
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).createBasicSettings(settingsFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
            }
        });
        expectTasksRunWithDagRebuild();
        build.runNonRecursivelyWithCurrentDirAsRoot(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testNotifiesListenerOnBuildComplete() {
        expectSettingsBuilt();
        expectTasksRunWithDagRebuild();
        context.checking(new Expectations() {{
            one(buildListenerMock).buildFinished(with(result(settingsMock, nullValue(Throwable.class))));
        }});

        build.addBuildListener(buildListenerMock);
        build.run(expectedStartParams);
    }

    @Test
    public void testNotifiesListenerOnBuildCompleteWithFailure() {
        final RuntimeException failure = new RuntimeException();
        expectSettingsBuilt();
        expectTasksRunWithFailure(failure);
        context.checking(new Expectations() {{
            one(buildListenerMock).buildFinished(with(result(settingsMock, sameInstance(failure))));
        }});

        build.addBuildListener(buildListenerMock);

        try {
            build.run(expectedStartParams);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }

    private void expectSettingsBuilt() {
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).process(settingsFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
            }
        });
    }

    private void expectTasksRunWithoutDagRebuild() {
        context.checking(new Expectations() {
            {
                one(buildConfigurerMock).process(expectedRootProject);
                one(buildExecuterMock).execute(expectedTasks.get(0), expectedRootProject);
                will(returnValue(false));
                one(buildExecuterMock).execute(expectedTasks.get(1), expectedRootProject);
                will(returnValue(false));
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        testGradleProperties, System.getProperties(), System.getenv());
            }
        });
    }

    private void expectTasksRunWithDagRebuild() {
        context.checking(new Expectations() {
            {
                one(buildConfigurerMock).process(expectedRootProject);
                one(buildExecuterMock).execute(expectedTasks.get(0), expectedRootProject);
                will(returnValue(true));
                one(buildExecuterMock).execute(expectedTasks.get(1), expectedRootProject);
                will(returnValue(true));
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        testGradleProperties, System.getProperties(), System.getenv());
                one(buildConfigurerMock).process(expectedRootProject);
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams, testGradleProperties,
                        System.getProperties(), System.getenv());
            }
        });
    }

    private void expectTasksRunWithFailure(final Throwable failure) {
        context.checking(new Expectations() {
            {
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        testGradleProperties, System.getProperties(), System.getenv());
                one(buildConfigurerMock).process(expectedRootProject);
                one(buildExecuterMock).execute(expectedTasks.get(0), expectedRootProject);
                will(throwException(failure));
            }
        });
    }

    @Test(expected = UnknownTaskException.class)
    public void testRunWithUnknownTask() {
        expectedStartParams.setTaskNames(WrapUtil.toList("unknown"));
        context.checking(new Expectations() {
            {
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        testGradleProperties, System.getProperties(), System.getenv());
                one(settingsProcessorMock).process(settingsFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
                one(buildConfigurerMock).process(expectedRootProject);
            }
        });
        build.run(expectedStartParams);
    }

    @Test
    public void testTaskList() {
        setTaskExpectations();
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).process(settingsFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        testGradleProperties, System.getProperties(), System.getenv());
            }
        });
        build.taskList(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testTaskListEmbedded() {
        final StartParameter expectedStartParameterArg = StartParameter.newInstance(expectedStartParams);
        expectedStartParameterArg.setSearchUpwards(false);
        context.checking(new Expectations() {
            {
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParameterArg,
                        testGradleProperties, System.getProperties(), System.getenv());
                one(settingsProcessorMock).createBasicSettings(settingsFinderMock, expectedStartParameterArg);
                will(returnValue(settingsMock));
            }
        });
        setTaskExpectations();
        build.taskListNonRecursivelyWithCurrentDirAsRoot(expectedStartParameterArg);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    private void setTaskExpectations() {
        context.checking(new Expectations() {
            {
                one(buildConfigurerMock).taskList(expectedRootProject, true, expectedCurrentProject);
            }
        });
    }

    private void checkSystemProps(Map props) {
        assertFalse(System.getProperties().keySet().contains("prop2"));
        assertEquals(testGradleProperties.get(Project.SYSTEM_PROP_PREFIX + ".prop1"), System.getProperty("prop1"));
    }

    // todo: This test is rather weak. Make it stronger.
    //@Test
    public void testNewInstanceFactory() {
        File expectedPluginProps = new File("pluginProps");
        File expectedDefaultImports = new File("defaultImports");

        StartParameter startParameter = new StartParameter();
        startParameter.setBuildFileName("buildfile");
        startParameter.setDefaultImportsFile(new File("imports"));
        startParameter.setPluginPropertiesFile(new File("plugin"));
        Build build = Build.newInstanceFactory(startParameter).newInstance(
                "embeddedscript",
                new File("buildResolverDir"));
//        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
//        assertEquals(expectedDefaultImports, build.settingsProcessor.importsReader.defaultImportsFile)
        build = Build.newInstanceFactory(startParameter).newInstance(null, null);
//        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
    }

    private Matcher<BuildResult> result(final Settings expectedSettings, final Matcher<? extends Throwable> exceptionMatcher) {
        return new BaseMatcher<BuildResult>() {
            public void describeTo(Description description) {
                description.appendText("matching build result");
            }

            public boolean matches(Object actual) {
                BuildResult result = (BuildResult) actual;
                return (result.getSettings() == expectedSettings) && exceptionMatcher.matches(result.getFailure());
            }
        };
    }
}
