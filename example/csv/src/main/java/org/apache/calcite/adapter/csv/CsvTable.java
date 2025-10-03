/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.csv;



import static org.apache.calcite.sql.SqlCollation.IMPLICIT;


import com.google.common.collect.ImmutableMap;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

import org.apache.calcite.adapter.file.CsvEnumerator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.DynamicRecordTypeImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataTypeFamily;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeImpl;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.SqlCollation;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.type.MapSqlType;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for table that reads CSV files.
 */
public abstract class CsvTable extends AbstractTable {
  protected final Source source;
  protected final @Nullable RelProtoDataType protoRowType;
  private @Nullable RelDataType rowType;
  private @Nullable List<RelDataType> fieldTypes;

  /** Creates a CsvTable. */
  CsvTable(Source source, @Nullable RelProtoDataType protoRowType) {
    this.source = source;
    this.protoRowType = protoRowType;
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    RelDataType extendMapType =
        new ExtendDataTypeFactory(typeFactory.getTypeSystem()).createExtendMapType(
            typeFactory.createSqlType(SqlTypeName.VARCHAR),
            typeFactory.createTypeWithNullability(
                typeFactory.createSqlType(SqlTypeName.ANY),
                true),
            ImmutableMap.of()
        );
    return typeFactory.builder().add("_MAP", extendMapType).build();
  }

  /** Returns the field types of this CSV table. */
  public List<RelDataType> getFieldTypes(RelDataTypeFactory typeFactory) {
    if (fieldTypes == null) {
      fieldTypes = new ArrayList<>();
      CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source,
          fieldTypes, isStream());
    }
    return fieldTypes;
  }

  /** Returns whether the table represents a stream. */
  protected boolean isStream() {
    return false;
  }

  /** Various degrees of table "intelligence". */
  public enum Flavor {
    SCANNABLE, FILTERABLE, TRANSLATABLE, DYNAMIC
  }

  public static class ExtendDataTypeFactory extends RelDataTypeFactoryImpl {


    /**
     * Creates a type factory.
     *
     * @param typeSystem
     */
    protected ExtendDataTypeFactory(RelDataTypeSystem typeSystem) {
      super(typeSystem);
    }

    public RelDataType createExtendMapType(
        RelDataType keyType,
        RelDataType valueType,
        Map<String, RelDataType> knownType) {
      ExtendMapType newType = new ExtendMapType(keyType, valueType, false);
      newType.knownType =  knownType;
      return canonize(newType);
    }

    @Override
    public RelDataType createArrayType(RelDataType elementType, long maxCardinality) {
      return null;
    }

    @Override
    public RelDataType createMapType(RelDataType keyType, RelDataType valueType) {
      return null;
    }

    @Override
    public RelDataType createFunctionSqlType(RelDataType parameterType, RelDataType returnType) {
      return null;
    }

    @Override
    public RelDataType createMeasureType(RelDataType valueType) {
      return null;
    }

    @Override
    public RelDataType createMultisetType(RelDataType elementType, long maxCardinality) {
      return null;
    }

    @Override
    public RelDataType createTypeWithCharsetAndCollation(RelDataType type, Charset charset,
        SqlCollation collation) {
      return null;
    }

    @Override
    public RelDataType createSqlType(SqlTypeName typeName) {
      return null;
    }

    @Override
    public RelDataType createUnknownType() {
      return null;
    }

    @Override
    public RelDataType createSqlType(SqlTypeName typeName, int precision) {
      return null;
    }

    @Override
    public RelDataType createSqlType(SqlTypeName typeName, int precision, int scale) {
      return null;
    }

    @Override
    public RelDataType createSqlIntervalType(SqlIntervalQualifier intervalQualifier) {
      return null;
    }

    class ExtendMapType extends MapSqlType {
      public Map<String, RelDataType> knownType;

      public ExtendMapType(RelDataType keyType, RelDataType valueType, boolean isNullable) {
        super(keyType, valueType, isNullable);
      }
    }
  }
}
