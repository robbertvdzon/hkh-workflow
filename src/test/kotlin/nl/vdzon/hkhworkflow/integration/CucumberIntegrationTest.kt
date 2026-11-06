package nl.vdzon.hkhworkflow.integration

import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/** De scenario's in src/test/resources/features, tegen de hele app die eenmaal wordt gestart. Zie docs/integratietests.md. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
class CucumberIntegrationTest
