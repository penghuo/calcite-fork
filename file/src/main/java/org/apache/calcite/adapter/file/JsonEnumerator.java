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
package org.apache.calcite.adapter.file;

import static org.apache.calcite.runtime.rtti.RuntimeTypeInformation.RuntimeSqlTypeName.INTEGER;
import static org.apache.calcite.runtime.rtti.RuntimeTypeInformation.RuntimeSqlTypeName.VARCHAR;
import static org.apache.calcite.runtime.rtti.RuntimeTypeInformation.RuntimeSqlTypeName.VARIANT;

import java.math.RoundingMode;

import java.util.stream.Collectors;

import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.runtime.rtti.BasicSqlTypeRtti;
import org.apache.calcite.runtime.rtti.RuntimeTypeInformation;
import org.apache.calcite.runtime.variant.VariantNonNull;
import org.apache.calcite.runtime.variant.VariantNull;
import org.apache.calcite.runtime.variant.VariantSqlValue;
import org.apache.calcite.runtime.variant.VariantValue;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Source;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumerator that reads from a Object List.
 */
public class JsonEnumerator implements Enumerator<@Nullable Object[]> {

  private final Enumerator<@Nullable Object[]> enumerator;

  public JsonEnumerator(List<? extends @Nullable Object> list) {
    List<@Nullable Object[]> objs = new ArrayList<>();
    for (Object obj : list) {
      if (obj instanceof Collection) {
        //noinspection unchecked
        List<Object> tmp = (List<Object>) obj;
        objs.add(tmp.toArray());
      } else if (obj instanceof Map) {
        objs.add(((LinkedHashMap) obj).values().toArray());
      } else {
        objs.add(new Object[]{obj});
      }
    }
    enumerator = Linq4j.enumerator(objs);
  }

  /** Deduces the names and types of a table's columns by reading the first line
   * of a JSON file. */
  static JsonDataConverter deduceRowType(RelDataType relDataType, Source source) {
    final ObjectMapper objectMapper = new ObjectMapper();
    List<Object> list;
    LinkedHashMap<String, Object> jsonFieldMap = new LinkedHashMap<>(1);
    Object jsonObj = null;
    try {
      objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
          .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
          .configure(JsonParser.Feature.ALLOW_COMMENTS, true);

      if ("file".equals(source.protocol()) && source.file().exists()) {
        //noinspection unchecked
        jsonObj = objectMapper.readValue(source.file(), Object.class);
      } else if (Arrays.asList("http", "https", "ftp").contains(source.protocol())) {
        //noinspection unchecked
        jsonObj = objectMapper.readValue(source.url(), Object.class);
      } else {
        jsonObj = objectMapper.readValue(source.reader(), Object.class);
      }

    } catch (MismatchedInputException e) {
      if (!e.getMessage().contains("No content")) {
        throw new RuntimeException("Couldn't read " + source, e);
      }
    } catch (Exception e) {
      throw new RuntimeException("Couldn't read " + source, e);
    }

    if (jsonObj == null) {
      list = new ArrayList<>();
      jsonFieldMap.put("EmptyFileHasNoColumns", Boolean.TRUE);
    } else if (jsonObj instanceof Collection) {
      list = new ArrayList<>();
      for (Object o : ((Collection<?>) jsonObj)) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) o;
        for (RelDataTypeField relDataTypeField : relDataType.getFieldList()) {
          if (data.containsKey(relDataTypeField.getName())) {
            result.put(relDataTypeField.getName(), toVariant(data.get(relDataTypeField.getName())));
          } else {
            result.put(relDataTypeField.getName(), VariantNull.INSTANCE);
          }
        }
        list.add(result);
      }
    } else if (jsonObj instanceof Map) {
      //noinspection unchecked
      jsonFieldMap = (LinkedHashMap) jsonObj;
      //noinspection unchecked
      List<Object> tempList = new ArrayList(((LinkedHashMap) jsonObj).values());
      list = new ArrayList<>();
      for (Object o : tempList) {
        list.add(toVariant(o));
      }
    } else {
      jsonFieldMap.put("line", jsonObj);
      list = new ArrayList<>();
      list.add(0, jsonObj);
    }

    return new JsonDataConverter(relDataType, list);
  }

  public static VariantValue toVariant(Object o) {
    RuntimeTypeInformation.RuntimeSqlTypeName sqlTypeName = VARIANT;
    if (o instanceof Integer) {
      sqlTypeName=INTEGER;
    } else {
      sqlTypeName=VARCHAR;
    }
    return VariantSqlValue.create(RoundingMode.CEILING, o, new BasicSqlTypeRtti(sqlTypeName));
  }

  @Override public Object[] current() {
    return enumerator.current();
  }

  @Override public boolean moveNext() {
    return enumerator.moveNext();
  }

  @Override public void reset() {
    enumerator.reset();
  }

  @Override public void close() {
    enumerator.close();
  }

  /**
   * Json data and relDataType Converter.
   */
  static class JsonDataConverter {
    private final RelDataType relDataType;
    private final List<Object> dataList;

    private JsonDataConverter(RelDataType relDataType, List<Object> dataList) {
      this.relDataType = relDataType;
      this.dataList = dataList;
    }

    RelDataType getRelDataType() {
      return relDataType;
    }

    List<Object> getDataList() {
      return dataList;
    }
  }
}
