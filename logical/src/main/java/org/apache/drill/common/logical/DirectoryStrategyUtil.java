/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.common.logical;

import org.apache.drill.common.scanner.persistence.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 *
 */
public class DirectoryStrategyUtil {

  private static final Logger LOG = LoggerFactory.getLogger(DirectoryStrategy.class);

  /**
   * scan for implementations of {@see FormatPlugin}.
   *
   * @param classpathScan - Drill configuration object, used to find the packages to scan
   * @return - list of classes that implement the interface.
   */
  public static Set<Class<? extends DirectoryStrategy>> getSubTypes(final ScanResult
    classpathScan) {
    final Set<Class<? extends DirectoryStrategy>> pluginClasses = classpathScan
      .getImplementations(DirectoryStrategy.class);
    if (LOG.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      sb.append("Found ");
      sb.append(pluginClasses.size());
      sb.append("format plugin configuration classes:\n");
      for (Class<?> c : pluginClasses) {
        sb.append('\t');
        sb.append(c.getName());
        sb.append('\n');
      }
      LOG.debug(sb.toString());
    }
    return pluginClasses;
  }
}
