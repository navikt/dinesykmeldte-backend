package no.nav.syfo.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import java.util.Properties

class GcpDatabase(credentials: GcpDatabaseCredentials, database: String) : DatabaseInterface {
    private val dataSource: HikariDataSource
    override val connection: Connection
        get() = dataSource.connection

    init {
        val properties = Properties()
        properties.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
        properties.setProperty("cloudSqlInstance", credentials.connectionName)
        dataSource = HikariDataSource(
            HikariConfig().apply {
                dataSourceProperties = properties
                jdbcUrl = "jdbc:postgresql:///$database"
                username = credentials.username
                password = credentials.password
                maximumPoolSize = 1
                minimumIdle = 1
                isAutoCommit = false
                connectionTimeout = 10_000
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
