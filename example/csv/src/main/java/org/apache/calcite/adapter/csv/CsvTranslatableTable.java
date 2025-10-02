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


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.file.CsvEnumerator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;

import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.QueryableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.simple.JSONArray;

/**
 * Table based on a CSV file.
 */
public class CsvTranslatableTable extends CsvTable
    implements QueryableTable, TranslatableTable {
  /** Creates a CsvTable. */
  CsvTranslatableTable(Source source, @Nullable RelProtoDataType protoRowType) {
    super(source, protoRowType);
  }

  @Override public String toString() {
    return "CsvTranslatableTable";
  }

  /** Returns an enumerable over a given projection of the fields. */
  @SuppressWarnings("unused") // called from generated code
  public Enumerable<Tuple> project(final DataContext root,
      final int[] fields) {
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root);
    return new AbstractEnumerable<Tuple>() {
      @Override public Enumerator<Tuple> enumerator() {
        return new MockEnumerator();
      }
    };
  }

  @Override public Expression getExpression(SchemaPlus schema, String tableName,
      Class clazz) {
    return Schemas.tableExpression(schema, getElementType(), tableName, clazz);
  }

  @Override public Type getElementType() {
    return Object[].class;
  }

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
      SchemaPlus schema, String tableName) {
    throw new UnsupportedOperationException();
  }

  @Override public RelNode toRel(
      RelOptTable.ToRelContext context,
      RelOptTable relOptTable) {
    // Request all fields.
    final int fieldCount = relOptTable.getRowType().getFieldCount();
    final int[] fields = CsvEnumerator.identityList(fieldCount);
    return new CsvTableScan(context.getCluster(), relOptTable, this, fields);
  }


  public static class Tuple {
    public final Map<String, Object> data;

    public Tuple(Map<String, Object> data) {
      this.data = data;
    }

    public Object resolve(String path) {
      if(data.containsKey(path)) {
        Object v = data.get(path);
        if (v instanceof JSONArray) {
          return ((JSONArray) v).toArray();
        }
        return v;
      }
      return null;
    }

    public Tuple merge(String path, Object v) {
      if (data.containsKey(path)) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(data.get(path));
        jsonArray.add(v);
        data.put(path, jsonArray);
      } else {
        data.put(path, v);
      }
      return this;
    }

    @Override
    public String toString() {
      // Create a map with evaluated values for toString display
      Map<String, Object> evaluatedData = new java.util.LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : data.entrySet()) {
        evaluatedData.put(entry.getKey(), entry.getValue());
      }
      return "Tuple{" +
          "data=" + evaluatedData +
          '}';
    }
  }


  /**
   * Static UDF function to resolve field values from a Tuple.
   * This function can be called from SQL as: resolve(_TUPLE, 'fieldName')
   *
   * @param tupleObj The Tuple object to resolve from (passed as Object)
   * @param fieldName The field name to resolve
   * @return The field value, or null if not found
   */
  public static Object resolve(Object tupleObj, String fieldName) {
    if (tupleObj == null) {
      return null;
    }
    if (tupleObj instanceof Tuple) {
      return ((Tuple) tupleObj).resolve(fieldName);
    }
    return null;
  }

  /**
   * Static UDF function to merge a new field value into a Tuple.
   * This function can be called from SQL as: tuple_merge(_TUPLE, 'fieldName', value)
   *
   * @param tupleObj The Tuple object to merge into (passed as Object)
   * @param fieldName The field name to set/update
   * @param valueProvider The value provider (can be a constant value or supplier function)
   * @return A new Tuple with the merged field, or null if input tuple is null
   */
  public static Tuple merge(Object tupleObj, String fieldName, Object valueProvider) {
    if (tupleObj == null) {
      return null;
    }

    if (!(tupleObj instanceof Tuple)) {
      return null;
    }

    Tuple originalTuple = (Tuple) tupleObj;
    originalTuple.merge(fieldName, valueProvider);

    return originalTuple;
  }


  public static class MockEnumerator<Tuple>
      implements Enumerator<CsvTranslatableTable.Tuple> {

    private final List<CsvTranslatableTable.Tuple> list;
    {
      list = new ArrayList<>();
      Map<String, Object> v1 = new LinkedHashMap<>();
      v1.put("v", 1);
      Map<String, Object> v2 = new LinkedHashMap<>();
      v2.put("v", 2);
      list.add(new CsvTranslatableTable.Tuple(v1));
      list.add(new CsvTranslatableTable.Tuple(v2));
    }

    private final Iterator<CsvTranslatableTable.Tuple> iterator = list.iterator();
    private boolean hasNext = false;
    private CsvTranslatableTable.Tuple current;

    @Override
    public CsvTranslatableTable.Tuple current() {
      return current;
    }

    @Override
    public boolean moveNext() {
      if (iterator.hasNext()) {
        current = iterator.next();
        return true;
      }
      return false;
    }

    @Override
    public void reset() {
      // do nothing
    }

    @Override
    public void close() {
      // do nothing
    }
  }
}
