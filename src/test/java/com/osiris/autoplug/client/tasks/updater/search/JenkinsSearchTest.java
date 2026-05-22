package com.osiris.autoplug.client.tasks.updater.search;

import com.osiris.autoplug.client.UtilsTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JenkinsSearchTest {

    @Test
    void searchHtmlFallback() throws IOException {
        UtilsTest.init();

        String project_url = "https://ci.citizensnpcs.co/job/Citizens2";
        String providedArtifactName = "Citizens";
        int build_id = 0;
        Exception apiException = null;

        SearchResult sr = new JenkinsSearch().searchHtmlFallback(project_url, providedArtifactName, build_id, apiException);

        assertNotNull(sr.downloadUrl);
        assertNotNull(sr.latestVersion);
    }
}