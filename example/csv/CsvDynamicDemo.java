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

import org.apache.calcite.adapter.csv.CsvSchemaFactory;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Demonstration of CsvDynamicTable usage.
 * 
 * Shows how to query CSV files with dynamic schema where:
 * - Existing columns return actual values
 * - Non-existent columns return NULL
 * - No schema predefinition required
 */
public class CsvDynamicDemo {
  
  public static void main(String[] args) throws SQLException, IOException {
    System.out.println("=== CSV Dynamic Table Demo ===");
    
    // Create a sample CSV file
    File tempDir = createTempDirectory();
    File csvFile = new File(tempDir, "employees.csv");
    
    try (PrintWriter writer = new PrintWriter(csvFile)) {
      writer.println("id,name,department,salary");
      writer.println("1,Alice,Engineering,75000");
      writer.println("2,Bob,Marketing,65000");
      writer.println("3,Charlie,Engineering,80000");
    }
    
    System.out.println("Created CSV file: " + csvFile.getAbsolutePath());
    System.out.println("CSV content:");
    System.out.println("id,name,department,salary");
    System.out.println("1,Alice,Engineering,75000");
    System.out.println("2,Bob,Marketing,65000");
    System.out.println("3,Charlie,Engineering,80000");
    System.out.println();
    
    Connection connection = createConnection(tempDir, "DYNAMIC");
    
    try {
      Statement statement = connection.createStatement();
      
      // Demo 1: Query existing columns
      System.out.println("=== Demo 1: Query existing columns ===");
      System.out.println("SQL: SELECT id, name, department FROM employees");
      
      ResultSet rs = statement.executeQuery("SELECT id, name, department FROM employees");
      System.out.println("Results:");
      while (rs.next()) {
        System.out.printf("id=%s, name=%s, department=%s%n",
            rs.getString("id"), rs.getString("name"), rs.getString("department"));
      }
      rs.close();
      System.out.println();
      
      // Demo 2: Mix existing and non-existent columns
      System.out.println("=== Demo 2: Mix existing and non-existent columns ===");
      System.out.println("SQL: SELECT id, name, nonexistent_column, department FROM employees");
      
      rs = statement.executeQuery("SELECT id, name, nonexistent_column, department FROM employees");
      System.out.println("Results:");
      while (rs.next()) {
        System.out.printf("id=%s, name=%s, nonexistent=%s, department=%s%n",
            rs.getString("id"), 
            rs.getString("name"), 
            rs.getString("nonexistent_column"), // This will be NULL
            rs.getString("department"));
      }
      rs.close();
      System.out.println();
      
      // Demo 3: Query only non-existent columns
      System.out.println("=== Demo 3: Query non-existent columns ===");
      System.out.println("SQL: SELECT missing1, missing2, missing3 FROM employees");
      
      rs = statement.executeQuery("SELECT missing1, missing2, missing3 FROM employees");
      System.out.println("Results:");
      int rowCount = 0;
      while (rs.next()) {
        rowCount++;
        System.out.printf("missing1=%s, missing2=%s, missing3=%s%n",
            rs.getString("missing1"), 
            rs.getString("missing2"), 
            rs.getString("missing3"));
      }
      System.out.println("Returned " + rowCount + " rows (all with NULL values)");
      rs.close();
      System.out.println();
      
      // Demo 4: With type casting
      System.out.println("=== Demo 4: Type casting ===");
      System.out.println("SQL: SELECT CAST(id AS INTEGER), CAST(salary AS DECIMAL(10,2)), CAST(missing AS INTEGER)");
      
      rs = statement.executeQuery(
          "SELECT CAST(id AS INTEGER) as id_int, "
          + "CAST(salary AS DECIMAL(10,2)) as salary_dec, "
          + "CAST(missing_field AS INTEGER) as missing_int "
          + "FROM employees");
      System.out.println("Results:");
      while (rs.next()) {
        System.out.printf("id_int=%d, salary_dec=%s, missing_int=%s%n",
            rs.getInt("id_int"),
            rs.getBigDecimal("salary_dec"),
            rs.getObject("missing_int")); // NULL
      }
      rs.close();
      
      statement.close();
      
    } finally {
      connection.close();
      // Clean up
      csvFile.delete();
      tempDir.delete();
    }
    
    System.out.println();
    System.out.println("=== Demo completed successfully! ===");
    System.out.println("Key features demonstrated:");
    System.out.println("✓ Runtime field resolution");
    System.out.println("✓ NULL for non-existent columns");
    System.out.println("✓ No schema predefinition required");
    System.out.println("✓ Type conversion with CAST");
  }
  
  private static File createTempDirectory() throws IOException {
    File tempDir = File.createTempFile("csv-dynamic-demo", ".tmp");
    tempDir.delete();
    tempDir.mkdirs();
    tempDir.deleteOnExit();
    return tempDir;
  }
  
  private static Connection createConnection(File directory, String flavor) throws SQLException {
    Properties info = new Properties();
    info.put("model", jsonPath(directory, flavor));
    return DriverManager.getConnection("jdbc:calcite:", info);
  }
  
  private static String jsonPath(File directory, String flavor) {
    return "inline:"
        + "{\n"
        + "  version: '1.0',\n"
        + "  defaultSchema: 'SALES',\n"
        + "  schemas: [\n"
        + "    {\n"
        + "      name: 'SALES',\n"
        + "      type: 'custom',\n"
        + "      factory: '" + CsvSchemaFactory.class.getName() + "',\n"
        + "      operand: {\n"
        + "        directory: '" + directory.getAbsolutePath() + "',\n"
        + "        flavor: '" + flavor + "'\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
  }
}