/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathSafetyCheckerTest {
    @TempDir
    Path tempDir;

    @Test
    void withinRootIsAllowed() {
        PathSafetyChecker checker = new PathSafetyChecker(tempDir.toString());
        assertTrue(checker.isSafe(tempDir.resolve("data.csv").toString()));
    }

    @Test
    void parentTraversalIsBlocked() {
        PathSafetyChecker checker = new PathSafetyChecker(tempDir.toString());
        assertFalse(checker.isSafe(tempDir.resolve("../../etc/passwd").toString()));
    }

    @Test
    void absolutePathOutsideRootIsBlocked() {
        PathSafetyChecker checker = new PathSafetyChecker(tempDir.toString());
        assertFalse(checker.isSafe("/etc/passwd"));
    }

    @Test
    void nullPathIsBlocked() {
        PathSafetyChecker checker = new PathSafetyChecker(tempDir.toString());
        assertFalse(checker.isSafe(null));
    }

    @Test
    void resolveWithinRoot() {
        PathSafetyChecker checker = new PathSafetyChecker(tempDir.toString());
        Path resolved = checker.resolve("data/test.csv");
        assertTrue(resolved.startsWith(tempDir));
    }

    @Test
    void resolveOutsideRootThrows() {
        PathSafetyChecker checker = new PathSafetyChecker(tempDir.toString());
        assertThrows(SecurityException.class, () -> checker.resolve("../../etc/passwd"));
    }
}
