package fr.esgi.tickrush.booking.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationStatusConstraintMigrationTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metadata;

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void updatesThePostgreSqlConstraintWithTheTicketIssuedState() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        ReservationStatusConstraintMigration migration =
                new ReservationStatusConstraintMigration(dataSource, jdbc);

        migration.run(new DefaultApplicationArguments());

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).execute(statements.capture());
        assertThat(statements.getAllValues().get(1)).contains("TICKET_ISSUED");
    }

    @Test
    void leavesTheSchemaManagedByHibernateForTheH2TestDatabase() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        ReservationStatusConstraintMigration migration =
                new ReservationStatusConstraintMigration(dataSource, jdbc);

        migration.run(new DefaultApplicationArguments());

        verifyNoInteractions(jdbc);
    }
}
