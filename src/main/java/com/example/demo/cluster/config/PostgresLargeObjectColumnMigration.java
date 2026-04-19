package com.example.demo.cluster.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresLargeObjectColumnMigration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PostgresLargeObjectColumnMigration.class);

	private final JdbcTemplate jdbcTemplate;

	public PostgresLargeObjectColumnMigration(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			if (!isPostgres(connection.getMetaData())) {
				log.debug("Skipping large object migration because the database is not PostgreSQL.");
				return null;
			}
			migrateColumn(connection, "cluster", "notes");
			migrateColumn(connection, "database_instance", "notes");
			migrateColumn(connection, "deployment_record", "command_text");
			migrateColumn(connection, "deployment_record", "stdout");
			migrateColumn(connection, "deployment_record", "stderr");
			return null;
		});
	}

	private boolean isPostgres(DatabaseMetaData metaData) throws SQLException {
		String productName = metaData.getDatabaseProductName();
		return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
	}

	private void migrateColumn(Connection connection, String tableName, String columnName) throws SQLException {
		if (!isOidColumn(connection, tableName, columnName)) {
			return;
		}

		String sql = String.format(
				"ALTER TABLE %s ALTER COLUMN %s TYPE text USING CASE WHEN %s IS NULL THEN NULL ELSE convert_from(lo_get(%s), 'UTF8') END",
				quoteIdentifier(tableName),
				quoteIdentifier(columnName),
				quoteIdentifier(columnName),
				quoteIdentifier(columnName)
		);

		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
			log.info("Migrated {}.{} from oid to text.", tableName, columnName);
		}
	}

	private boolean isOidColumn(Connection connection, String tableName, String columnName) throws SQLException {
		String sql = """
			select data_type
			from information_schema.columns
			where table_schema = current_schema()
			  and table_name = ?
			  and column_name = ?
			""";
		String dataType = jdbcTemplate.queryForObject(sql, String.class, tableName, columnName);
		return dataType != null && dataType.equalsIgnoreCase("oid");
	}

	private String quoteIdentifier(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}
