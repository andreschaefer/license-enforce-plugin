/**
 * MIT License
 *
 * Copyright (c) 2019 Namics AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.namics.oss.gradle.license

import org.apache.xerces.jaxp.SAXParserImpl
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File

class DependencyAnalyser(
    private val project: Project,
    private val analyseConfigurations: List<String>
) {

    fun analyse(): List<Dependency> {
        setupEnvironment()
        collectDependencies()
        return dependencyInformation()
    }

    /**
     * Setup configurations to collect dependencies.
     */
    private fun setupEnvironment() {
        // Create temporary configuration in order to store POM information
        project.configurations.create(POM_CONFIGURATION)
        project.configurations.forEach {
            try {
                it.isCanBeResolved = true
            } catch (ignore: Exception) {
                project.logger.info("cannot resolve configuration {}", it, ignore)
            }
        }
    }


    /**
     * Iterate through all configurations and collect dependencies.
     */
    private fun collectDependencies() {
        // Add POM information to our POM configuration
        val configurations = LinkedHashSet<Configuration>()

        project.configurations
            .filter { analyseConfigurations.contains(it.name) }
            .forEach { configurations.add(it) }

        configurations
            .filter { it.isCanBeResolved }
            .map { it.resolvedConfiguration }
            .map { it.lenientConfiguration }
            .flatMap { it.artifacts }
            .map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}@pom" }
            .forEach {
                project.configurations.getByName(POM_CONFIGURATION).dependencies.add(
                    project.dependencies.add(
                        POM_CONFIGURATION,
                        it
                    )
                )
            }
    }

    private fun dependencyInformation(): List<Dependency> {
        val artifacts = project.configurations
            .getByName(POM_CONFIGURATION).resolvedConfiguration.lenientConfiguration.artifacts
        return artifacts.map {
            val pom = it
            val file = pom.file
            val coordinates = pom.id.componentIdentifier.displayName
            val licenses = findLicenses(file)
            Dependency(coordinates, licenses)
        }.sortedBy { it.id }
    }


    @Suppress("TooGenericExceptionCaught")
    fun findLicenses(pomFile: File): List<License> {
        try {

            val parser = SAXParserImpl.JAXPSAXParser()
            parser.setFeature("http://xml.org/sax/features/external-general-entities", false)
            parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            parser.setFeature("http://xml.org/sax/features/namespaces", false)
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false)
            val reader = SAXReader(parser, false)
            val doc = reader.read(pomFile)

            if (ANDROID_SUPPORT_GROUP_ID == doc?.rootElement?.element("group")?.text) {
                return listOf(License(name = APACHE_LICENSE_NAME, url = APACHE_LICENSE_URL))
            }

            val lics = doc?.rootElement?.element("licenses")?.elements("license")
                ?.filterNotNull()
                ?.map { License(it.element("name")?.text ?: "", it.element("url")?.text ?: "") }

            if (lics != null)
                return lics

            val parent = doc.rootElement.element("parent")
            if (parent != null)
                return findLicenses(getParentPomFile(parent))

        } catch (e: Throwable) {
            project.logger.warn("Failed to analyse {}", pomFile, e)
        }
        return emptyList()
    }

    /**
     * Use Parent POM information when individual dependency license information is missing.
     */
    private fun getParentPomFile(parent: Element): File {
        val groupId = parent.element("groupId").text
        val artifactId = parent.element("artifactId").text
        val version = parent.element("version").text
        val dependency = "$groupId:$artifactId:$version@pom"

        // Add dependency to temporary configuration
        project.configurations.create(TEMP_POM_CONFIGURATION)

        project.configurations.getByName(TEMP_POM_CONFIGURATION).dependencies.add(
            project.dependencies.add(TEMP_POM_CONFIGURATION, dependency)
        )

        // resolve file
        val file = project.configurations
            .getByName(TEMP_POM_CONFIGURATION).resolvedConfiguration.lenientConfiguration.artifacts.iterator()
            .next().file

        // cleanup
        project.configurations.remove(project.configurations.getByName(TEMP_POM_CONFIGURATION))

        return file
    }


    companion object {
        private const val ANDROID_SUPPORT_GROUP_ID = "com.android.support"
        private const val APACHE_LICENSE_NAME = "Apache License 2.0"
        private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"

        private const val POM_CONFIGURATION = "dependencyAnalyserPoms"
        private const val TEMP_POM_CONFIGURATION = "dependencyAnalyserPomsTemp"
    }
}
