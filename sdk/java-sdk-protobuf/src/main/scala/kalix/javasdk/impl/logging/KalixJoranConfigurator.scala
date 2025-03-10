/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kalix.javasdk.impl.logging

import java.io.File

import ch.qos.logback.classic.ClassicConstants
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus
import ch.qos.logback.classic.util.DefaultJoranConfigurator
import ch.qos.logback.core.util.Loader

class KalixJoranConfigurator extends DefaultJoranConfigurator {

  private val devModeLogbackFile = "src/main/resources/logback-dev-mode.xml"

  // not really related to logging, but we can disable it here as well
  System.setProperty("spring.main.banner-mode", "off")

  private def loadDevModeLogback: Boolean = {

    // this is testing if logback-test.xml is on the classpath, which is the case when running tests
    val noLogbackTest = {
      // using as much as possible Logback classes to find the resource in the classpath
      val myClassLoader = Loader.getClassLoaderOfObject(this)
      val url = Loader.getResource(ClassicConstants.TEST_AUTOCONFIG_FILE, myClassLoader)
      url == null
    }

    // this file path only exist when running in dev-mode.
    // once packaged we don't have src/main/resources anymore
    def devModeLogbackExists = new File(sys.props("user.dir"), devModeLogbackFile).exists()

    noLogbackTest && devModeLogbackExists
  }

  override def configure(loggerContext: LoggerContext): ExecutionStatus = {
    if (loadDevModeLogback) {
      var loggingConfigSet = false
      var logbackConfigurationFile = false
      // only set it if not already set by user
      if (System.getProperty("logging.config") == null) {
        System.setProperty("logging.config", devModeLogbackFile)
        loggingConfigSet = true
      }
      // only set it if not already set by user
      if (System.getProperty("logback.configurationFile") == null) {
        System.setProperty("logback.configurationFile", devModeLogbackFile)
        logbackConfigurationFile = true
      }

      // let the DefaultJoranConfigurator do its thing
      val status = super.configure(loggerContext)

      // we can only log after we run 'configure'
      addInfo("Kalix application running in dev-mode");
      if (loggingConfigSet) addInfo("Setting logging.config to " + devModeLogbackFile);
      if (logbackConfigurationFile) addInfo("Setting logback.configurationFile to " + devModeLogbackFile);

      status

    } else {
      val status = super.configure(loggerContext)
      addInfo("Kalix application running in packaged mode")
      status
    }
  }
}
