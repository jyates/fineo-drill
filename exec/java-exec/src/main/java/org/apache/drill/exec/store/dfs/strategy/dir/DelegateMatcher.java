/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.dfs.strategy.dir;

import org.apache.drill.common.logical.DirectoryStrategy;

/**
 * Matcher that just delegates to the underlying directory strategy to provide an index for the
 * delegate and then return <tt>true</tt> if that index is non-null
 */
class DelegateMatcher extends DirectoryColumnMatcherBase {
  private final DirectoryStrategy delegate;

  DelegateMatcher(DirectoryStrategy delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isDirectory(String path) {
    Integer index = delegate.getColumnIndex(path);
    return index != null;
  }
}
