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
package org.apache.drill.exec.store.ischema;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Function;

import javax.annotation.Nullable;

/**
 * Translate an info-schema object.
 */
@JsonTypeName("info-schema-translator")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public abstract class InfoSchemaTranslator<T> implements Function<T, T> {

  protected String user;

  public InfoSchemaTranslator setUser(String user) {
    this.user = user;
    return this;
  }

  @Nullable
  @Override
  public T apply(@Nullable T input) {
    if (input instanceof Records.Catalog) {
      return (T) handleCatalog((Records.Catalog) input);
    } else if (input instanceof Records.Schema) {
      return (T) handleSchema((Records.Schema) input);
    } else if (input instanceof Records.Table) {
      return (T) handleTable((Records.Table) input);
    } else if (input instanceof Records.View) {
      return (T) handleView((Records.View) input);
    } else if (input instanceof Records.Column) {
      return (T) handleColumn((Records.Column) input);
    } else {
      return handleUnknown(input);
    }
  }

  protected Records.Catalog handleCatalog(Records.Catalog input) {
    return input;
  }

  protected Records.Schema handleSchema(Records.Schema input) {
    return input;
  }

  protected Records.Table handleTable(Records.Table input) {
    return input;
  }

  protected Records.View handleView(Records.View input) {
    return input;
  }

  protected Records.Column handleColumn(Records.Column input) {
    return input;
  }

  protected T handleUnknown(T input) {
    throw new UnsupportedOperationException("Cannot handle: " + input);
  }
}