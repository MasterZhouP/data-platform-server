package com.ruoyi.integration.datasource.validation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import com.ruoyi.integration.configuration.ConfigurationException;

/**
 * Confirms effective SQL Server permissions. Connection#setReadOnly is intentionally not used:
 * Microsoft JDBC does not make that flag a server-side permission boundary.
 */
public final class ReadonlyPermissionVerifier {
    private static final Set<String> FORBIDDEN = Set.of("CONTROL", "ALTER", "INSERT", "UPDATE", "DELETE", "EXECUTE", "IMPERSONATE", "TAKE OWNERSHIP");

    public void verify(Connection connection, Set<String> allowedObjects) {
        if (connection == null || allowedObjects == null || allowedObjects.isEmpty()) {
            throw unverified();
        }
        try {
            verifyDatabasePermissions(connection);
            for (String object : allowedObjects) verifyObjectPermissions(connection, object);
        } catch (SQLException unavailable) {
            throw unverified();
        }
    }

    private void verifyDatabasePermissions(Connection connection) throws SQLException {
        boolean any = false;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT permission_name FROM sys.fn_my_permissions(NULL, 'DATABASE')");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                any = true;
                rejectForbidden(rows.getString(1));
            }
        }
        if (!any) throw unverified();
    }

    private void verifyObjectPermissions(Connection connection, String object) throws SQLException {
        if (object == null || !object.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+")) throw unverified();
        boolean canSelect = false;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT permission_name FROM sys.fn_my_permissions(?, 'OBJECT')")) {
            statement.setString(1, object);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String permission = rows.getString(1);
                    rejectForbidden(permission);
                    canSelect |= "SELECT".equalsIgnoreCase(permission);
                }
            }
        }
        if (!canSelect) throw unverified();
    }

    private static void rejectForbidden(String permission) {
        if (permission != null && FORBIDDEN.contains(permission.toUpperCase(Locale.ROOT))) {
            throw new ConfigurationException("READONLY_PERMISSION_DENIED", 409, "数据库账户包含非只读权限");
        }
    }

    private static ConfigurationException unverified() {
        return new ConfigurationException("READONLY_UNVERIFIED", 409, "无法确认数据库账户的只读权限");
    }
}
