/*
 * Copyright (c) 2026 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.backup;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupGoogleDriveTest {

    @Test
    void buildBackupRetentionQueryFiltersBackupZipsBeforeCutoff() {
        String query = BackupGoogleDrive.buildBackupRetentionQuery(null, Instant.parse("2026-05-01T12:30:00Z"));

        assertTrue(query.contains("trashed = false"));
        assertTrue(query.contains("name contains '-BACKUP.zip'"));
        assertTrue(query.contains("mimeType != 'application/vnd.google-apps.folder'"));
        assertTrue(query.contains("createdTime < '2026-05-01T12:30:00Z'"));
        assertFalse(query.contains("in parents"));
    }

    @Test
    void buildBackupRetentionQueryScopesToConfiguredFolder() {
        String query = BackupGoogleDrive.buildBackupRetentionQuery("folder123", Instant.parse("2026-05-01T00:00:00Z"));

        assertEquals("trashed = false and name contains '-BACKUP.zip' and mimeType != 'application/vnd.google-apps.folder' and createdTime < '2026-05-01T00:00:00Z' and 'folder123' in parents", query);
    }

    @Test
    void escapeDriveQueryLiteralEscapesSpecialCharacters() {
        assertEquals("folder\\'123", BackupGoogleDrive.escapeDriveQueryLiteral("folder'123"));
        assertEquals("folder\\\\123", BackupGoogleDrive.escapeDriveQueryLiteral("folder\\123"));
    }

    @Test
    void buildBackupRetentionQueryEscapesFolderId() {
        String query = BackupGoogleDrive.buildBackupRetentionQuery("folder'123", Instant.parse("2026-05-01T00:00:00Z"));

        assertEquals("trashed = false and name contains '-BACKUP.zip' and mimeType != 'application/vnd.google-apps.folder' and createdTime < '2026-05-01T00:00:00Z' and 'folder\\'123' in parents", query);
    }
}
