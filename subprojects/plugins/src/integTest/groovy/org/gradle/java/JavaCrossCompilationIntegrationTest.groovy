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

package org.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.FileUtils
import org.gradle.internal.jvm.JavaInfo
import org.gradle.test.fixtures.file.ClassFile
import org.junit.Assume

//@TargetVersions(["1.6", "1.7", "1.8", "9"])
class JavaCrossCompilationIntegrationTest extends AbstractIntegrationSpec {
    JavaVersion getJavaVersion() {
        JavaVersion.VERSION_1_9
    }

    JavaInfo getTarget() {
        return AvailableJavaHomes.getJdk(javaVersion)
    }

    def setup() {
        Assume.assumeNotNull(target)

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($javaVersion.majorVersion )
                }
            }
            tasks.withType(Javadoc) {
                options.noTimestamp = false
            }
        """

        file("src/main/java/Thing.java") << """
            /** Some thing. */
            public class Thing { }
        """
        executer.withArgument("-Porg.gradle.java.installations.paths=" + target.javaHome.absolutePath)
    }

    def "can compile source and run JUnit tests using target Java version"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
        """

        file("src/test/java/ThingTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class ThingTest {
                @Test
                public void verify() {
                    assertTrue(System.getProperty("java.version").startsWith("${javaVersion.majorVersion}"));
                }
            }
        """

        expect:
        succeeds '--info', 'test'
        new ClassFile(javaClassFile("Thing.class")).javaVersion == javaVersion
        new ClassFile(classFile("java", "test", "ThingTest.class")).javaVersion == javaVersion
    }

    def "can compile source and run TestNG tests using target Java version"() {
        given:
        buildFile << """
            dependencies { testImplementation 'org.testng:testng:6.8.8' }
        """

        file("src/test/java/ThingTest.java") << """
            import org.testng.annotations.Test;

            public class ThingTest {
                @Test
                public void verify() {
                    assert System.getProperty("java.version").startsWith("${javaVersion.majorVersion}.");
                }
            }
        """

        expect:
        succeeds '--info', 'test'
    }

    def "can build and run application using target Java version"() {
        given:
        buildFile << """
            apply plugin: 'application'

            application {
                mainClass = 'Main'
            }
        """

        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("java home: " + System.getProperty("java.home"));
                    System.out.println("java version: " + System.getProperty("java.version"));
                }
            }
        """

        expect:
        succeeds '--info', 'run'
        output.contains("java home: ${FileUtils.canonicalize(target.javaHome)}")
        output.contains("java version: ${javaVersion.majorVersion}")
    }

    def "can generate Javadocs using target Java version"() {
        expect:
        succeeds '--info', 'javadoc'
        file('build/docs/javadoc/Thing.html').text.matches("(?s).*Generated by javadoc \\(.*?\\Q${javaVersion.majorVersion}\\E.*")
    }
}
