package org.gradle.plugins.performance

import spock.lang.Ignore

class PerformanceTestIntegrationTest extends AbstractIntegrationTest {
    @Ignore
    def "honors branch name in channel"() {
        buildFile << """
            plugins {
                id 'gradlebuild.int-test-image'
                id 'gradlebuild.performance-test'
            }
            subprojects {
                apply plugin: 'java'
            }
            def distributedPerformanceTests = tasks.withType(org.gradle.testing.DistributedPerformanceTest)
            distributedPerformanceTests.all {
                // resolve these tasks
            }
            task assertChannel {
                doLast {
                    distributedPerformanceTests.each { distributedPerformanceTest ->
                        assert distributedPerformanceTest.channel.endsWith("-myBranch")
                    }
                }
            }
        """

        settingsFile << """
            include 'internalPerformanceTesting', 'docs', 'launcher', 'apiMetadata'
        """
        expect:
        build("assertChannel")
    }
}
