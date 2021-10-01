/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package issues;

import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class OkHttpClient {

    @Test
    void test() throws IOException {
        Request request = new Request.Builder().url("https://bit.ly/acprogress") // Redirects too
                .build();

        Response response = new okhttp3.OkHttpClient.Builder().followRedirects(true).build().newCall(request).execute();
        Assertions.assertNotNull(response.body().contentType());

    }
}
