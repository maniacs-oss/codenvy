/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.api.dao.sql;

import com.codenvy.api.account.metrics.MemoryUsedMetric;
import com.codenvy.api.account.metrics.MeterBasedStorage;
import com.codenvy.api.account.metrics.UsageInformer;
import com.codenvy.api.dao.sql.postgresql.Int8RangeType;

import org.eclipse.che.api.core.ServerException;
import org.postgresql.util.PGobject;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.codenvy.api.dao.sql.SqlDaoQueries.METRIC_INSERT;
import static com.codenvy.api.dao.sql.SqlDaoQueries.METRIC_SELECT_ACCOUNT_GB_WS_TOTAL;
import static com.codenvy.api.dao.sql.SqlDaoQueries.METRIC_SELECT_ID;
import static com.codenvy.api.dao.sql.SqlDaoQueries.METRIC_SELECT_RUNID;
import static com.codenvy.api.dao.sql.SqlDaoQueries.METRIC_SELECT_WORKSPACE_GB_TOTAL;
import static com.codenvy.api.dao.sql.SqlDaoQueries.METRIC_UPDATE;

/**
 * @author Sergii Kabashniuk
 */
public class SqlMeterBasedStorage implements MeterBasedStorage {


    private final ConnectionFactory connectionFactory;

    @Inject
    public SqlMeterBasedStorage(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public UsageInformer createMemoryUsedRecord(MemoryUsedMetric metric) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection
                    .prepareStatement(METRIC_INSERT, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    statement.setInt(1, metric.getAmount());
                    statement.setObject(2, new Int8RangeType(metric.getStartTime(),
                                                             metric.getStopTime(), true, true));
                    statement.setString(3, metric.getUserId());
                    statement.setString(4, metric.getAccountId());
                    statement.setString(5, metric.getWorkspaceId());
                    statement.setString(6, metric.getRunId());
                    statement.execute();
                    connection.commit();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (keys.next()) {
                            return new SQLUsageInformer(keys.getInt(1),
                                                        metric,
                                                        connectionFactory);
                        } else {
                            throw new ServerException("Can't find inserted record id");
                        }
                    }


                } catch (SQLException | ServerException e) {
                    connection.rollback();
                    if (e.getLocalizedMessage().contains("conflicts with existing key (frun_id, fduring)")) {
                        throw new ServerException("Metric with given id and period already exist");
                    }
                    throw new ServerException(e.getLocalizedMessage(), e);
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }

    }

    /**
     * Get memory metric from storage.
     *
     * @param id
     *         - id of metrics
     * @return - Memory metric from storage if it exists or null.
     * @throws ServerException
     */

    MemoryUsedMetric getMetric(long id) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(METRIC_SELECT_ID)) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Int8RangeType range = new Int8RangeType((PGobject)resultSet.getObject("FDURING"));
                        return new MemoryUsedMetric(
                                resultSet.getInt("FAMOUNT"),
                                range.getFrom(),
                                range.getUntil(),
                                resultSet.getString("FUSER_ID"),
                                resultSet.getString("FACCOUNT_ID"),
                                resultSet.getString("FWORKSPACE_ID"),
                                resultSet.getString("FRUN_ID")
                        );
                    }
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    /**
     * Get memory metric from storage.
     *
     * @param runId
     *         - id of run.
     * @return - Memory metric from storage if it exists or null.
     * @throws ServerException
     */
    List<MemoryUsedMetric> getMetricsByRunId(String runId) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(METRIC_SELECT_RUNID)) {
                statement.setString(1, runId);
                List<MemoryUsedMetric> result = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        result.add(new MemoryUsedMetric(
                                resultSet.getInt(1),
                                resultSet.getLong(2),
                                resultSet.getLong(3),
                                resultSet.getString(4),
                                resultSet.getString(5),
                                resultSet.getString(6),
                                resultSet.getString(7)
                        ));
                    }
                    return result;
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public Map<String, Double> getMemoryUsedReport(String accountId, long from, long until) throws ServerException {
        Map<String, Double> result = new HashMap<>();
        try (Connection connection = connectionFactory.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(METRIC_SELECT_ACCOUNT_GB_WS_TOTAL)) {
                Int8RangeType range = new Int8RangeType(from, until, true, true);
                statement.setObject(1, range);
                statement.setObject(2, range);
                statement.setString(3, accountId);
                statement.setObject(4, range);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        result.put(resultSet.getString(2), resultSet.getDouble(1));
                    }

                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
        return result;
    }

    @Override
    public Double getUsedMemoryByWorkspace(String workspaceId, long from, long until) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(METRIC_SELECT_WORKSPACE_GB_TOTAL)) {
                Int8RangeType range = new Int8RangeType(from, until, true, true);
                statement.setObject(1, range);
                statement.setObject(2, range);
                statement.setString(3, workspaceId);
                statement.setObject(4, range);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getDouble(1);
                    }
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
        return 0D;
    }

    static final class SQLUsageInformer implements UsageInformer {

        private long recordId;

        private final MemoryUsedMetric metric;

        private final ConnectionFactory connectionFactory;

        private boolean isResourceUsageStopped = false;

        private SQLUsageInformer(long recordId, MemoryUsedMetric metric, ConnectionFactory connectionFactory) {
            this.recordId = recordId;
            this.metric = metric;
            this.connectionFactory = connectionFactory;
        }


        @Override
        public void resourceInUse() throws ServerException {
            if (!isResourceUsageStopped) {
                try (Connection connection = connectionFactory.getConnection()) {
                    try {
                        connection.setAutoCommit(false);
                        try (PreparedStatement statement = connection.prepareStatement(METRIC_UPDATE)) {
                            statement.setObject(1, new Int8RangeType(metric.getStartTime(),
                                                                     System.currentTimeMillis(), true, true));
                            statement.setLong(2, recordId);
                            statement.execute();
                        }
                        connection.commit();
                    } catch (SQLException e) {
                        connection.rollback();
                        throw e;
                    }
                } catch (SQLException e) {
                    throw new ServerException(e.getLocalizedMessage(), e);
                }
            }
        }

        @Override
        public void resourceUsageStopped() throws ServerException {
            resourceInUse();
            isResourceUsageStopped = true;
        }

        long getRecordId() {
            return recordId;
        }
    }
}
