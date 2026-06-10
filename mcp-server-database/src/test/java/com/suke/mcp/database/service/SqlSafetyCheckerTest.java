/**
 * @author 自然醒
 */
package com.suke.mcp.database.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlSafetyCheckerTest {
    private final SqlSafetyChecker checker = new SqlSafetyChecker();

    @Test
    void selectIsAllowed() { assertTrue(checker.isSafe("SELECT * FROM users")); }

    @Test
    void selectWithWhereIsAllowed() { assertTrue(checker.isSafe("SELECT id, name FROM orders WHERE status = 'active'")); }

    @Test
    void insertIsBlocked() { assertFalse(checker.isSafe("INSERT INTO users VALUES (1, 'hack')")); }

    @Test
    void updateIsBlocked() { assertFalse(checker.isSafe("UPDATE users SET role = 'admin'")); }

    @Test
    void deleteIsBlocked() { assertFalse(checker.isSafe("DELETE FROM users")); }

    @Test
    void dropIsBlocked() { assertFalse(checker.isSafe("DROP TABLE users")); }

    @Test
    void alterIsBlocked() { assertFalse(checker.isSafe("ALTER TABLE users ADD COLUMN x INT")); }

    @Test
    void createIsBlocked() { assertFalse(checker.isSafe("CREATE TABLE hack (id INT)")); }

    @Test
    void truncateIsBlocked() { assertFalse(checker.isSafe("TRUNCATE TABLE users")); }

    @Test
    void mixedCaseIsBlocked() { assertFalse(checker.isSafe("drop table users")); }

    @Test
    void sqlWithCommentsBlocked() { assertFalse(checker.isSafe("SELECT 1; DROP TABLE users --")); }

    @Test
    void explainIsAllowed() { assertTrue(checker.isSafe("EXPLAIN SELECT * FROM users")); }

    @Test
    void showIsAllowed() { assertTrue(checker.isSafe("SHOW TABLES")); }

    @Test
    void describeIsAllowed() { assertTrue(checker.isSafe("DESCRIBE users")); }

    @Test
    void nullIsBlocked() { assertFalse(checker.isSafe(null)); }

    @Test
    void blankIsBlocked() { assertFalse(checker.isSafe("   ")); }
}
