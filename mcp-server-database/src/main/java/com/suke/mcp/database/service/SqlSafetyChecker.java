/**
 * @author 自然醒
 */
package com.suke.mcp.database.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SqlSafetyChecker {
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|REPLACE|RENAME|GRANT|REVOKE|LOAD|CALL|EXEC)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "SELECT", "EXPLAIN", "SHOW", "DESCRIBE", "DESC"
    );

    public boolean isSafe(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String trimmed = sql.trim();
        boolean startsAllowed = ALLOWED_PREFIXES.stream()
                .anyMatch(prefix -> trimmed.toUpperCase().startsWith(prefix));
        if (!startsAllowed) {
            return false;
        }
        return !DANGEROUS_PATTERN.matcher(trimmed).find();
    }
}
