/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.guice;

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.inject.Named;
import javax.sql.DataSource;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.h2.jdbcx.JdbcDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.mapper.ColumnMappers;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.mapper.RowMappers;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.guice.util.GuiceTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.google.inject.name.Names.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestOverrides {

    public static final String GLOBAL = "global";
    public static final String LOCAL = "local";

    @Inject
    @Named("test")
    public Jdbi jdbi;

    @BeforeEach
    public void setUp() throws Exception {
        DataSource ds = new JdbcDataSource();

        Injector inj = GuiceTestSupport.createTestInjector(
            new GlobalModule(),

            binder -> binder.bind(DataSource.class).annotatedWith(named("test")).toInstance(ds),
            new InstanceModule());
        inj.injectMembers(this);
    }

    @Test
    public void testRowMapperOverrides() throws Exception {
        RowMapper<String> mapper = jdbi.getConfig().get(RowMappers.class).findFor(String.class).orElseThrow(IllegalStateException::new);
        assertEquals(LOCAL, mapper.map(null, null));
    }

    @Test
    public void testColumnMapperLocalOverride() throws Exception {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);

        // unqualified local mapper overrides unqualified global mapper
        ColumnMapper<String> unqualifiedMapper = columnMappers.findFor(String.class).orElseThrow(IllegalStateException::new);
        assertEquals(LOCAL, unqualifiedMapper.map(null, 1, null));
    }

    @Test
    public void testColumnMapperGlobalOverride() throws Exception {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);

        // explicit global mapper
        ColumnMapper<String> globalMapper = columnMappers.findFor(QualifiedType.of(String.class).with(named("global")))
            .orElseThrow(IllegalStateException::new);
        assertEquals(GLOBAL, globalMapper.map(null, 1, null));
    }

    @Test
    public void testLocalColumnMapper() throws Exception {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);

        // explicit local mapper
        ColumnMapper<String> localMapper = columnMappers.findFor(QualifiedType.of(String.class).with(named("local")))
            .orElseThrow(IllegalStateException::new);
        assertEquals(LOCAL, localMapper.map(null, 1, null));
    }

    @Test
    public void testQualifiedLocalColumnMapper() throws Exception {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);

        // qualified local mapper overrides qualified global mapper
        ColumnMapper<String> qualifiedMapper = columnMappers.findFor(QualifiedType.of(String.class).with(named("qualified")))
            .orElseThrow(IllegalStateException::new);
        assertEquals(LOCAL, qualifiedMapper.map(null, 1, null));
    }

    @Test
    public void testDifferentMappers() throws Exception {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);
        ColumnMapper<String> unqualifiedMapper = columnMappers.findFor(String.class)
            .orElseThrow(IllegalStateException::new);
        ColumnMapper<String> globalMapper = columnMappers.findFor(QualifiedType.of(String.class).with(named("global")))
            .orElseThrow(IllegalStateException::new);
        ColumnMapper<String> localMapper = columnMappers.findFor(QualifiedType.of(String.class).with(named("local")))
            .orElseThrow(IllegalStateException::new);
        ColumnMapper<String> qualifiedMapper = columnMappers.findFor(QualifiedType.of(String.class).with(named("qualified")))
            .orElseThrow(IllegalStateException::new);

        assertNotSame(unqualifiedMapper, globalMapper);
        assertNotSame(unqualifiedMapper, localMapper);
        assertNotSame(unqualifiedMapper, qualifiedMapper);
        assertNotSame(globalMapper, localMapper);
        assertNotSame(globalMapper, qualifiedMapper);
        assertNotSame(localMapper, qualifiedMapper);
    }

    @Test
    public void testMissingMapperLookup() {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);

        assertFalse(columnMappers.findFor(QualifiedType.of(String.class).with(named("absent"))).isPresent());
    }

    @Test
    public void testUnqualifiedQualified() throws Exception {
        final ColumnMappers columnMappers = jdbi.getConfig().get(ColumnMappers.class);

        // lookup by type
        ColumnMapper<String> typeMapper = columnMappers.findFor(String.class).orElseThrow(IllegalStateException::new);

        // lookup by qualified
        ColumnMapper<String> qualifiedMapper = columnMappers.findFor(QualifiedType.of(String.class)).orElseThrow(IllegalStateException::new);

        // lands on the same instance
        assertSame(typeMapper, qualifiedMapper);
    }

    static class GlobalModule extends AbstractJdbiConfigurationModule {

        @Override
        public void configureJdbi() {
            bindRowMapper().toInstance(new TestingRowMapper(GLOBAL));

            bindColumnMapper().toInstance(new TestingColumnMapper(GLOBAL));
            bindColumnMapper(QualifiedType.of(String.class).with(named("global"))).toInstance(new TestingColumnMapper(GLOBAL));
            bindColumnMapper(QualifiedType.of(String.class).with(named("qualified"))).toInstance(new TestingColumnMapper(GLOBAL));
        }
    }

    static class InstanceModule extends AbstractJdbiDefinitionModule {

        InstanceModule() {
            super(named("test"));
        }

        @Override
        public void configureJdbi() {
            bindRowMapper().toInstance(new TestingRowMapper(LOCAL));

            bindColumnMapper().toInstance(new TestingColumnMapper(LOCAL));

            bindColumnMapper(QualifiedType.of(String.class).with(named("local"))).toInstance(new TestingColumnMapper(LOCAL));
            bindColumnMapper(QualifiedType.of(String.class).with(named("qualified"))).toInstance(new TestingColumnMapper(LOCAL));
        }
    }

    private static class TestingRowMapper implements RowMapper<String> {

        private final String mode;

        TestingRowMapper(String mode) {
            this.mode = mode;
        }

        @Override
        public String map(ResultSet rs, StatementContext ctx) throws SQLException {
            return mode;
        }
    }

    private static class TestingColumnMapper implements ColumnMapper<String> {

        private final String mode;

        TestingColumnMapper(String mode) {
            this.mode = mode;
        }

        @Override
        public String map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
            return mode;
        }
    }
}
