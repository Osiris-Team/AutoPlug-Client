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
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.jlib.logger.AL;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
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

        if (config.backup_upload_delete_on_complete.asBoolean()) {
            fileToUpload.delete();
        }
    }
}
