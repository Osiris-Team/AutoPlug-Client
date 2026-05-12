package com.osiris.autoplug.client.tasks.backup;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.jlib.logger.AL;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

public class BackupGoogleDrive {

    final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

    public BackupGoogleDrive() throws GeneralSecurityException, IOException {
    }

    public Credential getCredentials(BackupConfig config) throws Exception {
        AL.info("Initializing Google Drive backup credentials...");

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(),
                new InputStreamReader(new java.io.ByteArrayInputStream(
                        ("{\"installed\":{\"client_id\":\"" + config.backup_upload_alternatives_google_drive_client_id.asString() + "\"," +
                                "\"project_id\":\""+config.backup_upload_alternatives_google_drive_project_id.asString()+"\",\"auth_uri\":\"https://accounts.google.com/o/oauth2/auth\"," +
                                "\"token_uri\":\"https://oauth2.googleapis.com/token\"," +
                                "\"auth_provider_x509_cert_url\":\"https://www.googleapis.com/oauth2/v1/certs\"," +
                                "\"client_secret\":\"" + config.backup_upload_alternatives_google_drive_client_secret.asString() + "\"," +
                                "\"redirect_uris\":[\"http://localhost:8888/Callback\"]}").getBytes())));

        // Try to use refresh token if available
        if (config.backup_upload_alternatives_google_drive_refresh_token.asString() != null &&
                !config.backup_upload_alternatives_google_drive_refresh_token.asString().isEmpty()) {
            AL.info("Using existing refresh token for authentication...");
            TokenResponse response = new TokenResponse();
            response.setRefreshToken(config.backup_upload_alternatives_google_drive_refresh_token.asString());
            return new GoogleCredential.Builder()
                    .setTransport(HTTP_TRANSPORT)
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(clientSecrets)
                    .build()
                    .setFromTokenResponse(response);
        }

        AL.info("No existing credentials found, starting new authentication flow...");

        // Automatic authorization flow with local server
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
                Collections.singleton(DriveScopes.DRIVE_FILE))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        AL.info("Opening browser for Google authentication...");
        AL.info("If browser doesn't open automatically, check your console for the URL");

        try {
            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            if (credential.getRefreshToken() != null) {
                AL.info("Authentication successful! Saving refresh token for future use...");
                config.backup_upload_alternatives_google_drive_refresh_token.setValues(credential.getRefreshToken());
                config.save();
            } else {
                AL.warn("Authentication succeeded but no refresh token was received. You may need to re-authenticate later.");
            }

            return credential;
        } catch (Exception e) {
            AL.warn("Google Drive authentication failed!", e);
            throw e;
        }
    }


    // Modify the uploadToGoogleDrive method
    public void uploadToGoogleDrive(java.io.File fileToUpload, BackupConfig config)
            throws Exception {
        if (!config.backup_upload_alternatives_google_drive_enable.asBoolean()) {
            throw new IOException("Google Drive upload is not enabled");
        }

        // Get credentials
        Credential credential = getCredentials(config);

        // Create Drive service
        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("AutoPlug-Client")
                .build();

        // Rest of the upload logic remains the same...
        File fileMetadata = new File();
        fileMetadata.setName(fileToUpload.getName());
        String folderId = config.backup_upload_path.asString();
        if (folderId != null && !folderId.isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(folderId));
        }

        FileContent mediaContent = new FileContent("application/zip", fileToUpload);
        File uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        AL.debug(this.getClass(), "Uploaded to Google Drive with ID: " + uploadedFile.getId());
        int deletedOldBackups = deleteOldBackupsFromGoogleDrive(drive, config);
        if (deletedOldBackups > 0) {
            AL.debug(this.getClass(), "Deleted " + deletedOldBackups + " old Google Drive backup(s).");
        }

        if (config.backup_upload_delete_on_complete.asBoolean()) {
            fileToUpload.delete();
        }
    }

    int deleteOldBackupsFromGoogleDrive(Drive drive, BackupConfig config) throws IOException {
        int maxDays = config.backup_max_days.asInt();
        if (maxDays <= 0) {
            AL.debug(this.getClass(), "Skipping Google Drive backup retention cleanup because max-days is " + maxDays + ".");
            return 0;
        }

        Instant cutoff = Instant.now().minus(maxDays, ChronoUnit.DAYS);
        return deleteOldBackupsFromGoogleDrive(drive, config.backup_upload_path.asString(), cutoff);
    }

    int deleteOldBackupsFromGoogleDrive(Drive drive, String folderId, Instant cutoff) throws IOException {
        int deleted = 0;
        String pageToken = null;
        String query = buildBackupRetentionQuery(folderId, cutoff);

        do {
            FileList result = drive.files().list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();

            if (result.getFiles() != null) {
                for (File oldBackup : result.getFiles()) {
                    if (oldBackup.getId() == null || oldBackup.getId().trim().isEmpty()) {
                        continue;
                    }

                    drive.files().delete(oldBackup.getId()).execute();
                    deleted++;
                    AL.debug(this.getClass(), "Deleted old Google Drive backup: " + oldBackup.getName());
                }
            }

            pageToken = result.getNextPageToken();
        } while (pageToken != null && !pageToken.trim().isEmpty());

        return deleted;
    }

    static String buildBackupRetentionQuery(String folderId, Instant cutoff) {
        StringBuilder query = new StringBuilder()
                .append("trashed = false")
                .append(" and name contains '-BACKUP.zip'")
                .append(" and mimeType != 'application/vnd.google-apps.folder'")
                .append(" and createdTime < '")
                .append(DateTimeFormatter.ISO_INSTANT.format(cutoff))
                .append("'");

        if (folderId != null && !folderId.trim().isEmpty()) {
            query.append(" and '")
                    .append(escapeDriveQueryLiteral(folderId.trim()))
                    .append("' in parents");
        }

        return query.toString();
    }

    static String escapeDriveQueryLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
