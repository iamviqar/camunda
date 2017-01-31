package org.camunda.optimize.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Askar Akhmerov
 */
public class PropertyUtil {

  private static Logger logger = LoggerFactory.getLogger(PropertyUtil.class);

  public static Properties loadProperties(String resource) {
    Properties properties = new Properties();
    try {
      properties.load(
          PropertyUtil.class
              .getClassLoader()
              .getResourceAsStream(resource)
      );
    } catch (IOException ex) {
      logger.error("Unable to load test properties!", ex);
    }
    return properties;
  }

  public static Properties loadProperties() {
    return PropertyUtil.loadProperties("service.properties");
  }
}
