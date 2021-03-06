/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.sqlserver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.Session;
import io.trino.spi.type.TimeZoneKey;
import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import io.trino.testing.TestingSession;
import io.trino.testing.datatype.CreateAndInsertDataSetup;
import io.trino.testing.datatype.CreateAsSelectDataSetup;
import io.trino.testing.datatype.DataSetup;
import io.trino.testing.datatype.SqlDataTypeTest;
import io.trino.testing.sql.JdbcSqlExecutor;
import io.trino.testing.sql.TrinoSqlExecutor;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.ZoneId;
import java.util.Properties;

import static io.trino.plugin.sqlserver.SqlServerQueryRunner.createSqlServerQueryRunner;
import static io.trino.spi.type.TimestampType.createTimestampType;
import static io.trino.spi.type.VarbinaryType.VARBINARY;
import static java.time.ZoneOffset.UTC;

public class TestSqlServerTypeMapping
        extends AbstractTestQueryFramework
{
    private TestingSqlServer sqlServer;

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        sqlServer = closeAfterClass(new TestingSqlServer());
        sqlServer.start();
        return createSqlServerQueryRunner(
                sqlServer,
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableList.of());
    }

    @Test
    public void testVarbinary()
    {
        SqlDataTypeTest.create()
                .addRoundTrip("varbinary", "NULL", VARBINARY, "CAST(NULL AS varbinary)")
                .addRoundTrip("varbinary", "X''", VARBINARY, "X''")
                .addRoundTrip("varbinary", "X'68656C6C6F'", VARBINARY, "to_utf8('hello')")
                .addRoundTrip("varbinary", "X'5069C4996B6E6120C582C4856B61207720E69DB1E4BAACE983BD'", VARBINARY, "to_utf8('Piękna łąka w 東京都')")
                .addRoundTrip("varbinary", "X'4261672066756C6C206F6620F09F92B0'", VARBINARY, "to_utf8('Bag full of 💰')")
                .addRoundTrip("varbinary", "X'0001020304050607080DF9367AA7000000'", VARBINARY, "X'0001020304050607080DF9367AA7000000'") // non-text
                .addRoundTrip("varbinary", "X'000000000000'", VARBINARY, "X'000000000000'")
                .execute(getQueryRunner(), trinoCreateAsSelect("test_varbinary"));
    }

    @Test(dataProvider = "testTimestampDataProvider")
    public void testTimestamp(ZoneId sessionZone)
    {
        SqlDataTypeTest tests = SqlDataTypeTest.create()

                // before epoch
                .addRoundTrip("timestamp(3)", "TIMESTAMP '1958-01-01 13:18:03.123'", createTimestampType(3), "TIMESTAMP '1958-01-01 13:18:03.123'")
                // after epoch
                .addRoundTrip("timestamp(3)", "TIMESTAMP '2019-03-18 10:01:17.987'", createTimestampType(3), "TIMESTAMP '2019-03-18 10:01:17.987'")
                // time doubled in JVM zone
                .addRoundTrip("timestamp(3)", "TIMESTAMP '2018-10-28 01:33:17.456'", createTimestampType(3), "TIMESTAMP '2018-10-28 01:33:17.456'")
                // time double in Vilnius
                .addRoundTrip("timestamp(3)", "TIMESTAMP '2018-10-28 03:33:33.333'", createTimestampType(3), "TIMESTAMP '2018-10-28 03:33:33.333'")
                // epoch
                .addRoundTrip("timestamp(3)", "TIMESTAMP '1970-01-01 00:00:00.000'", createTimestampType(3), "TIMESTAMP '1970-01-01 00:00:00.000'")
                // time gap in JVM zone
                .addRoundTrip("timestamp(3)", "TIMESTAMP '1970-01-01 00:13:42.000'", createTimestampType(3), "TIMESTAMP '1970-01-01 00:13:42.000'")
                .addRoundTrip("timestamp(3)", "TIMESTAMP '2018-04-01 02:13:55.123'", createTimestampType(3), "TIMESTAMP '2018-04-01 02:13:55.123'")
                // time gap in Vilnius
                .addRoundTrip("timestamp(3)", "TIMESTAMP '2018-03-25 03:17:17.000'", createTimestampType(3), "TIMESTAMP '2018-03-25 03:17:17.000'")
                // time gap in Kathmandu
                .addRoundTrip("timestamp(3)", "TIMESTAMP '1986-01-01 00:13:07.000'", createTimestampType(3), "TIMESTAMP '1986-01-01 00:13:07.000'")

                // same as above but with higher precision
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1958-01-01 13:18:03.1230000'", createTimestampType(7), "TIMESTAMP '1958-01-01 13:18:03.1230000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '2019-03-18 10:01:17.9870000'", createTimestampType(7), "TIMESTAMP '2019-03-18 10:01:17.9870000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '2018-10-28 01:33:17.4560000'", createTimestampType(7), "TIMESTAMP '2018-10-28 01:33:17.4560000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '2018-10-28 03:33:33.3330000'", createTimestampType(7), "TIMESTAMP '2018-10-28 03:33:33.3330000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:00:00.0000000'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.0000000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:13:42.0000000'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:13:42.0000000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '2018-04-01 02:13:55.1230000'", createTimestampType(7), "TIMESTAMP '2018-04-01 02:13:55.1230000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '2018-03-25 03:17:17.0000000'", createTimestampType(7), "TIMESTAMP '2018-03-25 03:17:17.0000000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1986-01-01 00:13:07.0000000'", createTimestampType(7), "TIMESTAMP '1986-01-01 00:13:07.0000000'")

                // test arbitrary time for all supported precisions
                .addRoundTrip("timestamp(0)", "TIMESTAMP '1970-01-01 00:00:00'", createTimestampType(0), "TIMESTAMP '1970-01-01 00:00:00'")
                .addRoundTrip("timestamp(1)", "TIMESTAMP '1970-01-01 00:00:00.1'", createTimestampType(1), "TIMESTAMP '1970-01-01 00:00:00.1'")
                .addRoundTrip("timestamp(2)", "TIMESTAMP '1970-01-01 00:00:00.12'", createTimestampType(2), "TIMESTAMP '1970-01-01 00:00:00.12'")
                .addRoundTrip("timestamp(3)", "TIMESTAMP '1970-01-01 00:00:00.123'", createTimestampType(3), "TIMESTAMP '1970-01-01 00:00:00.123'")
                .addRoundTrip("timestamp(4)", "TIMESTAMP '1970-01-01 00:00:00.1234'", createTimestampType(4), "TIMESTAMP '1970-01-01 00:00:00.1234'")
                .addRoundTrip("timestamp(5)", "TIMESTAMP '1970-01-01 00:00:00.12345'", createTimestampType(5), "TIMESTAMP '1970-01-01 00:00:00.12345'")
                .addRoundTrip("timestamp(6)", "TIMESTAMP '1970-01-01 00:00:00.123456'", createTimestampType(6), "TIMESTAMP '1970-01-01 00:00:00.123456'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:00:00.1234567'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234567'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:00:00.12345670'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234567'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:00:00.123456749999'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234567'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:00:00.12345675'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234568'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1970-01-01 00:00:00.12345679'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234568'")

                // before epoch with second fraction
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1969-12-31 23:59:59.1230000'", createTimestampType(7), "TIMESTAMP '1969-12-31 23:59:59.1230000'")
                .addRoundTrip("timestamp(7)", "TIMESTAMP '1969-12-31 23:59:59.1234567'", createTimestampType(7), "TIMESTAMP '1969-12-31 23:59:59.1234567'")

                // precision 0 ends up as precision 0
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00'", "TIMESTAMP '1970-01-01 00:00:00'")

                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.1'", "TIMESTAMP '1970-01-01 00:00:00.1'")
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.9'", "TIMESTAMP '1970-01-01 00:00:00.9'")
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.123'", "TIMESTAMP '1970-01-01 00:00:00.123'")
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.123000'", "TIMESTAMP '1970-01-01 00:00:00.123000'")
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.999'", "TIMESTAMP '1970-01-01 00:00:00.999'")
                // max supported precision
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.1234567'", "TIMESTAMP '1970-01-01 00:00:00.1234567'")

                .addRoundTrip("TIMESTAMP '2020-09-27 12:34:56.1'", "TIMESTAMP '2020-09-27 12:34:56.1'")
                .addRoundTrip("TIMESTAMP '2020-09-27 12:34:56.9'", "TIMESTAMP '2020-09-27 12:34:56.9'")
                .addRoundTrip("TIMESTAMP '2020-09-27 12:34:56.123'", "TIMESTAMP '2020-09-27 12:34:56.123'")
                .addRoundTrip("TIMESTAMP '2020-09-27 12:34:56.123000'", "TIMESTAMP '2020-09-27 12:34:56.123000'")
                .addRoundTrip("TIMESTAMP '2020-09-27 12:34:56.999'", "TIMESTAMP '2020-09-27 12:34:56.999'")
                // max supported precision
                .addRoundTrip("TIMESTAMP '2020-09-27 12:34:56.1234567'", "TIMESTAMP '2020-09-27 12:34:56.1234567'")

                // round down
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.12345671'", "TIMESTAMP '1970-01-01 00:00:00.1234567'")

                // nanos round up, end result rounds down
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.1234567499'", "TIMESTAMP '1970-01-01 00:00:00.1234567'")
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.123456749999'", "TIMESTAMP '1970-01-01 00:00:00.1234567'")

                // round up
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.12345675'", "TIMESTAMP '1970-01-01 00:00:00.1234568'")

                // max precision
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.111222333444'", "TIMESTAMP '1970-01-01 00:00:00.1112223'")

                // round up to next second
                .addRoundTrip("TIMESTAMP '1970-01-01 00:00:00.99999995'", "TIMESTAMP '1970-01-01 00:00:01.0000000'")

                // round up to next day
                .addRoundTrip("TIMESTAMP '1970-01-01 23:59:59.99999995'", "TIMESTAMP '1970-01-02 00:00:00.0000000'")

                // negative epoch
                .addRoundTrip("TIMESTAMP '1969-12-31 23:59:59.99999995'", "TIMESTAMP '1970-01-01 00:00:00.0000000'")
                .addRoundTrip("TIMESTAMP '1969-12-31 23:59:59.999999949999'", "TIMESTAMP '1969-12-31 23:59:59.9999999'")
                .addRoundTrip("TIMESTAMP '1969-12-31 23:59:59.99999994'", "TIMESTAMP '1969-12-31 23:59:59.9999999'");

        Session session = Session.builder(getSession())
                .setTimeZoneKey(TimeZoneKey.getTimeZoneKey(sessionZone.getId()))
                .build();

        tests.execute(getQueryRunner(), session, trinoCreateAsSelect(session, "test_timestamp"));
        tests.execute(getQueryRunner(), session, trinoCreateAsSelect(getSession(), "test_timestamp"));
        tests.execute(getQueryRunner(), session, trinoCreateAndInsert(session, "test_timestamp"));
    }

    @Test
    public void testSqlServerDatetime2()
    {
        SqlDataTypeTest.create()
                // literal values with higher precision are NOT rounded and cause an error
                .addRoundTrip("DATETIME2(0)", "'1970-01-01 00:00:00'", createTimestampType(0), "TIMESTAMP '1970-01-01 00:00:00'")
                .addRoundTrip("DATETIME2(1)", "'1970-01-01 00:00:00.1'", createTimestampType(1), "TIMESTAMP '1970-01-01 00:00:00.1'")
                .addRoundTrip("DATETIME2(1)", "'1970-01-01 00:00:00.9'", createTimestampType(1), "TIMESTAMP '1970-01-01 00:00:00.9'")
                .addRoundTrip("DATETIME2(3)", "'1970-01-01 00:00:00.123'", createTimestampType(3), "TIMESTAMP '1970-01-01 00:00:00.123'")
                .addRoundTrip("DATETIME2(6)", "'1970-01-01 00:00:00.123000'", createTimestampType(6), "TIMESTAMP '1970-01-01 00:00:00.123000'")
                .addRoundTrip("DATETIME2(3)", "'1970-01-01 00:00:00.999'", createTimestampType(3), "TIMESTAMP '1970-01-01 00:00:00.999'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.1234567'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234567'")
                .addRoundTrip("DATETIME2(1)", "'2020-09-27 12:34:56.1'", createTimestampType(1), "TIMESTAMP '2020-09-27 12:34:56.1'")
                .addRoundTrip("DATETIME2(1)", "'2020-09-27 12:34:56.9'", createTimestampType(1), "TIMESTAMP '2020-09-27 12:34:56.9'")
                .addRoundTrip("DATETIME2(3)", "'2020-09-27 12:34:56.123'", createTimestampType(3), "TIMESTAMP '2020-09-27 12:34:56.123'")
                .addRoundTrip("DATETIME2(6)", "'2020-09-27 12:34:56.123000'", createTimestampType(6), "TIMESTAMP '2020-09-27 12:34:56.123000'")
                .addRoundTrip("DATETIME2(3)", "'2020-09-27 12:34:56.999'", createTimestampType(3), "TIMESTAMP '2020-09-27 12:34:56.999'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.1234567'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.1234567'")

                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.0000000'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.1'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1000000'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.9'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.9000000'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.123'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1230000'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.123000'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1230000'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.999'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.9990000'")
                .addRoundTrip("DATETIME2(7)", "'1970-01-01 00:00:00.1234567'", createTimestampType(7), "TIMESTAMP '1970-01-01 00:00:00.1234567'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.1'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.1000000'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.9'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.9000000'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.123'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.1230000'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.123000'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.1230000'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.999'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.9990000'")
                .addRoundTrip("DATETIME2(7)", "'2020-09-27 12:34:56.1234567'", createTimestampType(7), "TIMESTAMP '2020-09-27 12:34:56.1234567'")

                .execute(getQueryRunner(), sqlServerCreateAndInsert("test_sqlserver_timestamp"));
    }

    @DataProvider
    public Object[][] testTimestampDataProvider()
    {
        return new Object[][] {
                {UTC},
                {ZoneId.systemDefault()},
                // using two non-JVM zones so that we don't need to worry what SQL Server system zone is
                // no DST in 1970, but has DST in later years (e.g. 2018)
                {ZoneId.of("Europe/Vilnius")},
                // minutes offset change since 1970-01-01, no DST
                {ZoneId.of("Asia/Kathmandu")},
                {ZoneId.of(TestingSession.DEFAULT_TIME_ZONE_KEY.getId())},
        };
    }

    private DataSetup trinoCreateAsSelect(String tableNamePrefix)
    {
        return trinoCreateAsSelect(getSession(), tableNamePrefix);
    }

    private DataSetup trinoCreateAsSelect(Session session, String tableNamePrefix)
    {
        return new CreateAsSelectDataSetup(new TrinoSqlExecutor(getQueryRunner(), session), tableNamePrefix);
    }

    private DataSetup trinoCreateAndInsert(Session session, String tableNamePrefix)
    {
        return new CreateAndInsertDataSetup(new TrinoSqlExecutor(getQueryRunner(), session), tableNamePrefix);
    }

    private DataSetup sqlServerCreateAndInsert(String tableNamePrefix)
    {
        Properties properties = new Properties();
        properties.setProperty("user", sqlServer.getUsername());
        properties.setProperty("password", sqlServer.getPassword());
        return new CreateAndInsertDataSetup(new JdbcSqlExecutor(sqlServer.getJdbcUrl(), properties), tableNamePrefix);
    }
}
