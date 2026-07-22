package fr.esgi.tickrush.booking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hibernate ddl-auto=update ne met pas à jour les CHECK générés pour un enum existant.
 * Cette migration locale reste nécessaire jusqu'à l'introduction de Flyway.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReservationStatusConstraintMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReservationStatusConstraintMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public ReservationStatusConstraintMigration(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }

        jdbc.execute("ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_status_check");
        jdbc.execute("ALTER TABLE reservations ADD CONSTRAINT reservations_status_check "
                + "CHECK (status IN ('PENDING','PAID','TICKET_ISSUED','EXPIRED','CANCELLED'))");
        log.info("Contrainte reservations_status_check alignée sur le cycle TP06");
    }
}
