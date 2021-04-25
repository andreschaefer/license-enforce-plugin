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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

open class LicenseEnforceTask : DefaultTask() {

    @Input
    var dictionaries: List<String> = mutableListOf()

    @Input
    var allowedDependencies: List<String> = emptyList()

    @Input
    var allowedCategories: List<String> = emptyList()

    @Input
    var allowedLicenses: List<String> = emptyList()

    @Input
    var failOnMissingLicenseInformation: Boolean = true

    @Input
    var analyseConfigurations: List<String> = listOf("compile", "api", "implementation")

    private val dictionary = LicenseDictionary()


    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Enforces licenses of dependencies to comply with definitions."
    }

    @TaskAction
    fun enforce() {
        initializeDictionary()

        val dependencies: List<Dependency> = DependencyAnalyser(project, analyseConfigurations).analyse()

        val denied = dependencies
            .filter { it.licenses.isNotEmpty() }
            .filter { !allowed(it) }

        val noLicense = dependencies
            .filter { it.licenses.isEmpty() }
            .filter { !allowedDependency(it) }

        if (denied.isNotEmpty()) {
            val known = dictionary.knownLicenses()

            @Suppress("MaxLineLength")
            throw GradleException(
                "You are using dependencies licensed under a permitted license!"
                    + "\nYou may "
                    + "\n- configure additional license mappings to $LICENSE_DICTIONARY_FILE_NAME in \$projectDir"
                    + "\n- add further dictionaries in task config"
                    + "\n- add allowed categories / licenses / dependencies in task config"
                    + "\ne.g.:"
                    + "\n\ttasks.enforceLicenses {"
                    + "\n\t    dictionaries = \"pathToYourFile\" "
                    + "\n\t    allowedCategories = listOf(\"Apache\", \"BSD\", \"LGPL\", \"MIT\")"
                    + "\n\t    allowedLicenses = listOf(\"Apache-2.0\", \"https://not.in.dictionary/LICENSE-1.0\", \"Another unknown License 1.0 (AUL)\")"
                    + "\n\t    allowedDependencies = listOf(\"my.group:allowed.dependency:123\")"
                    + "\n\t}"
                    + "\n\nCurrently allowed:"
                    + "\nCategories: " + allowedCategories.distinct().sorted().joinToString()
                    + "\nLicenses: " + allowedLicenses.distinct().sorted().joinToString()
                    + "\nDependencies: " + allowedDependencies.distinct().sorted().joinToString()
                    + "\n\nCurrently avaliable in dictionaries:"
                    + "\nCategories: " + known.map { it.category }.distinct().sorted().joinToString()
                    + "\nLicenses: " + known.map { it.id }.distinct().sorted().joinToString()
                    + "\n\nDependencies with licenses not allowed: "
                    + denied.map { "$it" }.joinToString(prefix = "\n- ", postfix = "", separator = "- ")
            )
        }

        if (noLicense.isNotEmpty()) {
            val report = noLicense.map { it.id }.joinToString(separator = "\n- ", prefix = "\n- ")
            if (failOnMissingLicenseInformation)
                throw GradleException(
                    "You are using dependencies without licensing information!"
                        + "\nYou may:"
                        + "\n- verify license manually and add it to allowedDependencies in task config"
                        + "\n- disable failOnMissingLicenseInformation in task config"
                        + "\ne.g.:"
                        + "\n\ttasks.enforceLicenses {"
                        + "\n\t    failOnMissingLicenseInformation = false"
                        + "\n\t    allowedDependencies = listOf(\"my.group:allowed.dependency:123\")"
                        + "\n\t}"
                        + "\n\nDependencies without license information: "
                        + report
                )
            else {
                logger.warn("Dependencies without license information: {}", report)
            }
        }
    }

    private fun allowed(dependency: Dependency): Boolean {
        // dependency
        if (allowedDependency(dependency))
            return true

        return dependency.licenses.any { allowed(it) }
    }

    private fun allowedDependency(dependency: Dependency) =
        allowedDependencies.map { Dependency(it, emptyList()) }.any { dependency.matches(it) }

    private fun allowed(license: License) = allowedLicenses.contains(license.name)
        || allowedLicenses.contains(license.url)
        || allowed(lookup(license))

    private fun lookup(license: License) = dictionary.lookup(license.name) ?: dictionary.lookup(license.url)

    private fun allowed(license: LicenseDefinition?) =
        license?.let { (allowedCategories.contains(license.category) || allowedLicenses.contains(license.id)) } ?: true


    private fun initializeDictionary() {
        addProjectDictionary()
        addBundledDictionary()
        addConfiguredDictionaries()
    }

    private fun addProjectDictionary() =
        project.projectDir.resolve(LICENSE_DICTIONARY_FILE_NAME).takeIf {
            it.isFile
        }?.run {
            dictionary.addConfig(readText())
        }

    private fun addBundledDictionary() =
        LicenseEnforceTask::class.java.getResource(LICENSE_DICTIONARY_FILE_NAME)?.run {
            dictionary.addConfig(this.readText())
        }

    private fun addConfiguredDictionaries() =
        dictionaries
            .map { File(it) }
            .filter { it.isFile }
            .map { it.readText() }
            .map { File(it).readText() }
            .forEach { dictionary.addConfig(it) }

    companion object {
        const val LICENSE_DICTIONARY_FILE_NAME = "license-dictionary.yaml"
    }
}
