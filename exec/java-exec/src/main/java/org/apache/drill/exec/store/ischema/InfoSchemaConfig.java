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
package org.apache.drill.exec.store.ischema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import org.apache.drill.common.logical.StoragePluginConfig;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SCHS_COL_SCHEMA_NAME;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_NAME;
import static org.apache.drill.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_SCHEMA;

public class InfoSchemaConfig extends StoragePluginConfig {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InfoSchemaConfig.class);

  public static final String NAME = "ischema";
  private Map<String, Collection<String>> catalogFilters;
  private Map<String, Collection<String>> tableSchemaFilters;
  private Map<String, Collection<String>> tableFilters;
  private Map<String, Collection<String>> viewFilters;
  private Map<String, Collection<String>> columnFilters;
  private InfoSchemaTranslator translator;

  public InfoSchemaConfig() {
  }

  @JsonCreator
  public InfoSchemaConfig(
    @JsonProperty("catalogs") Map<String, Collection<String>> catalogFilters,
    @JsonProperty("table_schema") Map<String, Collection<String>> tableSchemaFilters,
    @JsonProperty("tables") Map<String, Collection<String>> tableFilters,
    @JsonProperty("views") Map<String, Collection<String>> viewFilters,
    @JsonProperty("columns") Map<String, Collection<String>> columnFilters) {
    this.tableFilters = tableFilters;
    this.catalogFilters = catalogFilters;
    this.tableSchemaFilters = tableSchemaFilters;
    this.viewFilters = viewFilters;
    this.columnFilters = columnFilters;
  }

  @JsonIgnore
  public InfoSchemaUserFilters buildFilter(SelectedTable table) {
    InfoSchemaUserFilters filters = new InfoSchemaUserFilters();
    switch (table) {
      // tables support both table name and schema name
      // Table fields:
      // TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE
      case TABLES:
        filters.add(SHRD_COL_TABLE_NAME, tableFilters);
        filters.add(SHRD_COL_TABLE_SCHEMA, tableSchemaFilters);
        break;
      // schemata fields:
      // CATALOG_NAME, SCHEMA_NAME, SCHEMA_OWNER, TYPE, IS_MUTABLE
      //  * CATALOG_NAME => always DRILL, so skip it
      //  * SCHEMA_NAME => the full schema's that are available
      case SCHEMATA:
        filters.add(SCHS_COL_SCHEMA_NAME, tableSchemaFilters);
        break;
      // everything else doesn't check anything
      default:
        break;
    }
    return filters;
  }

  @Override
  public int hashCode() {
    return 1;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof InfoSchemaConfig;
  }

  private static Map<String, Collection<String>> getFilters(Config userFilter, String type) {
    Multimap<String, String> map = ArrayListMultimap.create();
    Config config;
    try {
      config = userFilter.getConfig(type);
    } catch (ConfigException.Missing e) {
      return Collections.emptyMap();
    }
    for (Map.Entry<String, ConfigValue> e : config.entrySet()) {
      for (String whitelist : config.getStringList(e.getKey())) {
        // should be one value here. Later we can get more creative and start supporting more
        // complex cases with nesting here
        map.put(ConfigUtil.splitPath(e.getKey()).get(0), whitelist);
      }
    }
    return map.asMap();
  }

  @JsonProperty("tables")
  public Map<String, Collection<String>> getTableFilters() {
    return tableFilters;
  }

  @JsonProperty("catalogs")
  public Map<String, Collection<String>> getCatalogFilters() {
    return catalogFilters;
  }

  @JsonProperty("table_schema")
  public Map<String, Collection<String>> getTableSchemaFilters() {
    return tableSchemaFilters;
  }

  @JsonProperty("views")
  public Map<String, Collection<String>> getViewFilters() {
    return viewFilters;
  }

  @JsonProperty("columns")
  public Map<String, Collection<String>> getColumnFilters() {
    return columnFilters;
  }

  public void setTableFilters(Map<String, Collection<String>> tableFilters) {
    this.tableFilters = tableFilters;
  }

  public void setCatalogFilters(Map<String, Collection<String>> catalogFilters) {
    this.catalogFilters = catalogFilters;
  }

  public void setTableSchemaFilters(Map<String, Collection<String>> tableSchemaFilters) {
    this.tableSchemaFilters = tableSchemaFilters;
  }

  public void setViewFilters(Map<String, Collection<String>> viewFilters) {
    this.viewFilters = viewFilters;
  }

  public void setColumnFilters(Map<String, Collection<String>> columnFilters) {
    this.columnFilters = columnFilters;
  }

  public static InfoSchemaConfig.Builder newBuilder() {
    return new Builder();
  }

  public void setTranslator(InfoSchemaTranslator translator) {
    this.translator = translator;
  }

  public InfoSchemaTranslator getTranslator() {
    return translator;
  }

  public static class Builder {

    private Config userFilters;
    private Config translator;

    public Builder withFilters(Config userFilters) {
      this.userFilters = userFilters;
      return this;
    }

    public Builder withTranslator(Config object) {
      this.translator = object;
      return this;
    }

    public InfoSchemaConfig build() {
      InfoSchemaConfig config = new InfoSchemaConfig();
      if (userFilters != null) {
        config.setCatalogFilters(getFilters(userFilters, "catalogs"));
        config.setTableSchemaFilters(getFilters(userFilters, "schemas"));
        config.setTableFilters(getFilters(userFilters, "tables"));
        config.setViewFilters(getFilters(userFilters, "views"));
        config.setColumnFilters(getFilters(userFilters, "columns"));
      }
      if (translator != null) {
        ObjectMapper mapper = new ObjectMapper();
        InjectableValues inject = new InjectableValues.Std();
        Map<String, Object> map = translator.root().unwrapped();
        try {
          String s = mapper.writeValueAsString(map);
          config.setTranslator(mapper.setInjectableValues(inject).readValue(s,
            InfoSchemaTranslator.class));
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return config;
    }
  }
}
