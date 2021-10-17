package com.highestpeak.util;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * sqlite帮助类，直接创建该类示例，并调用相应的借口即可对sqlite数据库进行操作
 * <p>
 * 本类基于 sqlite jdbc v56
 */
public class DatabaseHelper {

    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;
    private final String dbFilePath;

    /**
     * 构造函数
     *
     * @param dbFilePath sqlite db 文件路径
     */
    public DatabaseHelper(String dbFilePath) throws ClassNotFoundException, SQLException {
        this.dbFilePath = dbFilePath;
        connection = getConnection(dbFilePath);
    }

    /**
     * 获取数据库连接
     *
     * @param dbFilePath db文件路径
     * @return 数据库连接
     */
    public Connection getConnection(String dbFilePath) throws ClassNotFoundException, SQLException {
        Connection conn;
        // 初始化 sqlite 的类
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
        return conn;
    }

    /**
     * 执行sql查询
     *
     * @param sql sql select 语句
     * @param rse 结果集处理类对象
     * @return 查询结果
     */
    public <T> T executeQuery(String sql, ResultSetExtractor<T> rse) throws SQLException, ClassNotFoundException {
        resultSet = getStatement().executeQuery(sql);
        return rse.extractData(resultSet);
    }

    /**
     * 执行select查询，返回结果列表
     *
     * @param sql sql select 语句
     * @param rm  结果集的行数据处理类对象
     */
    public <T> List<T> executeQuery(String sql, RowMapper<T> rm) throws SQLException, ClassNotFoundException {
        List<T> rsList = new ArrayList<T>();
        resultSet = getStatement().executeQuery(sql);
        while (resultSet.next()) {
            rsList.add(rm.mapRow(resultSet, resultSet.getRow()));
        }
        return rsList;
    }

    /**
     * 执行数据库更新sql语句
     *
     * @return 更新行数
     */
    public int executeUpdate(String sql) throws SQLException, ClassNotFoundException {
        return getStatement().executeUpdate(sql);

    }

    /**
     * 执行多个sql更新语句
     */
    public void executeUpdate(String... sqlList) throws SQLException, ClassNotFoundException {
        for (String sql : sqlList) {
            getStatement().executeUpdate(sql);
        }
    }

    /**
     * 执行数据库更新 sql List
     *
     * @param sqlList sql列表
     */
    public void executeUpdate(List<String> sqlList) throws SQLException, ClassNotFoundException {
        for (String sql : sqlList) {
            getStatement().executeUpdate(sql);
        }
    }

    private Connection getConnection() throws ClassNotFoundException, SQLException {
        if (null == connection) connection = getConnection(dbFilePath);
        return connection;
    }

    private Statement getStatement() throws SQLException, ClassNotFoundException {
        if (null == statement) statement = getConnection().createStatement();
        return statement;
    }

    /**
     * 数据库资源关闭和释放
     */
    public void destroyed() {
        try {
            if (null != connection) {
                connection.close();
                connection = null;
            }

            if (null != statement) {
                statement.close();
                statement = null;
            }

            if (null != resultSet) {
                resultSet.close();
                resultSet = null;
            }
        } catch (SQLException e) {
            LogUtil.error("Sqlite数据库关闭时异常", e);
        }
    }

    public interface ResultSetExtractor<T> {
        T extractData(ResultSet rs);
    }

    public interface RowMapper<T> {
        T mapRow(ResultSet rs, int index) throws SQLException;
    }
}