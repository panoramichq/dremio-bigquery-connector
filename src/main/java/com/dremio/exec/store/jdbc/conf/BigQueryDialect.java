package com.dremio.exec.store.jdbc.conf;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSetOperator;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.fun.SqlTrimFunction;

import com.dremio.exec.store.jdbc.JdbcSchemaFetcher;
import com.dremio.exec.store.jdbc.JdbcStoragePlugin;
import com.dremio.exec.store.jdbc.JdbcStoragePlugin.Config;
import com.dremio.exec.store.jdbc.dialect.arp.ArpDialect;
import com.dremio.exec.store.jdbc.dialect.arp.ArpYaml;
import com.google.common.collect.ImmutableList;


/**
 * Custom Dialect for BigQuery.
 * See Apache Calcite project for Dialect info
 * https://github.com/apache/calcite/blob/4208d0ba6f2a749692fe64181a1373af07d55db5/core/src/main/java/org/apache/calcite/sql/dialect/BigQuerySqlDialect.java
 */
public class BigQueryDialect extends ArpDialect {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BigQueryDialect.class);

  public BigQueryDialect(final ArpYaml yaml) {
    super(yaml);
  }

  /**
   * Custom Dialect for BigQuery.
   * The following block is required as BigQuery reports integers as NUMBER(38,0).
   */
  static class BigQuerySchemaFetcher extends JdbcSchemaFetcher {
    public BigQuerySchemaFetcher(String name, DataSource dataSource, int timeout, Config config) {
      super(name, dataSource, timeout, config);
    }

    @Override
    protected boolean usePrepareForColumnMetadata() {
      return true;
    }
  }

  private static final List<String> RESERVED_KEYWORDS =
      ImmutableList.copyOf(
          Arrays.asList("ALL", "AND", "ANY", "ARRAY", "AS", "ASC",
              "ASSERT_ROWS_MODIFIED", "AT", "BETWEEN", "BY", "CASE", "CAST",
              "COLLATE", "CONTAINS", "CREATE", "CROSS", "CUBE", "CURRENT",
              "DEFAULT", "DEFINE", "DESC", "DISTINCT", "ELSE", "END", "ENUM",
              "ESCAPE", "EXCEPT", "EXCLUDE", "EXISTS", "EXTRACT", "FALSE",
              "FETCH", "FOLLOWING", "FOR", "FROM", "FULL", "GROUP", "GROUPING",
              "GROUPS", "HASH", "HAVING", "IF", "IGNORE", "IN", "INNER",
              "INTERSECT", "INTERVAL", "INTO", "IS", "JOIN", "LATERAL", "LEFT",
              "LIKE", "LIMIT", "LOOKUP", "MERGE", "NATURAL", "NEW", "NO",
              "NOT", "NULL", "NULLS", "OF", "ON", "OR", "ORDER", "OUTER",
              "OVER", "PARTITION", "PRECEDING", "PROTO", "RANGE", "RECURSIVE",
              "RESPECT", "RIGHT", "ROLLUP", "ROWS", "SELECT", "SET", "SOME",
              "STRUCT", "TABLESAMPLE", "THEN", "TO", "TREAT", "TRUE",
              "UNBOUNDED", "UNION", "UNNEST", "USING", "WHEN", "WHERE",
              "WINDOW", "WITH", "WITHIN"));

  /** An unquoted BigQuery identifier must start with a letter and be followed
   * by zero or more letters, digits or _. */
  private static final Pattern IDENTIFIER_REGEX =
    Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

  @Override
  public JdbcSchemaFetcher getSchemaFetcher(final String name, final DataSource dataSource, final int timeout,
      final JdbcStoragePlugin.Config config) {
    return new BigQuerySchemaFetcher(name, dataSource, timeout, config);
  }

  @Override
  public boolean identifierNeedsToBeQuoted(String val) {
    return !IDENTIFIER_REGEX.matcher(val).matches()
        || RESERVED_KEYWORDS.contains(val.toUpperCase(Locale.ROOT));
  }

  @Override
  public SqlNode emulateNullDirection(SqlNode node, boolean nullsFirst, boolean desc) {
    return emulateNullDirectionWithIsNull(node, nullsFirst, desc);
  }

  @Override
  public boolean supportsNestedAggregations() {
    return false;
  }

  @Override
  public void unparseOffsetFetch(SqlWriter writer, SqlNode offset, SqlNode fetch) {
    unparseFetchUsingLimit(writer, offset, fetch);
  }

  @Override public void unparseCall(final SqlWriter writer, final SqlCall call, final int leftPrec,
      final int rightPrec) {
    switch (call.getKind()) {
    case UNION:
      if (((SqlSetOperator) call.getOperator()).isAll()) {
        super.unparseCall(writer, call, leftPrec, rightPrec);
      } else {
        SqlSyntax.BINARY.unparse(writer, UNION_DISTINCT, call, leftPrec,
            rightPrec);
      }
      break;
    case EXCEPT:
      if (((SqlSetOperator) call.getOperator()).isAll()) {
        throw new RuntimeException("BigQuery does not support EXCEPT ALL");
      }
      SqlSyntax.BINARY.unparse(writer, EXCEPT_DISTINCT, call, leftPrec,
          rightPrec);
      break;
    case INTERSECT:
      if (((SqlSetOperator) call.getOperator()).isAll()) {
        throw new RuntimeException("BigQuery does not support INTERSECT ALL");
      }
      SqlSyntax.BINARY.unparse(writer, INTERSECT_DISTINCT, call, leftPrec,
          rightPrec);
      break;
    case TRIM:
      unparseTrim(writer, call, leftPrec, rightPrec);
      break;
    default:
      super.unparseCall(writer, call, leftPrec, rightPrec);
    }
  }

  /**
   * For usage of TRIM, LTRIM and RTRIM in BQ see
   * <a href="https://cloud.google.com/bigquery/docs/reference/standard-sql/functions-and-operators#trim">
   *  BQ Trim Function</a>.
   */
  private void unparseTrim(SqlWriter writer, SqlCall call, int leftPrec,
      int rightPrec) {
    final String operatorName;
    SqlLiteral trimFlag = call.operand(0);
    SqlLiteral valueToTrim = call.operand(1);
    switch (trimFlag.getValueAs(SqlTrimFunction.Flag.class)) {
    case LEADING:
      operatorName = "LTRIM";
      break;
    case TRAILING:
      operatorName = "RTRIM";
      break;
    default:
      operatorName = call.getOperator().getName();
      break;
    }
    final SqlWriter.Frame trimFrame = writer.startFunCall(operatorName);
    call.operand(2).unparse(writer, leftPrec, rightPrec);

    // If the trimmed character is a non-space character, add it to the target SQL.
    // eg: TRIM(BOTH 'A' from 'ABCD'
    // Output Query: TRIM('ABC', 'A')
    if (!valueToTrim.toValue().matches("\\s+")) {
      writer.literal(",");
      call.operand(1).unparse(writer, leftPrec, rightPrec);
    }
    writer.endFunCall(trimFrame);
  }

  /**
   * List of BigQuery Specific Operators needed to form Syntactically Correct SQL.
   */
  private static final SqlOperator UNION_DISTINCT = new SqlSetOperator(
      "UNION DISTINCT", SqlKind.UNION, 14, false);

  private static final SqlSetOperator EXCEPT_DISTINCT =
      new SqlSetOperator("EXCEPT DISTINCT", SqlKind.EXCEPT, 14, false);

  private static final SqlSetOperator INTERSECT_DISTINCT =
      new SqlSetOperator("INTERSECT DISTINCT", SqlKind.INTERSECT, 18, false);
}
