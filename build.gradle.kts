import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import com.github.jk1.license.render.JsonReportRenderer

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    id("com.github.jk1.dependency-license-report") version "2.9"
}

licenseReport {
    outputDir = "$buildDir/reports/dependency-license"
    projects = arrayOf(project(":app"))
    configurations = arrayOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    renderers = arrayOf(
        JsonReportRenderer("licenses.json"),
        InventoryMarkdownReportRenderer("licenses.md")
    )
    filters = arrayOf(LicenseBundleNormalizer())
}
