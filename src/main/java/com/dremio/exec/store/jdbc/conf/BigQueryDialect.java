package com.dremio.exec.store.jdbc.conf;

import javax.sql.DataSource;

import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;

import com.dremio.exec.store.jdbc.JdbcSchemaFetcherImpl;
import com.dremio.exec.store.jdbc.JdbcPluginConfig;
import com.dremio.exec.store.jdbc.dialect.arp.ArpDialect;
import com.dremio.exec.store.jdbc.dialect.arp.ArpYaml;


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
  static class BigQuerySchemaFetcher extends JdbcSchemaFetcherImpl {
    public BigQuerySchemaFetcher(JdbcPluginConfig config) {
      super(config);
    }

    @Override
    protected boolean usePrepareForColumnMetadata() {
      return true;
    }
  }

  @Override
  public JdbcSchemaFetcherImpl newSchemaFetcher(JdbcPluginConfig config) {
    return new BigQuerySchemaFetcher(config);
  }

  @Override
  public boolean supportsNestedAggregations() {
    return false;
  }

  @Override
  public void unparseCall(SqlWriter writer, SqlCall call, int leftPrec, int rightPrec) {
    if (call instanceof SqlSelect) {
      SqlSelect select = (SqlSelect) call;
      SqlNodeList group = select.getGroup();

      if (group != null) {
        for (int i = 0; i < group.size(); i++) {
          SqlNode sqlNode = group.get(i);
          if (sqlNode instanceof SqlLiteral) {
            SqlLiteral stringLiteral = (SqlLiteral) sqlNode;
            group.set(i, new SqlBasicCall(SqlStdOperatorTable.COALESCE, new SqlNode[]{stringLiteral}, SqlParserPos.ZERO));
          }
        }
      }
    }
    super.unparseCall(writer, call, leftPrec, rightPrec);
  }
}
