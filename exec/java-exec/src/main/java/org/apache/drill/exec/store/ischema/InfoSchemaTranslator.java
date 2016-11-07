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
